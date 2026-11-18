package dev.gamecore.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.screen.SnakeScreen;
import dev.gamecore.client.screen.UiScreen;
import dev.gamecore.client.settings.ClientSettings;
import dev.gamecore.client.settings.KeyBindings;
import dev.gamecore.client.settings.SettingsStore;
import dev.gamecore.client.snake.SnakeRecords;
import dev.gamecore.snake.Cell;
import dev.gamecore.snake.Direction;
import dev.gamecore.snake.SnakeGame;
import dev.gamecore.snake.SnakeMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 真窗口、真实 Stage 命中及输入管线；显式传帧时间稳定复现，不靠睡眠和机器帧率。 */
public final class SnakeSmokeTest {
    private SnakeSmokeTest() { }
    public static void main(String[] args) throws IOException {
        Path report = Path.of(args[0]); Files.createDirectories(report);
        Files.deleteIfExists(report.resolve("result.txt"));
        Path records = report.resolve("snake-records.properties"); Files.deleteIfExists(records);
        Path preferences = report.resolve("preferences.properties");
        var defaults = ClientSettings.defaults();
        new SettingsStore(preferences).save(new ClientSettings(0, defaults.resolution(), true,
                new KeyBindings(Input.Keys.UP, Input.Keys.DOWN, Input.Keys.LEFT, Input.Keys.RIGHT)));
        Probe probe = new Probe(report, false);
        var configuration = Lwjgl3Launcher.configuration(DesktopSettings.defaults()); configuration.disableAudio(true);
        new Lwjgl3Application(probe, configuration);
        require(probe.finished && probe.isDisposed(), "Smoke did not finish");
        require(probe.screens.stream().allMatch(UiScreen::isDisposed), "Visited screens retained");
        require(Gdx.input.getInputProcessor() == null, "Input retained on exit");
        require(new SnakeRecords(records).best(SnakeMode.CLASSIC) == probe.completedClassicScore, "Record did not survive client shutdown");
        require(new SnakeRecords(records).leaderboard(SnakeMode.CLASSIC).size() == 1, "Completed round missing from leaderboard");
        require(new SnakeRecords(records).leaderboard(SnakeMode.DUO).size() == 2, "Duo results not persisted");
        probe.dispose();
        Path failureDirectory = report.resolve("save-failure");
        Files.createDirectories(failureDirectory.resolve("snake-records.properties"));
        Files.writeString(failureDirectory.resolve("snake-records.properties/keep.txt"), "preserve");
        Probe failure = new Probe(failureDirectory, true);
        new Lwjgl3Application(failure, configuration);
        require(failure.finished && failure.isDisposed(), "Save failure scenario did not finish");
        require(Files.readString(failureDirectory.resolve("snake-records.properties/keep.txt")).equals("preserve"), "Existing target damaged");
        Files.writeString(report.resolve("result.txt"), "SNAKE_SMOKE_OK\nClassicGrowth=true\nScorePersisted=true\n"
                + "ReadyPauseResumeRestart=true\nLifecyclePause=true\nObstacles=true\nDuoInputDisjoint=true\n"
                + "DuoDraw=true\nWallGameOver=true\nResizeAndLetterbox=true\nGlyphCoverage=true\n"
                + "SaveFailureVisible=true\nLeaderboardPersisted=true\nLeaderboardPauses=true\nModalFontRefreshed=true\n"
                + "FixedSimulation20Hz=true\nProgressiveAcceleration=true\nSlowFramePolicy=true\n"
                + "ScreensDisposed=true\nInputDetached=true\nOpenGL=" + probe.glVersion + "\n");
        System.out.println("SNAKE_SMOKE_OK");
    }
    private static void require(boolean condition, String message) { if (!condition) { throw new IllegalStateException(message); } }

    private static final class Probe extends GameCoreClient {
        private final Path directory;
        private final boolean failureCase;
        private final List<UiScreen> screens = new ArrayList<>();
        private UiScreen previous;
        private int frame;
        private int step;
        private boolean finished;
        private String glVersion;
        private int completedClassicScore;
        private Probe(Path directory, boolean failureCase) {
            super(directory.resolve("preferences.properties")); this.directory = directory; this.failureCase = failureCase;
        }

