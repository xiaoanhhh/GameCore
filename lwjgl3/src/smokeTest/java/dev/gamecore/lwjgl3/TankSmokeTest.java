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
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.api.event.GameEvent;
import dev.gamecore.client.screen.TankScreen;
import dev.gamecore.client.screen.UiScreen;
import dev.gamecore.client.tank.TankMapAsset;
import dev.gamecore.tank.ArenaBot;
import dev.gamecore.tank.TankBot;
import dev.gamecore.tank.TankCommand;
import dev.gamecore.tank.TankGame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 真窗口走目录、Stage 命中和键盘按下/释放；验收驾驶员也只发输入，不注入或篡改世界状态。 */
public final class TankSmokeTest {
    private TankSmokeTest() { }
    public static void main(String[] args) throws IOException {
        Path directory = Path.of(args[0]); Files.createDirectories(directory); Files.deleteIfExists(directory.resolve("result.txt"));
        Probe probe = new Probe(directory);
        var config = Lwjgl3Launcher.configuration(DesktopSettings.defaults()); config.disableAudio(true);
        new Lwjgl3Application(probe, config);
        require(probe.finished && probe.isDisposed(), "Tank acceptance did not finish");
        require(probe.pages.stream().allMatch(UiScreen::isDisposed), "Tank Stage retained");
        require(probe.pages.stream().filter(TankScreen.class::isInstance).map(TankScreen.class::cast).allMatch(page -> page.effectSnapshot().isEmpty()), "Particle entities retained after disposal");
        require(probe.maps.stream().allMatch(TankMapAsset::isDisposed), "Tank map resource retained");
        require(Gdx.input.getInputProcessor() == null, "Tank input retained"); probe.dispose();
        Files.writeString(directory.resolve("result.txt"), "TANK_SMOKE_OK\nDuelPressRelease=true\nPauseResumeFocus=true\n"
                + "ThreeBotLevels=true\nTwoTmxMaps=true\nVisualLogicParity=true\nCombatResult=true\n"
                + "RestartReady=true\nEcsMuzzleParticles=true\nEcsPauseFrozen=true\nEcsRestartDisposed=true\nEcsExplosionFrozenRules=true\nSlowFramePolicy=true\nResizeAspect=true\nGlyphCoverage=true\n"
                + "MapResourcesDisposed=true\nStagesDisposed=true\nInputDetached=true\nOpenGL=" + probe.glVersion + "\n");
        System.out.println("TANK_SMOKE_OK");
    }
    private static void require(boolean condition, String message) { if (!condition) { throw new IllegalStateException(message); } }
    private static final class Probe extends GameCoreClient {
        private final Path directory;
        private final List<UiScreen> pages = new ArrayList<>();
        private final List<TankMapAsset> maps = new ArrayList<>();
        private final ArenaBot driver = new ArenaBot(TankBot.Level.HARD, 73);
        private int step;
        private int frame;
        private int drivenTicks;
        private boolean finished;
        private String glVersion;
        private Probe(Path directory) { super(directory.resolve("preferences.properties")); this.directory = directory; }
        private UiScreen ui() { return (UiScreen) getScreen(); }
        private TankScreen tank() { return (TankScreen) getScreen(); }
        private Actor actor(String name) { return ui().stage().getRoot().findActor(name); }
        @Override public void render() {
            getScreen().render(0);
            if (!pages.contains(ui())) { pages.add(ui()); }
            if (++frame % 3 != 0) { return; }
            switch (step) {
                case 0 -> { click("game-tank"); step++; }
                case 1 -> { capture("tank-detail.png"); click("mode-duel"); click("start-preview"); step++; }
                case 2 -> {
                    require(tank().snapshot() != null && tank().snapshot().state() == TankGame.State.READY, "Tank not ready");
                    maps.add(tank().mapAsset());
                    for (char glyph : Gdx.files.internal("fonts/characters.txt").readString("UTF-8").toCharArray()) {
                        if (!Character.isWhitespace(glyph)) { require(assets().body.getData().hasGlyph(glyph), "Missing glyph: " + glyph); }
                    }
                    capture("duel-ready.png"); click("tank-action");
                    var before = tank().snapshot();
                    down(tank().controls().keys(0).up()); down(tank().controls().keys(1).up());
                    for (int i = 0; i < 10; i++) { tank().render(1f / 60); }
                    require(tank().snapshot().tanks().get(0).position().x() > before.tanks().get(0).position().x(), "P1 did not move");
                    require(tank().snapshot().tanks().get(1).position().x() < before.tanks().get(1).position().x(), "P2 did not move");
                    up(tank().controls().keys(0).up()); up(tank().controls().keys(1).up());
                    var stopped = tank().snapshot().tanks().get(0).position(); tank().render(1f / 60);
                    require(stopped.equals(tank().snapshot().tanks().get(0).position()), "Key release did not stop tank");
                    down(Input.Keys.SPACE); down(Input.Keys.ENTER); tank().render(1f / 60); up(Input.Keys.SPACE); up(Input.Keys.ENTER);
                    require(tank().snapshot().bullets().stream().map(b -> b.owner()).distinct().count() == 2, "Duel fire keys not independent");
                    require(tank().effectSnapshot().stream().anyMatch(particle -> particle.kind() == GameEvent.Effect.Kind.SHOT), "ECS muzzle particles missing");
                    tank().render(.08f);
                    require(tank().effectSnapshot().stream().map(particle -> particle.y()).distinct().count() > 1, "Muzzle particles did not spread");
                    capture("duel-muzzle.png");
                    down(tank().controls().keys(0).up()); pause(); var paused = tank().snapshot(); var pausedEffects = tank().effectSnapshot(); tank().render(20);
                    require(tank().effectSnapshot().equals(pausedEffects), "Paused ECS advanced");
                    require(tank().snapshot().equals(paused), "Paused Tank advanced");
                    key(Input.Keys.P); tank().render(1f / 60);
                    require(tank().snapshot().tanks().get(0).position().equals(paused.tanks().get(0).position()), "Focus pause retained held key");
                    tank().render(1); require(tank().slowFrames() == 1, "Slow frame policy absent");
                    key(Input.Keys.ESCAPE); capture("duel-paused.png"); click("tank-restart");
                    require(tank().effectSnapshot().isEmpty(), "Restart retained particle entities");
                    require(tank().snapshot().tick() == 0 && tank().snapshot().state() == TankGame.State.READY, "Restart not fresh");
                    maps.add(tank().mapAsset()); Gdx.graphics.setWindowedMode(1600, 900); step++;
                }
                case 3 -> {
                    capture("duel-1600.png");
                    Actor board = actor("tank-board"); require(Math.abs(board.getWidth() / board.getHeight() - 24f / 18) < 1e-5f, "Arena stretched");
                    Gdx.graphics.setWindowedMode(960, 800); step++;
                }
                case 4 -> { capture("duel-small.png"); Gdx.graphics.setWindowedMode(1280, 720); step++; }
                case 5 -> { click("tank-action"); step++; }
                case 6 -> {
                    for (int i = 0; i < 60 && tank().snapshot().state() == TankGame.State.RUNNING; i++) {
                        TankCommand command = driver.command(tank().snapshot(), 0); drive(command); tank().render(1f / 60); drivenTicks++;
                    }
                    if (tank().snapshot().state() == TankGame.State.FINISHED) {
                        require(tank().snapshot().result() == TankGame.Result.PLAYER_ONE, "Acceptance driver failed combat");
                        require(((Label) actor("tank-state")).getText().toString().contains("P1 获胜"), "Result UI missing");
                        require(tank().effectSnapshot().stream().anyMatch(particle -> particle.kind() == GameEvent.Effect.Kind.DESTROYED), "ECS explosion missing");
                        var terminal = tank().snapshot(); tank().render(.2f);
                        require(terminal.equals(tank().snapshot()), "Visual explosion advanced terminal rules");
                        require(tank().effectSnapshot().stream().anyMatch(particle -> particle.kind() == GameEvent.Effect.Kind.DESTROYED && particle.alpha() < 1), "Explosion did not fade");
                        capture("duel-result.png"); click("return-home"); step++;
                    } else { require(drivenTicks < 6000, "Combat never settled"); }
                }
                case 7 -> { click("game-tank"); step++; }
                case 8 -> { click("mode-bot"); click("start-preview"); step++; }
                case 9 -> {
                    for (int level = 0; level < 3; level++) {
                        TankMapAsset old = tank().mapAsset();
                        ((SelectBox<?>) actor("tank-difficulty")).setSelectedIndex(level);
                        if (old != tank().mapAsset()) { require(old.isDisposed(), "Difficulty switch leaked old map"); }
                        maps.add(tank().mapAsset()); click("tank-action");
                        for (int i = 0; i < 120; i++) { tank().render(1f / 60); }
                        require(tank().snapshot().tick() == 120, "Bot level did not simulate");
                        require(tank().snapshot().tanks().get(1).position().distanceSquared(tank().snapshot().map().spawns().get(1).position()) > .01
                                || tank().snapshot().tanks().get(1).heading() != 180, "Bot inactive");
                        capture("bot-level-" + level + ".png"); click("tank-restart");
                    }
                    step++;
                }
                case 10 -> {
                    click("tank-action"); click("tank-map");
                    require(tank().snapshot().state() == TankGame.State.PAUSED, "Map selector did not pause gameplay");
                    require(((SelectBox<?>) actor("tank-map")).getScrollPane().getStage() != null, "Map selector did not open");
                    key(Input.Keys.ESCAPE);
                    require(tank().snapshot().state() == TankGame.State.PAUSED, "Closing selector resumed gameplay");
                    TankMapAsset old = tank().mapAsset(); ((SelectBox<?>) actor("tank-map")).setSelectedIndex(1);
                    require(old.isDisposed() && tank().snapshot().map().id().equals("ruins"), "Map switch did not replace rules/resources");
                    maps.add(tank().mapAsset()); require(tank().snapshot().state() == TankGame.State.READY, "Map switch not ready");
                    click("tank-action"); for (int i = 0; i < 180; i++) { tank().render(1f / 60); }
                    capture("bot-ruins.png"); click("return-home"); step++;
                }
                case 11 -> { glVersion = Gdx.gl.glGetString(GL20.GL_VERSION); finished = true; click("exit"); step++; }
                default -> { }
            }
            require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "Tank OpenGL error");
        }
        private void drive(TankCommand command) {
            var keys = tank().controls().keys(0);
            for (int key : new int[]{keys.up(), keys.down(), keys.left(), keys.right(), Input.Keys.SPACE}) { up(key); }
            if (command.throttle() != 0) { down(command.throttle() > 0 ? keys.up() : keys.down()); }
            if (command.turn() != 0) { down(command.turn() > 0 ? keys.left() : keys.right()); }
            if (command.fire()) { down(Input.Keys.SPACE); }
        }
        private void down(int key) { Gdx.input.getInputProcessor().keyDown(key); }
        private void up(int key) { Gdx.input.getInputProcessor().keyUp(key); }
        private void key(int key) { down(key); up(key); }
        private void click(String name) {
            Actor target = actor(name); require(target != null, "Missing actor: " + name);
            for (Actor parent = target.getParent(); parent != null; parent = parent.getParent()) {
                if (parent instanceof ScrollPane scroll) {
                    Vector2 location = target.localToAscendantCoordinates(scroll.getActor(), new Vector2());
                    scroll.validate();
                    scroll.setScrollY(dev.gamecore.client.ui.ScrollReveal.offset(scroll.getScrollY(), scroll.getMaxY(),
                            scroll.getScrollHeight(), location.y, target.getHeight())); scroll.updateVisualScroll();
                    ui().stage().act(.2f); ui().stage().draw(); break;
                }
            }
            Vector2 point = target.localToStageCoordinates(new Vector2(target.getWidth() / 2, target.getHeight() / 2));
            Actor hit = ui().stage().hit(point.x, point.y, true);
            require(hit == target || hit != null && hit.isDescendantOf(target), "Control unreachable: " + name + " hit=" + hit);
            ui().stage().stageToScreenCoordinates(point);
            require(point.x >= 0 && point.y >= 0 && point.x < Gdx.graphics.getWidth() && point.y < Gdx.graphics.getHeight(), "Control outside screen");
            Gdx.input.getInputProcessor().touchDown(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
            Gdx.input.getInputProcessor().touchUp(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
        }
        private void capture(String name) {
            for (int i = 0; i < 4; i++) { ui().stage().act(.1f); ui().render(0); }
            Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            try { PixmapIO.writePNG(new FileHandle(directory.resolve(name).toFile()), pixmap, 6, true); } finally { pixmap.dispose(); }
        }
    }
}
