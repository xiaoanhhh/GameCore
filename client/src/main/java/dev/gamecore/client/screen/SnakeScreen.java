package dev.gamecore.client.screen;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.api.event.GameEvent;
import dev.gamecore.api.session.PlayerInput;
import dev.gamecore.api.session.SessionLoop;
import dev.gamecore.snake.SnakeSessionFactory;
import dev.gamecore.client.timing.FixedStepClock;
import dev.gamecore.client.snake.SnakeBoard;
import dev.gamecore.client.snake.SnakeControls;
import dev.gamecore.client.snake.SnakeArenaPane;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.snake.Direction;
import dev.gamecore.snake.SnakeGame;
import dev.gamecore.snake.SnakeGameSession;
import dev.gamecore.snake.SnakeMode;
import dev.gamecore.snake.SnakeSnapshot;

/**
 * Snake 可玩页面：拥有一局规则和计时器，共享主题/成绩由客户端持有。
 * 开始前静止；暂停、失焦和离页清空计时余量，恢复后不会补跑后台时间。
 * 重开使用同一种子便于练习，重新从大厅进入时创建新种子；使用公共会话/执行器，网格规则仍由 Snake 实现。
 */
public final class SnakeScreen extends UiScreen {
    private final SnakeMode mode;
    private final long seed = System.nanoTime();
    private final FixedStepClock clock = new FixedStepClock(SnakeGameSession.TICKS_PER_SECOND);
    private final SnakeControls controls;
    private final SnakeBoard board;
    private final Label stateLabel;
    private final Label scoreLabel;
    private final Label bestLabel;
    private final Label speedLabel;
    private final Label notice;
    private final TextButton action;
    private SnakeGameSession game;
    private SnakeSnapshot view;
    private SessionLoop<Direction, SnakeSnapshot> loop;
    private long inputSequence;
    private Dialog leaderboard;

    public SnakeScreen(GameCoreClient app, String modeId) {
        super(app, "SNAKE ARENA", "Tab 切换焦点   ·   Enter 确认   ·   Esc 暂停", app.isGameWindow(),false);
        mode = SnakeMode.fromId(modeId);
        controls = new SnakeControls(app.settings().keys());
        createSession();
        view = game.snapshot();
        board = new SnakeBoard(theme, view);
        board.setName("snake-board");
        scoreLabel = theme.label("", app.isGameWindow() ? theme.assets.title : theme.assets.heading, ThemeManager.MINT);
        scoreLabel.setWrap(app.isGameWindow());
        scoreLabel.setName("snake-score");
        bestLabel = theme.caption(""); bestLabel.setName("snake-best");
        speedLabel = theme.caption(""); speedLabel.setName("snake-speed");
        stateLabel = theme.label("", theme.assets.body, ThemeManager.MINT);
        stateLabel.setName("snake-state"); stateLabel.setWrap(true);
        notice = theme.caption(""); notice.setName("snake-notice"); notice.setWrap(true);
        action = bind(theme.button("开始游戏", true), "snake-action", this::activate);
        TextButton restart = bind(theme.button("重新开始", false), "snake-restart", this::restart);
        TextButton ranking = bind(theme.button("排行榜", false), "snake-leaderboard", this::showLeaderboard);
        TextButton home = bind(theme.button("返回大厅", false), "return-home", app::home);
        Label keys = theme.label("P1  " + controls.label(0) + (mode == SnakeMode.DUO ? "\nP2  " + controls.label(1) : ""),
                theme.assets.small, ThemeManager.TEXT);
        String modeName = app.registry().require("snake").requireMode(modeId).name();
        if (app.isGameWindow()) {
            Table actions=new Table();
            actions.add(theme.caption("Space / P / Esc  暂停或继续")).left().row();
            actions.add(action).growX().height(40).padTop(14).row();
            Table secondary=new Table();secondary.add(restart).growX().height(38).padRight(6);secondary.add(ranking).growX().height(38);
            actions.add(secondary).growX().padTop(8).row();actions.add(home).growX().height(38).padTop(8);
            Table panel=dev.gamecore.client.ui.ArenaPanels.snake(theme,"LIVE / 本地竞技",modeName,scoreLabel,bestLabel,speedLabel,keys,actions,stateLabel,notice);
            panel.getChildren().first().setName("snake-hud-kicker");
            ScrollPane scroll=dev.gamecore.client.ui.ArenaPanels.scroll(theme,panel);scroll.setName("snake-controls");
            content.add(new SnakeArenaPane(board, scroll)).grow();
        } else {
            // 嵌入入口保留用于无多窗口适配器的图形测试；正式桌面启动统一使用独立窗口。
            Table heading = new Table();
            Table title = new Table();
            title.add(theme.label("Snake Arena", theme.assets.title, ThemeManager.TEXT)).left().row();
            title.add(theme.label(modeName + "  /  20 × 20", theme.assets.body, ThemeManager.MINT)).left().padTop(4);
            heading.add(title).expandX().left();
            heading.add(ranking).height(40).padRight(10); heading.add(home).height(40);
            content.add(heading).growX().height(66).padBottom(14).row();
            Table arena = new Table();
            arena.add(board).size(440).padRight(24);
            Table panel = new Table(); panel.setBackground(theme.panel(ThemeManager.PANEL)); panel.pad(20).top();
            panel.add(theme.caption("本局成绩  /  SCORE")).left().row();
            panel.add(scoreLabel).growX().left().padTop(8).row();
            panel.add(bestLabel).left().padTop(10).row();
            panel.add(speedLabel).left().padTop(8).row();
            panel.add(keys).left().padTop(8).row();
            panel.add(theme.caption("Space / P / Esc  暂停或继续")).left().padTop(8).row();
            panel.add(action).growX().height(42).padTop(12).row();
            panel.add(restart).growX().height(38).padTop(8);
            arena.add(panel).growX().height(440);
            content.add(arena).growX().row();
            Table banner = new Table();
            banner.setBackground(theme.panel(ThemeManager.PANEL));
            banner.pad(8, 12, 8, 12);
            banner.add(stateLabel).growX().height(24).row();
            banner.add(notice).growX().height(18).padTop(4);
            content.add(banner).growX().height(62).padTop(8).row();
            content.add().expandY();
        }
        stage.setKeyboardFocus(action);
        refresh();
    }

