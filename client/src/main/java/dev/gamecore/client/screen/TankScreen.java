package dev.gamecore.client.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.api.event.GameEvent;
import dev.gamecore.api.session.PlayerInput;
import dev.gamecore.api.session.SessionLoop;
import dev.gamecore.client.effects.EffectWorld;
import dev.gamecore.tank.TankCommand;
import dev.gamecore.tank.TankSessionFactory;
import dev.gamecore.client.timing.FixedStepClock;
import dev.gamecore.client.tank.TankArenaPane;
import dev.gamecore.client.tank.TankBoard;
import dev.gamecore.client.tank.TankControls;
import dev.gamecore.client.tank.TankMapAsset;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.tank.TankBot;
import dev.gamecore.tank.TankGame;
import dev.gamecore.tank.TankMode;
import dev.gamecore.tank.TankSession;
import dev.gamecore.tank.TankSnapshot;
import java.io.IOException;

/**
 * Tank 页面只负责输入、固定步长、UI 和地图资源；规则及 Bot 在纯 Java 会话中运行。
 * 切图/切难度/重开建立新局；失焦暂停并清空按键和时间余量；离页释放独占 TMX 纹理。
 */
public final class TankScreen extends UiScreen {
    private final TankMode mode;
    private final long seed = System.nanoTime();
    private final FixedStepClock clock = new FixedStepClock(TankGame.TICKS_PER_SECOND);
    private final TankControls controls;
    private final TankBoard board;
    private final Label health;
    private final Label status;
    private final Label time;
    private final Label botState;
    private final Label notice;
    private final TextButton action;
    private final SelectBox<TankMapAsset.Arena> maps;
    private final SelectBox<String> difficulty;
    private TankMapAsset mapAsset;
    private TankSession session;
    private SessionLoop<TankCommand, TankSnapshot> loop;
    private final EffectWorld effects = new EffectWorld();
    private TankSnapshot view;