        @Override public void render() {
            // 测试控制 Tick 时间，日常客户端仍使用 Game.render 的真实 delta。
            getScreen().render(0f);
            UiScreen current = ui();
            if (current != previous) {
                require(previous == null || previous.isDisposed(), "Previous page was not disposed");
                screens.add(current); previous = current;
            }
            if (++frame % 3 != 0) { return; }
            switch (step++) {
                case 0 -> click("game-snake");
                case 1 -> click("start-preview");
                case 2 -> {
                    require(snake().snapshot().state() == SnakeGame.State.READY, "Game should wait for start");
                    for (char c : Gdx.files.internal("fonts/characters.txt").readString("UTF-8").toCharArray()) {
                        if (!Character.isWhitespace(c)) { require(assets().body.getData().hasGlyph(c), "Missing glyph: " + c); }
                    }
                    snapshot("classic-ready.png"); click("snake-action");
                    Cell before = snake().snapshot().players().getFirst().head();
                    key(snake().controls().keys(0).up()); snake().render(.1f);
                    require(snake().snapshot().players().getFirst().head().equals(before.move(Direction.UP)), "Rebound key did not move P1");
                    require(snake().timingSnapshot().simulationTick() == 2, "Expected two 20Hz steps in 100ms");
                    key(Input.Keys.P); var paused = snake().snapshot(); snake().render(20f);
                    require(snake().snapshot().equals(paused), "Paused game advanced");
                    snapshot("paused.png");
                    click("snake-leaderboard");
                    require(actor("snake-rank-0") == null, "Aborted round should not be ranked");
                    key(Input.Keys.ESCAPE);
                    require(snake().snapshot().state() == SnakeGame.State.PAUSED, "Closing leaderboard resumed game");
                }
                case 3 -> {
                    key(Input.Keys.ESCAPE); require(snake().snapshot().state() == SnakeGame.State.RUNNING, "Esc did not resume");
                    pause(); require(snake().snapshot().state() == SnakeGame.State.PAUSED, "Lifecycle pause ignored");
                    key(Input.Keys.SPACE); snake().render(.01f);
                    require(snake().snapshot().tick() == 1, "Resume caught up hidden time");
                    click("snake-restart"); require(snake().snapshot().state() == SnakeGame.State.READY, "Restart did not reset");
                    click("snake-action"); snake().render(2f);
                    require(snake().slowFrames() == 1 && snake().timingSnapshot().simulationTick() == 5, "Slow frame cap not applied");
                    click("snake-restart"); require(snake().slowFrames() == 0, "Restart retained old timing statistics");
                    click("snake-action"); eatUntil(failureCase ? 10 : 30);
                    require(snake().snapshot().players().getFirst().body().size() == (failureCase ? 4 : 6), "Food did not grow snake");
                    require(snakeRecords().best(SnakeMode.CLASSIC) == (failureCase ? 10 : 30), "Best score did not update");
                    if (failureCase) {
                        require(snakeRecords().pendingSave(), "Save failure not retained");
                        require(((Label) actor("snake-notice")).getText().toString().contains("保存失败"), "Save failure not visible");
                        snapshot("save-failure.png");
                        for (int i = 0; i < 21; i++) { snake().render(.1f); }
                        require(snakeRecords().leaderboard(SnakeMode.CLASSIC).size() == 1 && snakeRecords().pendingSave(), "Failed ranking was not kept in memory");
                        click("snake-leaderboard");
                        snapshot("ranking-save-failure.png"); key(Input.Keys.ESCAPE);
                        finished = true; click("exit"); return;
                    }
                    require(snake().timingSnapshot().movementRate() == 12, "Third food did not accelerate");
                    require(((Label) actor("snake-speed")).getText().toString().contains("12 格/秒"), "Speed label not updated");
                    snapshot("classic-growth.png");
                    click("snake-leaderboard");
                    require(snake().snapshot().state() == SnakeGame.State.PAUSED, "Opening leaderboard did not pause live game");
                    var rankingPause = snake().timingSnapshot();
                    key(snake().controls().keys(0).up()); snake().render(1f);
                    require(snake().timingSnapshot().equals(rankingPause), "Modal input or time advanced game");
                    snapshot("leaderboard-empty.png"); key(Input.Keys.SPACE);
                    require(snake().snapshot().state() == SnakeGame.State.PAUSED, "Modal close resumed live game");
                    key(Input.Keys.P);
                    // 不再转弯，必定在 20 Tick 内撞边界。
                    for (int i = 0; i < 21; i++) { snake().render(.1f); }
                    require(snake().snapshot().result() == SnakeGame.Result.SOLO_LOSS, "Wall did not finish game");
                    // 原玩法随机食物可能恰好在冲向墙的路径上；排行榜应等于真实终局分数。
                    completedClassicScore = snake().snapshot().players().getFirst().score();
                    require(completedClassicScore >= 30, "Final score lost earned food");
                    require(((Label) actor("snake-state")).getText().toString().contains("撞到边界"), "Game over message missing");
                    snapshot("classic-game-over.png");
                    require(snakeRecords().leaderboard(SnakeMode.CLASSIC).size() == 1, "Round was submitted more than once");
                    click("snake-leaderboard");
                    require(((Label) actor("snake-rank-score-0")).getText().toString().contains(completedClassicScore + " 分"), "Ranking score does not match authoritative final score");
                    snapshot("classic-leaderboard.png"); key(Input.Keys.ESCAPE); click("snake-action");
                    require(snake().snapshot().tick() == 0 && snake().snapshot().state() == SnakeGame.State.RUNNING, "Play again failed");
                    click("return-home");
                }
                case 4 -> { click("game-snake"); }
                case 5 -> { click("mode-obstacles"); click("start-preview"); }
                case 6 -> {
                    require(snake().snapshot().mode() == SnakeMode.OBSTACLES && snake().snapshot().obstacles().size() == 16, "Obstacle mode lost");
                    snapshot("obstacles.png"); Gdx.graphics.setWindowedMode(960, 540);
                }
                case 7 -> { snapshot("snake-small.png"); click("snake-action"); Gdx.graphics.setWindowedMode(800, 900); }
                case 8 -> {
                    require(ui().stage().getViewport().getScreenY() > 0, "Portrait letterbox missing");
                    snapshot("snake-letterbox.png"); click("return-home");
                }
                case 9 -> { Gdx.graphics.setWindowedMode(1280, 720); click("game-snake"); }
                case 10 -> { click("mode-duo"); click("start-preview"); }
                case 11 -> {
                    require(snake().controls().keys(1).up() == Input.Keys.I, "P2 did not avoid rebound arrow keys");
                    click("snake-action"); key(Input.Keys.UP); key(Input.Keys.K); snake().render(.1f);
                    require(snake().snapshot().players().get(0).head().equals(new Cell(5, 11)), "P1 control mismatch: " + snake().snapshot());
                    require(snake().snapshot().players().get(1).head().equals(new Cell(14, 9)), "P2 control mismatch: " + snake().snapshot());
                    snapshot("duo-playing.png"); click("snake-restart"); click("snake-action");
                    for (int i = 0; i < 5; i++) { snake().render(.1f); }
                    require(snake().snapshot().result() == SnakeGame.Result.DRAW, "Simultaneous head swap should draw");
                    snapshot("duo-draw.png");
                    click("snake-leaderboard");
                    require(actor("snake-rank-1") != null, "Duo should submit two individual scores");
                    Gdx.graphics.setWindowedMode(1920, 1080);
                }
                case 12 -> {
                    ui().render(.2f);
                    require(((Window) actor("snake-ranking-dialog")).getStyle().titleFont == assets().heading, "Modal title retained old font");
                    snapshot("leaderboard-large.png"); key(Input.Keys.ESCAPE); snapshot("duo-large.png"); click("return-home");
                }
                case 13 -> { glVersion = Gdx.gl.glGetString(GL20.GL_VERSION); finished = true; click("exit"); }
                default -> throw new IllegalStateException("Scenario did not exit");
            }
            require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "OpenGL error");
        }