    public SnakeSnapshot snapshot() { return view; }
    public SnakeControls controls() { return controls; }
    public SnakeGameSession.TimingSnapshot timingSnapshot() { return game.timingSnapshot(); }
    public long slowFrames() { return clock.slowFrames(); }

    private void activate() {
        switch (view.state()) {
            case READY -> game.start();
            case RUNNING -> game.pause();
            case PAUSED -> game.resume();
            case FINISHED -> { restart(); game.start(); }
        }
        clock.reset();
        app.snakeRecords().flush();
        refresh();
    }

    private void restart() {
        app.snakeRecords().flush();
        createSession();
        clock.restart();
        refresh();
    }

    /** 事件订阅属于当前页面的一局；重开先释放旧订阅，结束战绩只随 MatchEnded 提交一次。 */
    private void createSession() {
        if (loop != null) { loop.events().clear(); }
        game = new SnakeSessionFactory().create(new SnakeSessionFactory.Config(mode), seed);
        loop = new SessionLoop<>(game); inputSequence = 0;
        loop.events().subscribe(event -> {
            if (event instanceof GameEvent.ScoreChanged score) {
                app.snakeRecords().record(mode, score.current());
                app.assets().previewVolume(app.settings().volume());
            }
            if (event instanceof GameEvent.MatchEnded) { app.snakeRecords().recordFinishedRound(game.snapshot()); }
        });
    }

    private void refresh() {
        view = game.snapshot();
        board.update(view);
        scoreLabel.setText(dev.gamecore.client.ui.ArenaHudText.snakeScores(view,app.isGameWindow()));
        bestLabel.setText("模式最佳个人分  " + app.snakeRecords().best(mode));
        speedLabel.setText(dev.gamecore.client.ui.ArenaHudText.snakeSpeed(view));
        action.setText(switch (view.state()) {
            case READY -> "开始游戏"; case RUNNING -> "暂停游戏"; case PAUSED -> "继续游戏"; case FINISHED -> "再来一局";
        });
        stateLabel.setText(switch (view.state()) {
            case READY -> mode == SnakeMode.DUO ? "准备就绪：共同争食，最后存活者获胜；同时碰撞为平局。" : "准备就绪：吃食物成长，避开墙壁和自己的身体。";
            case RUNNING -> "●  游戏中  ·  转弯可提前输入，不能直接掉头";
            case PAUSED -> "Ⅱ  已暂停  ·  点击继续游戏或按 Space / P / Esc";
            case FINISHED -> resultText();
        });
        stateLabel.getStyle().fontColor = view.state() == SnakeGame.State.FINISHED ? ThemeManager.GOLD : ThemeManager.MINT;
        notice.setText(app.snakeRecords().notice().isBlank() ? "离开页面结束本局；新纪录即时保存。" : app.snakeRecords().notice());
    }