    public TankScreen(GameCoreClient app, String modeId) {
        super(app, "TANK ARENA", "P / Esc 暂停   ·   Space / Enter 开火", true);
        mode = TankMode.fromId(modeId); controls = new TankControls(app.settings().keys());
        board = new TankBoard(theme); board.setName("tank-board");
        health = theme.label("", theme.assets.heading, ThemeManager.MINT); health.setName("tank-health");
        status = theme.text(""); status.setWrap(true); status.setName("tank-state");
        time = theme.caption(""); time.setName("tank-time");
        botState = theme.caption(""); botState.setName("tank-bot-state");
        notice = theme.caption(""); notice.setWrap(true); notice.setName("tank-notice");
        action = bind(theme.button("开始游戏", true), "tank-action", this::activate);
        maps = focus(theme.selectBox(), "tank-map"); maps.setItems(TankMapAsset.Arena.values());
        difficulty = focus(theme.selectBox(), "tank-difficulty"); difficulty.setItems("简单", "普通", "困难");
        difficulty.setSelectedIndex(1); difficulty.setDisabled(mode == TankMode.DUEL);
        Table options=new Table();
        Table mapRow=new Table();mapRow.add(theme.caption("地图")).width(44).left();mapRow.add(maps).growX().height(34);
        options.add(mapRow).growX().row();
        if(mode==TankMode.BOT){Table botRow=new Table();botRow.add(theme.caption("难度")).width(44).left();botRow.add(difficulty).growX().height(34);options.add(botRow).growX().padTop(8).row();}
        options.add(botState).growX().padTop(6);
        Label keys=theme.caption("P1  "+controls.label(0)+(mode==TankMode.DUEL?"\nP2  "+controls.label(1):"\nP2  电脑控制"));
        // 主操作独占一行，重开/返回并排；“P / Esc”提示已由地图浮层给出，侧栏不再重复，保证 720 高度内无需滚动。
        Table actions=new Table();
        actions.add(action).growX().height(40).colspan(2).row();
        actions.add(bind(theme.button("重新开始",false),"tank-restart",this::restart)).growX().uniformX().height(38).padTop(6).padRight(3);
        actions.add(bind(theme.button("返回大厅",false),"return-home",app::home)).growX().uniformX().height(38).padTop(6).padLeft(3);
        var scoreboard=new dev.gamecore.client.tank.TankScoreboard(theme,board,"P1",mode==TankMode.DUEL?"P2":"电脑");
        Table panel=dev.gamecore.client.ui.ArenaPanels.tank(theme,"本地竞技",mode==TankMode.DUEL?"本地双人":"人机竞技",scoreboard,health,time,status,options,keys,actions,notice);
        ScrollPane scroll=dev.gamecore.client.ui.ArenaPanels.scroll(theme,panel);
        content.add(new TankArenaPane(board, scroll)).grow();
        maps.addListener(new ChangeListener() { @Override public void changed(ChangeEvent event, Actor actor) { restart(); } });
        difficulty.addListener(new ChangeListener() { @Override public void changed(ChangeEvent event, Actor actor) { restart(); } });
        for (Actor selector : new Actor[]{maps, difficulty}) {
            selector.addListener(new InputListener() {
                @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) { pause(); return false; }
            });
            selector.addListener(new FocusListener() {
                @Override public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) { if (focused) { pause(); } }
            });
        }
        stage.setKeyboardFocus(action); restart();
    }
    public TankSnapshot snapshot() { return view; }
    public TankControls controls() { return controls; }
    public TankMapAsset mapAsset() { return mapAsset; }
    public java.util.List<EffectWorld.Particle> effectSnapshot() { return effects.snapshot(); }
    public long slowFrames() { return clock.slowFrames(); }
    private void restart() {
        controls.clear(); clock.restart(); effects.clear();
        TankMapAsset candidate = null;
        try {
            candidate = new TankMapAsset(maps.getSelected());
            TankSession next = new TankSessionFactory().create(
                    new TankSessionFactory.Config(candidate.logic(), mode, TankBot.Level.values()[difficulty.getSelectedIndex()]), seed);
            if (mapAsset != null) { mapAsset.dispose(); }
            if (loop != null) { loop.events().clear(); }
            mapAsset = candidate; session = next;
            loop = new SessionLoop<>(session);
            loop.events().subscribe(effects::accept);
            loop.events().subscribe(event -> {
                if (event instanceof GameEvent.Effect effect && effect.kind() == GameEvent.Effect.Kind.HIT) {
                    app.assets().previewVolume(app.settings().volume());
                }
            });
            action.setDisabled(false); notice.setText("");
            ((Table) notice.getParent()).getCell(notice).height(0);
            refresh(); content.invalidateHierarchy();
        } catch (IOException | RuntimeException failure) {
            if (candidate != null) { candidate.dispose(); }
            if (session != null) { session.pause(); refresh(); }
            action.setDisabled(session == null);
            notice.setText("地图加载失败，请重新选择地图。");
            ((Table) notice.getParent()).getCell(notice).height(44); ((Table) notice.getParent()).invalidateHierarchy();
            Gdx.app.error("Tank", "Could not load arena", failure);
        }
    }
    private void activate() {
        if (session == null) { return; }
        controls.clear(); clock.reset();
        switch (view.state()) {
            case READY -> session.start();
            case PAUSED -> session.resume();
            case RUNNING -> session.pause();
            case FINISHED -> { restart(); if (session != null) { session.start(); } }
        }
        refresh();
    }
    private void refresh() {
        view = session.snapshot(); board.update(mapAsset, view);
        health.setText(dev.gamecore.client.ui.ArenaHudText.tankHealth(view));
        time.setText(dev.gamecore.client.ui.ArenaHudText.tankTime(view));
        botState.setText(mode == TankMode.DUEL ? "双人共用键盘" : "电脑行动  " + switch (session.botState()) {
            case PATROL -> "巡游"; case CHASE -> "追踪"; case ATTACK -> "攻击"; case EVADE -> "躲避";
        });
        action.setText(switch (view.state()) { case READY -> "开始游戏"; case RUNNING -> "暂停游戏"; case PAUSED -> "继续游戏"; case FINISHED -> "再来一局"; });
        status.setText(switch (view.state()) {
            case READY -> "准备就绪 · 利用掩体";
            case RUNNING -> "● 游戏中 · 炮口朝向车头";
            case PAUSED -> "Ⅱ 已暂停 · 按 P / Esc 继续";
            case FINISHED -> switch (view.result()) {
                case PLAYER_ONE -> "P1 获胜"; case PLAYER_TWO -> mode == TankMode.BOT ? "电脑获胜" : "P2 获胜";
                case DRAW -> "本局平局"; case NONE -> "";
            } + (view.tick() >= TankGame.MATCH_TICKS ? " · 时间到" : " · 对局结束");
        });
        status.getStyle().fontColor = view.state() == TankGame.State.FINISHED ? ThemeManager.GOLD : ThemeManager.TEXT;
        // 地图中部浮层与侧栏状态同源；进行中不遮挡战场。
        var banner = dev.gamecore.client.ui.ArenaHudText.tankBanner(view, mode == TankMode.BOT);
        board.overlay(banner == null ? null : banner.title(), banner == null ? null : banner.detail(),
                view.state() == TankGame.State.READY ? ThemeManager.BLUE
                        : view.result() == TankGame.Result.PLAYER_ONE ? ThemeManager.MINT
                        : view.result() == TankGame.Result.PLAYER_TWO ? ThemeManager.GOLD : ThemeManager.TEXT);
    }
    @Override protected boolean handleKey(int key) {
        if (maps.getScrollPane().getStage() != null || difficulty.getScrollPane().getStage() != null) {
            if (key == Input.Keys.ESCAPE) { maps.hideScrollPane(); difficulty.hideScrollPane(); return true; }
            return false;
        }
        if (key == Input.Keys.P || key == Input.Keys.ESCAPE) { activate(); return true; }
        if (controls.accepts(key) && view != null && view.state() == TankGame.State.RUNNING) { controls.down(key); return true; }
        return false;
    }
    @Override protected boolean handleKeyUp(int key) { controls.up(key); return controls.accepts(key); }
    @Override public void render(float delta) {
        if (view != null && (view.state() == TankGame.State.RUNNING || view.state() == TankGame.State.FINISHED)) {
            effects.update(Math.min(Math.max(0, delta), .25));
        }
        if (session != null && view.state() == TankGame.State.RUNNING) {
            long previous = clock.slowFrames();
            int count = clock.advance(delta);
            if (clock.slowFrames() != previous && (clock.slowFrames() == 1 || clock.slowFrames() % 60 == 0)) {
                Gdx.app.log("Tank", "Slow frames=" + clock.slowFrames() + ", droppedSeconds=" + clock.droppedSeconds());
            }
            for (int i = 0; i < count; i++) {
                session.submitInput(new PlayerInput<>(session.tickNumber() + 1, 0, 0, controls.command(0)));
                if (mode == TankMode.DUEL) { session.submitInput(new PlayerInput<>(session.tickNumber() + 1, 1, 1, controls.command(1))); }
                for (var failure : loop.tick()) { Gdx.app.error("Tank", "Event consumer failed", failure); }
                var next = session.snapshot();
                if (next.state() == TankGame.State.FINISHED) { controls.clear(); clock.reset(); break; }
            }
            if (count > 0) { refresh(); }
        } else { clock.reset(); }
        board.updateEffects(effects.snapshot());
        super.render(delta);
    }
    @Override protected boolean tacticalBackdrop() { return true; }
    @Override public void pause() { controls.clear(); clock.reset(); if (session != null) { session.pause(); refresh(); } }
    @Override public void hide() { pause(); super.hide(); }
    @Override public void dispose() { if (isDisposed()) { return; } if (loop != null) { loop.events().clear(); } effects.clear(); super.dispose(); if (mapAsset != null) { mapAsset.dispose(); } }
}