        /** BFS 只用于验收时收集三颗食物触发加速，不把自动寻路塞进正式玩法。 */
        private void eatUntil(int targetScore) {
            for (int tick = 0; tick < 400 && snake().snapshot().players().getFirst().score() < targetScore; tick++) {
                var view = snake().snapshot(); var player = view.players().getFirst();
                Set<Cell> blocked = new HashSet<>(view.obstacles()); blocked.addAll(player.body());
                Map<Cell, Direction> firstStep = new HashMap<>(); Set<Cell> seen = new HashSet<>();
                ArrayDeque<Cell> queue = new ArrayDeque<>(); Cell origin = player.head(); queue.add(origin); seen.add(origin);
                while (!queue.isEmpty() && !seen.contains(view.food())) {
                    Cell current = queue.removeFirst();
                    for (Direction direction : Direction.values()) {
                        if (current.equals(origin) && player.direction().opposite(direction)) { continue; }
                        Cell next = current.move(direction);
                        if (next.x() < 0 || next.x() >= 20 || next.y() < 0 || next.y() >= 20 || blocked.contains(next) || !seen.add(next)) { continue; }
                        firstStep.put(next, current.equals(origin) ? direction : firstStep.get(current)); queue.add(next);
                    }
                }
                Direction turn = firstStep.get(view.food()); require(turn != null, "No path to initial food");
                var keys = snake().controls().keys(0);
                key(switch (turn) { case UP -> keys.up(); case DOWN -> keys.down(); case LEFT -> keys.left(); case RIGHT -> keys.right(); });
                snake().render(.1f); require(snake().snapshot().state() == SnakeGame.State.RUNNING, "Died while collecting food");
            }
            require(snake().snapshot().players().getFirst().score() == targetScore, "Target food not collected");
        }
        private SnakeScreen snake() { return (SnakeScreen) getScreen(); }
        private UiScreen ui() { return (UiScreen) getScreen(); }
        private Actor actor(String name) { return ui().stage().getRoot().findActor(name); }
        private void key(int key) { Gdx.input.getInputProcessor().keyDown(key); Gdx.input.getInputProcessor().keyUp(key); }
        private void click(String name) {
            Actor target = actor(name); require(target != null, "Missing control: " + name);
            Vector2 point = target.localToStageCoordinates(new Vector2(target.getWidth() / 2, target.getHeight() / 2));
            Actor hit = ui().stage().hit(point.x, point.y, true);
            require(hit == target || hit != null && hit.isDescendantOf(target), "Control unreachable: " + name + ", hit=" + hit);
            ui().stage().stageToScreenCoordinates(point);
            require(point.x >= 0 && point.y >= 0 && point.x < Gdx.graphics.getWidth() && point.y < Gdx.graphics.getHeight(),
                    "Control outside window: " + name + " at " + point);
            Gdx.input.getInputProcessor().touchDown(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
            Gdx.input.getInputProcessor().touchUp(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
        }
        private void snapshot(String name) {
            ui().stage().act(.2f);
            // 等待模态正常淡入完成并实际重绘，不能把创建 Actor 前一帧误当作榜单图像证据。
            Actor modal = actor("snake-ranking-dialog");
            if (modal != null) {
                // 顺序动作可能先设置 alpha，再于下一帧淡入，单次 act 不能保证动作链已完成。
                for (int frame = 0; frame < 8; frame++) { ui().stage().act(.1f); ui().render(0f); }
                require(modal.getColor().a >= .99f, "Leaderboard fade-in did not complete before capture");
            }
            ui().render(0f);
            Pixmap image = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            try { PixmapIO.writePNG(new FileHandle(directory.resolve(name).toFile()), image, 6, true); }
            finally { image.dispose(); }
        }
    }
}