    private String resultText() {
        String result = switch (view.result()) {
            case SOLO_WIN -> "恭喜，已填满棋盘！";
            case SOLO_LOSS -> "本局结束：" + collision(view.players().getFirst().collision());
            case PLAYER_ONE -> "P1 获胜";
            case PLAYER_TWO -> "P2 获胜";
            case DRAW -> "本局平局";
            case NONE -> "";
        };
        if (mode == SnakeMode.DUO) {
            result += "  ·  P1 " + collision(view.players().get(0).collision()) + " / P2 " + collision(view.players().get(1).collision());
        }
        return result;
    }
    private static String collision(SnakeGame.Collision collision) {
        return switch (collision) {
            case NONE -> "存活"; case WALL -> "撞到边界"; case OBSTACLE -> "撞到障碍";
            case SELF -> "撞到自己"; case OTHER -> "撞到对手身体"; case HEAD_ON -> "同时撞头"; case HEAD_SWAP -> "交叉撞头";
        };
    }

    /** 查看榜单先暂停，关闭后仍需主动继续；所有榜单数据均来自真实完成的本地对局。 */
    private void showLeaderboard() {
        if (leaderboard != null) { return; }
        pause();
        app.snakeRecords().flush();
        TextButton close=bind(theme.button("关闭排行榜",true),"snake-ranking-close",this::closeLeaderboard);
        leaderboard=dev.gamecore.client.snake.SnakeLeaderboard.create(theme,app.snakeRecords(),mode,app.registry().require("snake").requireMode(mode.id()).name(),"本地排行榜",close);
        leaderboard.show(stage);
        stage.setKeyboardFocus(close);
    }

    private void closeLeaderboard() {
        if (leaderboard == null) { return; }
        forgetFocus(leaderboard);
        leaderboard.remove();
        leaderboard = null;
        stage.setKeyboardFocus(action);
        refresh();
    }

    @Override protected boolean handleKey(int keycode) {
        if (leaderboard != null) {
            if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) { closeLeaderboard(); }
            return true;
        }
        if (keycode == Input.Keys.SPACE || keycode == Input.Keys.P || keycode == Input.Keys.ESCAPE) {
            if (view.state() != SnakeGame.State.FINISHED || keycode == Input.Keys.SPACE) { activate(); }
            return true;
        }
        for (int player = 0; player < view.players().size(); player++) {
            Direction direction = controls.direction(player, keycode);
            if (direction != null) { game.submitInput(new PlayerInput<>(game.tickNumber() + 1, player, inputSequence++, direction)); return true; }
        }
        return false;
    }

    @Override public void render(float delta) {
        if (view.state() == SnakeGame.State.RUNNING) {
            long previousSlowFrames = clock.slowFrames();
            int count = clock.advance(delta);
            if (clock.slowFrames() != previousSlowFrames && (clock.slowFrames() == 1 || clock.slowFrames() % 60 == 0)) {
                Gdx.app.log("Snake", "Slow frames=" + clock.slowFrames() + ", droppedSeconds=" + clock.droppedSeconds());
            }
            for (int i = 0; i < count; i++) {
                for (var failure : loop.tick()) { Gdx.app.error("Snake", "Event consumer failed", failure); }
                inputSequence = 0;
                if (game.isFinished()) { clock.reset(); break; }
            }
            if (count > 0) { refresh(); }
        } else { clock.reset(); }
        super.render(delta);
    }
    @Override public void pause() { game.pause(); clock.reset(); refresh(); }
    @Override public void dispose() { if (loop != null) { loop.events().clear(); } super.dispose(); }
    @Override public void hide() { pause(); app.snakeRecords().flush(); super.hide(); }
}
