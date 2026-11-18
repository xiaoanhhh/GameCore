package dev.gamecore.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.navigation.LobbyNavigation;
import dev.gamecore.client.screen.SnakeScreen;
import dev.gamecore.client.screen.TankScreen;
import dev.gamecore.client.screen.FightScreen;
import dev.gamecore.api.session.SessionState;
import dev.gamecore.tank.TankGame;
import dev.gamecore.client.screen.UiScreen;
import dev.gamecore.client.settings.ClientSettings;
import dev.gamecore.client.settings.KeyBindings;
import dev.gamecore.client.settings.SettingsStore;
import dev.gamecore.snake.SnakeGame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.glfw.GLFW;

/** 同一个真实 LWJGL3 应用中创建大厅和独立游戏窗口，所有断言/输入均在所属窗口的 render 中执行。 */
public final class GameWindowSmokeTest {
    private GameWindowSmokeTest() { }
    public static void main(String[] args) throws IOException {
        Path report = Path.of(args[0]); Files.createDirectories(report);
        Files.deleteIfExists(report.resolve("result.txt"));
        Path settingsFile = report.resolve("preferences.properties");
        var defaults = ClientSettings.defaults();
        new SettingsStore(settingsFile).save(new ClientSettings(0, defaults.resolution(), true,
                new KeyBindings(Input.Keys.I, Input.Keys.K, Input.Keys.J, Input.Keys.L)));
        State state = new State(report);
        Host host = new Host(state);
        Lobby lobby = new Lobby(settingsFile, host, state);
        var configuration = Lwjgl3Launcher.configuration(DesktopSettings.defaults()); configuration.disableAudio(true);
        new Lwjgl3Application(lobby, configuration);
        require(lobby.isDisposed() && state.finished, "Lobby did not finish and dispose");
        require(state.games.size() == 5 && state.games.stream().allMatch(GameCoreClient::isDisposed), "A game window leaked");
        require(state.backgrounds.stream().allMatch(texture -> texture.getTextureObjectHandle() == 0), "A background texture leaked");
        require(host.count() == 0, "Closed window registration retained");
        require(state.inputs.stream().allMatch(input -> input.getInputProcessor() == null), "A closed window retained input");
        Files.writeString(report.resolve("result.txt"), "GAME_WINDOW_SMOKE_OK\nNewNativeWindow=true\nStartsMaximized=true\n"
                + "LobbyRetained=true\nLobbyNavigationWhileGameOpen=true\nConcurrentModes=true\nDuplicateLaunchReusesWindow=true\nBoardUsesAvailableHeight=true\n"
                + "ResizeReflows=true\nConfiguredKeys=true\nFocusLossPausesOwner=true\nSharedRecords=true\n"
                + "ReturnClosesOnlyGame=true\nNativeCloseKeepsLobby=true\nLobbyCloseDisposesAll=true\nInputDetached=true\n"
                + "TankWindowInputAndReturn=true\nFightWindowInputAndReturn=true\nBackgroundsOwnedPerWindow=true\nBackgroundsReleased=true\n" + state.metrics);
        System.out.println("GAME_WINDOW_SMOKE_OK");
    }
    private static void require(boolean condition, String message) { if (!condition) { throw new IllegalStateException(message); } }
    private static UiScreen ui(GameCoreClient app) { return (UiScreen) app.getScreen(); }
    private static Actor actor(GameCoreClient app, String name) { return ui(app).stage().getRoot().findActor(name); }
    private static void key(int key) { Gdx.input.getInputProcessor().keyDown(key); Gdx.input.getInputProcessor().keyUp(key); }
    private static void click(GameCoreClient app, String name) {
        Actor target = actor(app, name); require(target != null, "Missing control: " + name);
        Vector2 point = target.localToStageCoordinates(new Vector2(target.getWidth() / 2, target.getHeight() / 2));
        Actor hit = ui(app).stage().hit(point.x, point.y, true);
        require(hit == target || hit != null && hit.isDescendantOf(target), "Control unreachable: " + name + ", hit=" + hit);
        ui(app).stage().stageToScreenCoordinates(point);
        require(point.x >= 0 && point.y >= 0 && point.x < Gdx.graphics.getWidth() && point.y < Gdx.graphics.getHeight(),
                "Control outside window: " + name + " at " + point);
        Gdx.input.getInputProcessor().touchDown(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
        Gdx.input.getInputProcessor().touchUp(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
    }
    private static void snapshot(GameCoreClient app, Path path) {
        ui(app).stage().act(.2f);
        ui(app).render(0f);
        Pixmap image = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try { PixmapIO.writePNG(new FileHandle(path.toFile()), image, 6, true); } finally { image.dispose(); }
    }
    private static final class State {
        private final Path report;
        private final List<GameProbe> games = new ArrayList<>();
        private final List<Input> inputs = new ArrayList<>();
        private final List<com.badlogic.gdx.graphics.Texture> backgrounds = new ArrayList<>();
        private final StringBuilder metrics = new StringBuilder();
        private long lobbyHandle;
        private UiScreen retainedDetail;
        private boolean finished;
        private boolean lobbyNavigationConfirmed;
        private State(Path report) { this.report = report; }
    }
    private static final class Host extends DesktopGameWindows {
        private final State state;
        private Host(State state) { this.state = state; }
        @Override protected GameCoreClient createClient(GameCoreClient owner, String game, String mode, Runnable close, Runnable disposed) {
            GameProbe probe = new GameProbe(owner, game, mode, close, disposed, state);
            state.games.add(probe); return probe;
        }
    }
    private static final class Lobby extends GameCoreClient {
        private final Host host;
        private final State state;
        private int frame;
        private int step;
        private int loggedStep = -1;
        private Lobby(Path settingsFile, Host host, State state) { super(settingsFile, host); this.host = host; this.state = state; }
        @Override public void create() {
            super.create(); state.lobbyHandle = ((Lwjgl3Graphics) Gdx.graphics).getWindow().getWindowHandle(); state.inputs.add(Gdx.input);
            state.backgrounds.add(assets().background);
        }
        @Override public void render() {
            getScreen().render(0f);
            if (++frame % 3 != 0) { return; }
            if (loggedStep != step) { System.out.println("Window test lobby step=" + step); loggedStep = step; }
            switch (step) {
                case 0 -> { click(this, "game-snake"); step++; }
                case 1 -> {
                    require(navigation().current().page() == LobbyNavigation.Page.DETAIL, "Wrong lobby page");
                    state.retainedDetail = ui(this);
                    click(this, "start-preview"); click(this, "start-preview");
                    require(host.count() == 1 && state.games.size() == 1, "Double launch created duplicate windows");
                    step++;
                }
                case 2 -> {
                    if (!state.games.getFirst().ready) { return; }
                    require(ui(this) == state.retainedDetail && !ui(this).isDisposed(), "Launching game replaced lobby");
                    click(this, "mode-duo"); click(this, "start-preview");
                    require(host.count() == 2, "Different modes did not coexist"); step++;
                }
                case 3 -> {
                    if (state.games.size() < 2 || !state.games.get(1).isDisposed()) { return; }
                    require(!state.games.getFirst().isDisposed() && host.count() == 1, "Closing second mode closed first game");
                    click(this, "nav-settings"); step++;
                }
                case 4 -> {
                    require(navigation().current().page() == LobbyNavigation.Page.SETTINGS, "Lobby settings did not open independently");
                    require(((Lwjgl3Graphics) Gdx.graphics).getWindow().getWindowHandle() == state.lobbyHandle, "Lobby lost its rendering context");
                    key(Input.Keys.ESCAPE); step++;
                }
                case 5 -> {
                    require(navigation().current().page() == LobbyNavigation.Page.DETAIL, "Lobby settings return lost detail");
                    state.retainedDetail = ui(this); state.lobbyNavigationConfirmed = true; step++;
                }
                case 6 -> {
                    if (!state.games.getFirst().isDisposed()) { return; }
                    require(host.count() == 0 && ui(this) == state.retainedDetail && !ui(this).isDisposed(), "Game close replaced or disposed lobby");
                    key(Input.Keys.TAB); require(ui(this).stage().getKeyboardFocus() != null, "Lobby input stopped");
                    snapshot(this, state.report.resolve("lobby-after-game.png"));
                    home(); step++;
                }
                case 7 -> { click(this, "game-tank"); step++; }
                case 8 -> { click(this, "mode-bot"); click(this, "start-preview"); step++; }
                case 9 -> {
                    if (state.games.size() < 3 || !state.games.get(2).isDisposed()) { return; }
                    require(state.games.get(2).ready && host.count() == 0, "Tank return did not close only preview");
                    home(); step++;
                }
                case 10 -> { detail("fight"); step++; }
                case 11 -> { click(this, "mode-duel"); click(this, "start-preview"); step++; }
                case 12 -> {
                    if (state.games.size() < 4 || !state.games.get(3).isDisposed()) { return; }
                    require(state.games.get(3).ready && host.count() == 0, "Fight return did not close only game");
                    home(); step++;
                }
                case 13 -> { click(this, "game-snake"); step++; }
                case 14 -> { click(this, "mode-obstacles"); click(this, "start-preview"); step++; }
                case 15 -> {
                    if (state.games.size() < 5 || !state.games.get(4).ready) { return; }
                    require(host.count() == 1 && !state.games.get(4).isDisposed(), "Expected active child at lobby exit");
                    state.finished = true;
                    var window = ((Lwjgl3Graphics) Gdx.graphics).getWindow();
                    if (window.getWindowListener().closeRequested()) { window.closeWindow(); }
                    step++;
                }
                default -> { }
            }
            require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "Lobby GL error");
        }
    }
    private static final class GameProbe extends GameCoreClient {
        private final String mode;
        private final String gameId;
        private final State state;
        private final GameCoreClient lobby;
        private int frame;
        private int step;
        private boolean ready;
        private GameProbe(GameCoreClient owner, String game, String mode, Runnable close, Runnable disposed, State state) {
            super(owner, game, mode, close, disposed); this.mode = mode; this.state = state; lobby = owner; gameId = game;
        }
        @Override public void create() {
            System.out.println("Window test create game=" + mode);
            super.create(); state.inputs.add(Gdx.input);
            require(assets().background != null && state.backgrounds.stream().noneMatch(texture -> texture == assets().background),
                    "Windows share background resource ownership");
            state.backgrounds.add(assets().background);
            System.out.println("Window test game ready=" + mode);
        }
        private SnakeScreen snake() { return (SnakeScreen) getScreen(); }
        @Override public void render() {
            getScreen().render(0f);
            if (++frame % 4 != 0) { return; }
            var window = ((Lwjgl3Graphics) Gdx.graphics).getWindow();
            if (step == 0) {
                require(window.getWindowHandle() != state.lobbyHandle && window.getWindowHandle() != 0, "Game reused lobby native window");
                require(GLFW.glfwGetWindowAttrib(window.getWindowHandle(), GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE, "Game did not start maximized");
                require(isGameWindow() && actor(this, "nav-home") == null, "Lobby sidebar present in game window");
                require(snakeRecords() == lobby.snakeRecords(), "Game loaded a stale independent records store");
                require(settings().keys().up() == Input.Keys.I, "Configured keys not supplied");
                if (gameId.equals("tank")) {
                    TankScreen tank = (TankScreen) getScreen(); click(this, "tank-action");
                    double previousX = tank.snapshot().tanks().getFirst().position().x();
                    Gdx.input.getInputProcessor().keyDown(Input.Keys.I); tank.render(1f / 60);
                    Gdx.input.getInputProcessor().keyUp(Input.Keys.I);
                    require(tank.snapshot().tanks().getFirst().position().x() > previousX, "Tank ignored configured input");
                    window.getWindowListener().focusLost(); require(tank.snapshot().state() == TankGame.State.PAUSED, "Tank focus pause failed");
                    snapshot(this, state.report.resolve("tank-maximized.png"));
                    window.restoreWindow(); Gdx.graphics.setWindowedMode(1280, 720); step++; return;
                }
                if (gameId.equals("fight")) {
                    FightScreen fight = (FightScreen) getScreen(); snapshot(this,state.report.resolve("selection-maximized.png"));
                    click(this,"select-next");click(this,"select-next"); key(Input.Keys.ENTER);
                    Gdx.input.getInputProcessor().keyDown(Input.Keys.D); fight.render(1f / 60);
                    Gdx.input.getInputProcessor().keyUp(Input.Keys.D);
                    require(fight.snapshot().fighters().getFirst().x() > 560, "Fight input missed owning window");
                    window.getWindowListener().focusLost(); require(fight.snapshot().state() == SessionState.PAUSED, "Fight focus pause failed");
                    snapshot(this, state.report.resolve("fight-maximized.png"));
                    window.restoreWindow(); Gdx.graphics.setWindowedMode(1280, 720); step++; return;
                }
                Actor board = actor(this, "snake-board");
                Actor kicker = actor(this, "snake-hud-kicker");
                Vector2 kickerPoint = kicker.localToStageCoordinates(new Vector2());
                if (kickerPoint.y < 0 || kickerPoint.y + kicker.getHeight() > ui(this).stage().getViewport().getWorldHeight()) {
                    snapshot(this, state.report.resolve(mode + "-hud-heading-failure.png"));
                }
                require(kickerPoint.y >= 0 && kickerPoint.y + kicker.getHeight() <= ui(this).stage().getViewport().getWorldHeight(),
                        "HUD heading out of view: mode=" + mode + ", point=" + kickerPoint + ", height=" + kicker.getHeight()
                                + ", world=" + ui(this).stage().getViewport().getWorldHeight());
                float scale = ui(this).stage().getViewport().getScreenHeight() / ui(this).stage().getViewport().getWorldHeight();
                require(board.getWidth() == board.getHeight(), "Board is not square");
                require(board.getHeight() / ui(this).stage().getViewport().getWorldHeight() > .94f, "Board did not use available height");
                state.metrics.append(mode).append("Framebuffer=").append(Gdx.graphics.getBackBufferWidth()).append('x')
                        .append(Gdx.graphics.getBackBufferHeight()).append(", BoardPixels=").append(board.getWidth() * scale).append('\n');
                snapshot(this, state.report.resolve(mode + "-maximized.png"));
                if (mode.equals("duo")) {
                    if (window.getWindowListener().closeRequested()) { window.closeWindow(); }
                    step++; return;
                }
                if (mode.equals("obstacles")) {
                    require(snake().snapshot().obstacles().size() == 16, "Chosen mode lost"); ready = true; step++; return;
                }
                click(this, "snake-action"); key(Input.Keys.I); snake().render(.1f);
                require(snake().snapshot().players().getFirst().head().y() == 11, "Child input did not reach Snake");
                window.getWindowListener().focusLost();
                require(snake().snapshot().state() == SnakeGame.State.PAUSED, "Focus loss did not pause target child");
                window.restoreWindow(); Gdx.graphics.setWindowedMode(1280, 720); step++;
            } else if (step == 1 && gameId.equals("tank")) {
                snapshot(this, state.report.resolve("tank-1280.png"));
                key(Input.Keys.P); require(((TankScreen) getScreen()).snapshot().state() == TankGame.State.RUNNING, "Tank resume failed");
                ready = true; click(this, "return-home"); step++;
            } else if (step == 1 && gameId.equals("fight")) {
                snapshot(this, state.report.resolve("fight-1280.png")); key(Input.Keys.P);
                require(((FightScreen) getScreen()).snapshot().state() == SessionState.RUNNING, "Fight resume failed");
                ready = true; click(this, "return-home"); step++;
            } else if (step == 1 && mode.equals("classic")) {
                snake().render(.1f); snake().render(.1f);
                require(actor(this, "snake-board").getWidth() >= 695, "Restored window kept old 440px board");
                require(ui(this).stage().getViewport().getWorldWidth() == 1280, "Unexpected restored viewport");
                snapshot(this, state.report.resolve("classic-1280.png"));
                click(this, "snake-leaderboard");
                for (int i = 0; i < 8; i++) { ui(this).stage().act(.1f); }
                snapshot(this, state.report.resolve("ranking-window.png")); key(Input.Keys.ESCAPE);
                ready = true; step++;
            } else if (step == 2 && mode.equals("classic") && state.lobbyNavigationConfirmed) {
                click(this, "return-home"); step++;
            }
            require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "Game GL error");
        }
    }
}
