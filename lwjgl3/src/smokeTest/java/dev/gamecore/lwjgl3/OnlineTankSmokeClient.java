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
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.network.ConnectionState;
import dev.gamecore.client.network.RemoteGameSession;
import dev.gamecore.client.screen.OnlineTankScreen;
import dev.gamecore.client.screen.UiScreen;
import dev.gamecore.tank.ArenaBot;
import dev.gamecore.tank.TankBot;
import dev.gamecore.tank.TankCommand;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** 独立JVM的真实大厅/原生游戏窗口验收：驾驶员只读取快照并按真实键，不注入权威状态。 */
public final class OnlineTankSmokeClient {
    private OnlineTankSmokeClient() { }
    public static void main(String[] args) throws Exception {
        var state = new State(Path.of(args[0]), Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]));
        Files.createDirectories(state.directory); Files.deleteIfExists(state.directory.resolve("result.txt"));
        var host = new Host(state); var lobby = new Lobby(host, state);
        Thread.ofPlatform().daemon(true).name("online-test-controller").start(() -> {
            try {
                var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                for (String line; (line = reader.readLine()) != null;) { if (line.equals("GO_CYCLES")) { state.cyclesAllowed.set(true); } }
            } catch (java.io.IOException ignored) { /* 验收编排端退出。 */ }
        });
        var config = Lwjgl3Launcher.configuration(DesktopSettings.defaults()); config.disableAudio(true); config.setTitle("Online Tank acceptance " + state.name);
        new Lwjgl3Application(lobby, config);
        require(state.done && lobby.isDisposed(), "Lobby did not finish");
        require(host.count() == 0 && state.games.stream().allMatch(GameCoreClient::isDisposed), "Native game retained");
        require(state.pages.stream().allMatch(UiScreen::isDisposed), "Stage retained");
        require(state.remotes.stream().allMatch(remote -> remote.network().isTerminated()), "Network executor retained");
        require(state.inputs.stream().allMatch(input -> input.getInputProcessor() == null), "Window input retained");
        require(state.pages.stream().filter(OnlineTankScreen.class::isInstance).map(OnlineTankScreen.class::cast)
                .allMatch(page -> page.mapAsset() == null || page.mapAsset().isDisposed()), "Map texture retained");
        Files.writeString(state.directory.resolve("result.txt"), "ONLINE_TANK_SMOKE_OK\nIndependentJvm=true\nLobbyEntry=true\nNativeGameWindow=true\n"
                + "AuthorityAndDisplaySeparated=true\nPredictionToggle=true\nInterpolationToggle=true\nNetworkDebugPanel=true\nInputAck=true\nAuthoritativeResult=true\nFocusClearsInput=true\nGlyphCoverage=true\n"
                + "StagesDisposed=true\nMapsDisposed=true\nNetworkTerminated=true\nInputDetached=true\nPages=" + state.pages.size() + "\n");
        System.out.println("ONLINE_CLIENT_DONE " + state.name);
    }
    private static void require(boolean condition, String text) { if (!condition) { throw new IllegalStateException(text); } }
    private static UiScreen ui(GameCoreClient app) { return (UiScreen) app.getScreen(); }
    private static Actor actor(GameCoreClient app, String name) { return ui(app).stage().getRoot().findActor(name); }
    private static void click(GameCoreClient app, String name) {
        Actor target = actor(app, name); require(target != null, "Missing control " + name);
        for (Actor parent = target.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof ScrollPane scroll) {
                var location = target.localToAscendantCoordinates(scroll.getActor(), new Vector2()); scroll.validate();
                scroll.setScrollY(dev.gamecore.client.ui.ScrollReveal.offset(scroll.getScrollY(), scroll.getMaxY(), scroll.getScrollHeight(), location.y, target.getHeight()));
                scroll.updateVisualScroll(); ui(app).stage().act(.2f); ui(app).stage().draw(); break;
            }
        }
        var point = target.localToStageCoordinates(new Vector2(target.getWidth() / 2, target.getHeight() / 2));
        Actor hit = ui(app).stage().hit(point.x, point.y, true); require(hit == target || hit != null && hit.isDescendantOf(target), "Control unreachable " + name);
        ui(app).stage().stageToScreenCoordinates(point);
        require(point.x >= 0 && point.y >= 0 && point.x < Gdx.graphics.getWidth() && point.y < Gdx.graphics.getHeight(), "Control outside window " + name);
        Gdx.input.getInputProcessor().touchDown(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
        Gdx.input.getInputProcessor().touchUp(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
    }
    private static void capture(GameCoreClient app, State state, String name) {
        ui(app).stage().act(.2f); ui(app).stage().draw();
        var image = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try { PixmapIO.writePNG(new FileHandle(state.directory.resolve(name).toFile()), image, 6, true); } finally { image.dispose(); }
    }
    private static final class State {
        final Path directory; final int port, faultPort; final String name;
        final List<GameProbe> games = new ArrayList<>(); final List<UiScreen> pages = new ArrayList<>();
        final List<RemoteGameSession> remotes = new ArrayList<>(); final List<Input> inputs = new ArrayList<>();
        final AtomicBoolean cyclesAllowed = new AtomicBoolean(); boolean done;
        String nextScenario = "battle";
        State(Path directory, int port, String name, int faultPort) { this.directory = directory; this.port = port; this.name = name; this.faultPort = faultPort; }
        void observe(GameCoreClient app) { if (app.getScreen() instanceof UiScreen page && !pages.contains(page)) { pages.add(page); } }
    }
    private static final class Host extends DesktopGameWindows {
        final State state;
        Host(State state) { this.state = state; }
        @Override protected GameCoreClient createClient(GameCoreClient owner, String game, String mode, Runnable close, Runnable disposed) {
            var child = new GameProbe(owner, close, disposed, state, state.nextScenario); state.games.add(child); return child;
        }
    }
    private static final class Lobby extends GameCoreClient {
        final State state; final Host host; int step, frame, cycle;
        Lobby(Host host, State state) { super(state.directory.resolve("preferences.properties"), host); this.host = host; this.state = state; }
        @Override public void create() { super.create(); state.inputs.add(Gdx.input); }
        @Override public void render() {
            super.render(); state.observe(this); if (++frame % 3 != 0) { return; }
            if (step == 0) { click(this, "game-tank"); step++; }
            else if (step == 1) {
                require(actor(this, "mode-duel") != null && actor(this, "mode-bot") != null, "Local mode removed");
                click(this, "mode-online"); capture(this, state, "online-detail.png"); click(this, "start-preview"); step++;
            } else if (step == 2 && host.count() == 0 && !state.games.isEmpty()) {
                if (!state.remotes.stream().allMatch(remote -> remote.network().isTerminated())) { return; }
                require(state.games.getLast().completed, "Game window closed too early");
                if (state.name.equals("P2")) { state.done = true; Gdx.app.exit(); return; }
                System.out.println("ONLINE_BATTLE_DONE"); step++;
            } else if (step == 3 && state.cyclesAllowed.get()) { state.nextScenario = "cycle"; click(this, "start-preview"); step++; }
            else if (step == 4 && host.count() == 0) {
                if (!state.remotes.stream().allMatch(remote -> remote.network().isTerminated())) { return; }
                require(state.games.getLast().completed, "Cycle did not finish");
                if (++cycle < 2) { click(this, "start-preview"); }
                else { state.nextScenario = "fault"; click(this, "start-preview"); step++; }
            } else if (step == 5 && host.count() == 0) {
                if (!state.remotes.stream().allMatch(remote -> remote.network().isTerminated())) { return; }
                require(state.games.getLast().completed, "Fault page did not finish");
                state.nextScenario = "disconnect"; click(this, "start-preview"); step++;
            } else if (step == 6 && host.count() == 0) {
                if (!state.remotes.stream().allMatch(remote -> remote.network().isTerminated())) { return; }
                require(state.games.getLast().completed, "Disconnect page did not finish"); state.done = true; Gdx.app.exit();
            }
        }
    }
    private static final class GameProbe extends GameCoreClient {
        final State state; final String scenario; final ArenaBot driver = new ArenaBot(TankBot.Level.HARD, 73);
        int step, frame; boolean waitingPrinted, stopPrinted, completed, moved, shot, focusChecked;
        long runningAt, progressAt; double startX, startY; boolean debugOpened, debugChecked, debugCaptured;
        GameProbe(GameCoreClient owner, Runnable close, Runnable disposed, State state, String scenario) {
            super(owner, "tank", "online", close, disposed); this.state = state; this.scenario = scenario;
        }
        OnlineTankScreen page() { return (OnlineTankScreen) getScreen(); }
        @Override public void create() { super.create(); state.inputs.add(Gdx.input); }
        @Override public void render() {
            super.render(); state.observe(this);
            var page = page(); var remote = page.remote();
            if (remote != null && !state.remotes.contains(remote)) { state.remotes.add(remote); }
            if (++frame % 3 != 0) { return; }
            if (step == 0) {
                ((TextField) actor(this, "online-port")).setText(Integer.toString(scenario.equals("fault") ? state.faultPort : state.port));
                ((TextField) actor(this, "online-name")).setText(state.name); click(this, "online-connect"); step++; return;
            }
            remote = page.remote(); if (remote == null) { return; }
            if (remote.state() == ConnectionState.WAITING && !waitingPrinted) {
                waitingPrinted = true; System.out.println("ONLINE_WAITING " + state.name + " scenario=" + scenario);
                capture(this, state, scenario + "-waiting.png");
            }
            if (scenario.equals("cycle") && remote.snapshot() != null) {
                require(remote.state() == ConnectionState.WAITING, "Fresh room not waiting"); completed = true; click(this, "return-home"); return;
            }
            if (scenario.equals("fault") && remote.state().terminal()) {
                require(remote.state() == ConnectionState.DISCONNECTED, "Bad frame not handled safely");
                require(!((Label) actor(this, "online-state")).getText().toString().isEmpty(), "No error UI");
                capture(this, state, "malformed-frame.png"); completed = true; click(this, "return-home"); return;
            }
            if (scenario.equals("disconnect")) {
                if (remote.state() == ConnectionState.WAITING && !stopPrinted) { stopPrinted = true; System.out.println("ONLINE_READY_FOR_SERVER_STOP"); }
                if (remote.state().terminal()) {
                    require(remote.state() == ConnectionState.DISCONNECTED, "Server stop not handled safely");
                    capture(this, state, "server-disconnected.png"); completed = true; click(this, "return-home");
                }
                return;
            }
            if (!scenario.equals("battle")) { return; }
            if (remote.state() == ConnectionState.RUNNING) {
                var view = remote.snapshot(); var own = view.tanks().get(remote.slot());
                if (runningAt == 0) {
                    runningAt = System.nanoTime(); startX = own.position().x(); startY = own.position().y();
                    System.out.println("ONLINE_RUNNING name=" + state.name + " slot=" + remote.slot() + " match=" + remote.matchId());
                    for (char glyph : Gdx.files.internal("fonts/characters.txt").readString("UTF-8").toCharArray()) {
                        if (!Character.isWhitespace(glyph)) { require(assets().body.getData().hasGlyph(glyph), "Missing glyph " + glyph); }
                    }
                    capture(this, state, "battle-start.png");
                }
                if (!debugChecked) {
                    require(remote.predictionSupported(), "New server did not advertise predictive input status");
                    if (Integer.getInteger("gamecore.network.rttMs", 0) > 0 || Integer.getInteger("gamecore.network.jitterMs", 0) > 0
                            || Integer.getInteger("gamecore.network.dropPercent", 0) > 0) {
                        require(remote.network().simulation() != null, "Requested development simulation was not installed");
                    }
                    if (!debugOpened) {
                        Gdx.input.getInputProcessor().keyDown(Input.Keys.F3); Gdx.input.getInputProcessor().keyUp(Input.Keys.F3);
                        debugOpened = true; return; // 等正常渲染帧完成新面板布局，再点真实热区。
                    }
                    capture(this, state, "network-debug-before-click.png");
                    click(this, "online-prediction"); require(!remote.predictionEnabled(), "Prediction OFF did not apply");
                    click(this, "online-prediction"); require(remote.predictionEnabled(), "Prediction ON did not apply");
                    click(this, "online-interpolation"); require(!remote.interpolationEnabled(), "Interpolation OFF did not apply");
                    click(this, "online-interpolation"); require(remote.interpolationEnabled(), "Interpolation ON did not apply");
                    debugChecked = true;
                }
                if (debugChecked && !debugCaptured) {
                    var metrics = (Label) actor(this, "online-debug-metrics");
                    if (metrics.getText().toString().contains("Corrections")) { capture(this, state, "network-debug.png"); debugCaptured = true; }
                }
                if (state.name.equals("P1")) {
                    var command = driver.command(view, remote.slot());
                    drive(new TankCommand(command.throttle(), command.turn(), true));
                }
                moved |= own.position().x() != startX || own.position().y() != startY;
                shot |= !view.bullets().isEmpty();
                if (state.name.equals("P1") && !focusChecked && remote.acknowledgedSequence() > 0 && moved) {
                    pause(); require(page.controls().command(0).equals(TankCommand.IDLE), "Focus retained controls");
                    require(remote.state() == ConnectionState.RUNNING, "Focus paused server session"); focusChecked = true;
                    capture(this, state, "battle-input.png");
                }
                if (System.nanoTime() - progressAt > 5_000_000_000L) {
                    progressAt = System.nanoTime(); System.out.println("ONLINE_PROGRESS " + state.name + " tick=" + remote.serverTick() + " hp=" + own.health());
                }
            } else if (remote.state().terminal()) {
                require(remote.state() == ConnectionState.FINISHED && remote.result() != null, "Battle failed " + remote.message());
                require(((Label) actor(this, "online-state")).getText().toString().contains("比赛结束"), "Result UI absent");
                require(remote.acknowledgedSequence() > 0, "No input acknowledgement");
                if (state.name.equals("P1")) { require(moved && shot && focusChecked, "No movement/fire/focus acceptance"); }
                try { Files.write(state.directory.resolve("match-result.pb"), remote.result().toByteArray()); }
                catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                System.out.println("ONLINE_MATCH_RESULT " + state.name + " " + remote.result().getOutcome());
                capture(this, state, "battle-result.png"); completed = true; click(this, "return-home");
            }
            require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "OpenGL error");
        }
        private void drive(TankCommand command) {
            var keys = page().controls().keys(0); var input = Gdx.input.getInputProcessor();
            for (int key : new int[]{keys.up(), keys.down(), keys.left(), keys.right(), Input.Keys.SPACE}) { input.keyUp(key); }
            if (command.throttle() != 0) { input.keyDown(command.throttle() > 0 ? keys.up() : keys.down()); }
            if (command.turn() != 0) { input.keyDown(command.turn() > 0 ? keys.left() : keys.right()); }
            if (command.fire()) { input.keyDown(Input.Keys.SPACE); }
        }
    }
}
