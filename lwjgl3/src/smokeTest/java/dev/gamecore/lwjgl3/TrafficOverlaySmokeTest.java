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
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.network.RemoteArenaSession;
import dev.gamecore.client.screen.HomeScreen;
import dev.gamecore.client.screen.OnlineArenaScreen;
import dev.gamecore.client.screen.PlatformScreen;
import dev.gamecore.client.screen.SettingsScreen;
import dev.gamecore.client.screen.SnakeScreen;
import dev.gamecore.client.screen.UiScreen;
import dev.gamecore.client.traffic.TrafficDirection;
import dev.gamecore.client.traffic.TrafficEntry;
import dev.gamecore.client.traffic.TrafficKind;
import dev.gamecore.client.traffic.TrafficOverlay;
import dev.gamecore.client.traffic.TrafficPanelModel;
import dev.gamecore.client.traffic.TrafficPanelModel.Control;
import dev.gamecore.online.GameRegistry;
import dev.gamecore.online.tank.TankFactory;
import dev.gamecore.platform.api.PlatformHttpClient;
import dev.gamecore.platform.api.PlatformMessages.Login;
import dev.gamecore.platform.api.PlatformMessages.Profile;
import dev.gamecore.protocol.GameProtocol;
import dev.gamecore.server.multigame.NettyMultiGameServer;
import dev.gamecore.server.multigame.RoomHost;
import dev.gamecore.tank.TankCommand;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 真实窗口验收交互记录面板：账户开关、真实输入坐标下的拖动和点击隔离、跨页面绘制、
 * 真实回环对局中的类型颜色、窗口缩放、退出登录，以及其他账户和独立窗口。
 * 平台是回环假服务，游戏服务器是进程内的真实服务器。
 */
public final class TrafficOverlaySmokeTest {
    private static final String PASSWORD = "test-password-only";
    private TrafficOverlaySmokeTest() { }

    public static void main(String[] args) throws Exception {
        try { verify(args); }
        catch (Throwable failure) {
            // 窗口循环异常后残留的线程会让进程一直挂到任务超时，这里立即带着错误码退出。
            failure.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private static void verify(String[] args) throws Exception {
        Path out = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(out);
        System.setProperty("gamecore.offline", "false");
        var games = GameRegistry.builtIn();
        try (var server = NettyMultiGameServer.start("127.0.0.1", 0, games, RoomHost.defaults(games), false)) {
            System.setProperty("gamecore.multi.host", "127.0.0.1");
            System.setProperty("gamecore.multi.port", Integer.toString(server.address().getPort()));
            run(out, "developer", new Profile("id-1", "testuser", "开发者", "USER"), false);
            run(out, "windows", new Profile("id-1", "testuser", "开发者", "USER"), true);
            run(out, "other", new Profile("id-2", "player01", "testuser", "USER"), false);
        } finally {
            System.clearProperty("gamecore.offline");
            System.clearProperty("gamecore.platform.url");
            System.clearProperty("gamecore.multi.host");
            System.clearProperty("gamecore.multi.port");
        }
        Files.writeString(out.resolve("result.txt"), "TRAFFIC_OVERLAY_OK\nAccountGate=true\nDisplayNameRejected=true\nDragAndClamp=true\n"
                + "ClickIsolation=true\nControlsAndToggle=true\nEveryPage=true\nLiveMatchKinds=true\nResize=true\nLogoutClears=true\nGameWindow=true\n");
        System.out.println("TRAFFIC_OVERLAY_OK");
    }

    private static void run(Path out, String scenario, Profile profile, boolean separateWindows) throws Exception {
        Path directory = out.resolve(scenario);
        Files.createDirectories(directory);
        HttpServer http = fakePlatform(profile);
        System.setProperty("gamecore.platform.url", "http://127.0.0.1:" + http.getAddress().getPort());
        try {
            var windows = separateWindows ? new TrackedWindows() : null;
            var probe = new Probe(directory, scenario, windows);
            var config = Lwjgl3Launcher.configuration(DesktopSettings.defaults());
            config.disableAudio(true);
            config.setTitle("Traffic overlay: " + scenario);
            new Lwjgl3Application(probe, config);
            probe.platform().termination().get(8, TimeUnit.SECONDS);
            if (!probe.done || !probe.isDisposed()) { throw new IllegalStateException("Incomplete scenario " + scenario + " phase " + probe.phase); }
        } finally { http.stop(0); }
    }

    private static HttpServer fakePlatform(Profile profile) throws IOException {
        var http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 8);
        http.createContext("/api/public/lobby", e -> respond(e, "{\"games\":[],\"rooms\":[]}"));
        http.createContext("/api/auth/login", e -> { e.getRequestBody().readAllBytes(); respond(e, PlatformHttpClient.JSON.writeValueAsString(new Login("temporary-token", 123, profile))); });
        http.createContext("/api/games", e -> respond(e, "[]"));
        http.createContext("/api/rooms", e -> respond(e, "[]"));
        http.start();
        return http;
    }
    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
        exchange.close();
    }

    /** 记下独立窗口的客户端，用于断言它有自己的面板。 */
    private static final class TrackedWindows extends DesktopGameWindows {
        volatile GameCoreClient child;
        @Override protected GameCoreClient createClient(GameCoreClient owner, String gameId, String modeId, Runnable close, Runnable disposed) {
            child = super.createClient(owner, gameId, modeId, close, disposed);
            return child;
        }
    }

    private static final class Probe extends GameCoreClient {
        final Path out; final String scenario; final TrackedWindows windows;
        final long deadline = System.nanoTime() + 75_000_000_000L;
        int phase, frames, waited; boolean done;
        float savedX, savedY;
        RemoteArenaSession opponent; OnlineArenaScreen match;
        final TankFactory tank = new TankFactory();
        int focusPauses; long controlsSent, pausesSeen;

        Probe(Path out, String scenario, TrackedWindows windows) {
            super(out.resolve("run-" + UUID.randomUUID()).resolve("preferences.properties"), windows);
            this.out = out; this.scenario = scenario; this.windows = windows;
        }

        // ---- 观察与输入工具 ----
        TrafficOverlay overlay() { return trafficOverlay(); }
        TrafficPanelModel panel() { return overlay().panel(); }
        UiScreen page() { return (UiScreen) getScreen(); }
        Actor actor(String id) { return getScreen() instanceof UiScreen ui ? ui.stage().getRoot().findActor(id) : null; }
        boolean notice(String value) { return actor("platform-notice") instanceof Label label && label.getText().toString().contains(value); }
        List<TrafficEntry> entries() { return traffic().log().entries(); }
        Set<TrafficKind> kinds() { return entries().stream().map(TrafficEntry::kind).collect(Collectors.toSet()); }
        void require(boolean value, String message) { if (!value) { throw new IllegalStateException(message + " [" + scenario + " phase " + phase + "]"); } }
        void click(String id) {
            var a = actor(id);
            require(a != null, "Missing " + id);
            var p = a.localToStageCoordinates(new Vector2(a.getWidth() / 2, a.getHeight() / 2));
            page().stage().stageToScreenCoordinates(p);
            var input = Gdx.input.getInputProcessor();
            input.touchDown(Math.round(p.x), Math.round(p.y), 0, Input.Buttons.LEFT);
            input.touchUp(Math.round(p.x), Math.round(p.y), 0, Input.Buttons.LEFT);
        }
        boolean press(float x, float y) {
            var p = overlay().screen(x, y);
            var input = Gdx.input.getInputProcessor();
            boolean handled = input.touchDown(Math.round(p.x), Math.round(p.y), 0, Input.Buttons.LEFT);
            input.touchUp(Math.round(p.x), Math.round(p.y), 0, Input.Buttons.LEFT);
            return handled;
        }
        boolean press(TrafficPanelModel.Box box) { return press(box.x() + box.width() / 2, box.y() + box.height() / 2); }
        /** 按住标题栏中点，以真实指针事件拖到逻辑坐标 (x, y)。 */
        void dragTitle(float x, float y) {
            var title = panel().title();
            var from = overlay().screen(title.x() + 20, title.y() + title.height() / 2);
            var to = overlay().screen(x, y);
            var input = Gdx.input.getInputProcessor();
            require(input.touchDown(Math.round(from.x), Math.round(from.y), 0, Input.Buttons.LEFT), "Title rejected the press");
            require(panel().dragging(), "Title press did not start a drag");
            input.touchDragged(Math.round(to.x), Math.round(to.y), 0);
            require(input.touchUp(Math.round(to.x), Math.round(to.y), 0, Input.Buttons.LEFT), "Release leaked to the page");
            require(!panel().dragging(), "Drag did not end");
        }
        void requireInside(float width, float height) {
            require(panel().x() >= 0 && panel().x() + panel().width() <= width + .01f, "Panel left the window horizontally: " + panel().x());
            require(panel().y() >= 0 && panel().y() + panel().height() <= height + .01f, "Panel left the window vertically: " + panel().y());
        }
        /** 从帧缓冲读取“错误”图例色条：这种不透明红色只有面板才会画出来，标题栏的深色会和页面卡片混淆。 */
        boolean panelDrawn() {
            var chip = panel().chip(TrafficKind.ERROR);
            var p = overlay().screen(chip.x() + 1.5f, chip.y() + chip.height() / 2);
            Gdx.gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT, 1);
            var pixmap = Pixmap.createFromFrameBuffer(Math.round(p.x), Gdx.graphics.getBackBufferHeight() - Math.round(p.y), 1, 1);
            int rgba = pixmap.getPixel(0, 0);
            pixmap.dispose();
            var color = TrafficKind.ERROR.color;
            float r = (rgba >>> 24) / 255f, g = (rgba >>> 16 & 255) / 255f, b = (rgba >>> 8 & 255) / 255f;
            return Math.abs(r - color.r) < .04f && Math.abs(g - color.g) < .04f && Math.abs(b - color.b) < .04f;
        }
        void capture(String name) {
            Gdx.gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT, 1);
            var pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            PixmapIO.writePNG(new FileHandle(out.resolve(name).toFile()), pixmap, 6, true);
            pixmap.dispose();
        }
        void login() {
            ((TextField) actor("platform-username")).setText("testuser");
            ((TextField) actor("platform-password")).setText(PASSWORD);
            click("platform-login");
        }
        void finish() {
            if (opponent != null) { opponent.close(); opponent = null; }
            done = true;
            Gdx.app.exit();
        }

        @Override public void pause() { focusPauses++; super.pause(); }
        long roomMessagesSent() {
            return entries().stream().filter(entry -> entry.kind() == TrafficKind.ROOM && entry.direction() == TrafficDirection.SENT).count();
        }

        @Override public void render() {
            super.render();
            if (System.nanoTime() > deadline) { throw new IllegalStateException("Traffic overlay timeout " + scenario + " phase " + phase + " kinds " + kinds()); }
            if (opponent != null) { opponent.update(() -> tank.inputs().encode(TankCommand.IDLE), System.nanoTime()); }
            if (++frames % 3 != 0 || done) { return; }
            switch (scenario) {
                case "developer" -> developer();
                case "windows" -> separateWindow();
                default -> otherAccount();
            }
        }

        private void guestChecks() {
            require(!overlay().active() && !overlay().allocated(), "Guest must not get the panel");
            require(!overlay().input().keyDown(TrafficOverlay.TOGGLE_KEY), "F9 must do nothing for guests");
            require(entries().isEmpty(), "Guest traffic must not be recorded");
        }

        private void otherAccount() {
            if (phase == 0 && getScreen() instanceof PlatformScreen && notice("房间列表已更新")) { guestChecks(); click("platform-tab-登录"); phase++; }
            else if (phase == 1 && actor("platform-username") != null) { login(); phase++; }
            else if (phase == 2 && platform().profile() != null && notice("房间列表已更新")) {
                require("testuser".equals(platform().profile().displayName()), "Scenario needs the spoofed display name");
                guestChecks();
                require(!panelDrawn(), "Panel pixels on another account");
                capture("other-account.png");
                finish();
            }
        }

        private void separateWindow() {
            if (phase == 0 && getScreen() instanceof PlatformScreen && notice("房间列表已更新")) { click("platform-tab-登录"); phase++; }
            else if (phase == 1 && actor("platform-username") != null) { login(); phase++; }
            else if (phase == 2 && overlay().active()) { home(); phase++; }
            else if (phase == 3 && getScreen() instanceof HomeScreen) { preview("snake", "classic"); phase++; }
            else if (phase == 4 && windows.child != null && windows.child.trafficOverlay() != null && windows.child.trafficOverlay().allocated()) {
                require(windows.child.trafficOverlay() != overlay(), "Each window owns its panel");
                require(windows.child.traffic() == traffic(), "Windows share one log");
                require(windows.child.getScreen() instanceof SnakeScreen, "Child window should run the game");
                require(windows.child.trafficOverlay().panel().visible(), "Child panel hidden");
                windows.child.home();
                phase++;
            }
            else if (phase == 5 && windows.child.isDisposed()) { require(overlay().active(), "Closing a game window must not log out"); finish(); }
        }

        private void developer() {
            switch (phase) {
                case 0 -> { if (getScreen() instanceof PlatformScreen && notice("房间列表已更新")) { guestChecks(); click("platform-tab-登录"); phase++; } }
                case 1 -> { if (actor("platform-username") != null) { login(); phase++; } }
                case 2 -> {
                    if (!overlay().active() || !kinds().contains(TrafficKind.PLATFORM)) { return; }
                    require(overlay().allocated(), "Panel resources missing");
                    require(entries().getFirst().text().contains("testuser"), "Log should start with the account line");
                    require(entries().stream().noneMatch(entry -> entry.text().contains(PASSWORD) || entry.text().contains("temporary-token")), "Secret in log");
                    require(panel().x() + panel().width() + TrafficPanelModel.MARGIN >= 1280 && panel().y() == TrafficPanelModel.MARGIN, "Panel should start bottom right");
                    require(panelDrawn(), "Panel not drawn in the lobby");
                    capture("01-lobby.png");
                    phase++;
                }
                case 3 -> {
                    var title = panel().title();
                    float x = panel().x(), y = panel().y();
                    dragTitle(title.x() + 20 - 300, title.y() + title.height() / 2 + 200);
                    require(Math.abs(panel().x() - (x - 300)) < 1.5f && Math.abs(panel().y() - (y + 200)) < 1.5f, "Drag distance wrong: " + panel().x() + "," + panel().y());
                    dragTitle(-5000, -5000);
                    require(panel().x() == 0 && panel().y() == 0, "Corner clamp failed");
                    dragTitle(5000, 5000);
                    float width = panel().x() + panel().width(), height = panel().y() + panel().height();
                    requireInside(width, height);
                    require(width >= 1280 - 1 && height >= 720 - 1, "Opposite corner clamp failed: " + width + "x" + height);
                    phase++;
                }
                case 4 -> {
                    // 把面板盖到“返回本地游戏”按钮上：点击要被面板吃掉，页面不能切换。
                    var button = actor("platform-offline");
                    var stagePoint = button.localToStageCoordinates(new Vector2(button.getWidth() / 2, button.getHeight() / 2));
                    var screen = page().stage().stageToScreenCoordinates(new Vector2(stagePoint));
                    var logical = new Vector2(screen.x, Gdx.graphics.getHeight() - screen.y);
                    overlayPoint(logical);
                    float grabY = panel().title().height() / 2;
                    dragTitle(logical.x - TrafficPanelModel.WIDTH / 2 + 20, logical.y + TrafficPanelModel.HEIGHT / 2 - grabY);
                    require(panel().hit(logical.x, logical.y).control() != Control.NONE, "Panel does not cover the button");
                    require(press(logical.x, logical.y), "Panel must consume the click");
                    waited = frames;
                    phase++;
                }
                case 5 -> {
                    if (frames - waited < 12) { return; }
                    require(getScreen() instanceof PlatformScreen, "Click went through the panel");
                    dragTitle(5000, -5000);
                    var p = overlay().screen(20, 700);
                    require(!overlay().input().touchDown(Math.round(p.x), Math.round(p.y), 0, Input.Buttons.LEFT), "Outside click must reach the page");
                    overlay().input().touchUp(Math.round(p.x), Math.round(p.y), 0, Input.Buttons.LEFT);
                    require(!overlay().input().keyDown(Input.Keys.W), "Ordinary keys must reach the page");
                    phase++;
                }
                case 6 -> {
                    require(press(panel().chip(TrafficKind.PLATFORM)) && panel().filtered(TrafficKind.PLATFORM), "Filter click failed");
                    require(panel().rows().stream().noneMatch(row -> row.kind() == TrafficKind.PLATFORM), "Filter kept the kind");
                    waited = frames;
                    phase = 61;
                }
                // 帧缓冲里是上一帧，状态变化后要等新帧画完才能截图。
                case 61 -> {
                    if (frames - waited < 3) { return; }
                    capture("02-filtered.png");
                    require(press(panel().chip(TrafficKind.PLATFORM)) && !panel().filtered(TrafficKind.PLATFORM), "Filter did not toggle back");
                    require(press(panel().button(Control.PAUSE)) && panel().paused(), "Pause click failed");
                    require(press(panel().button(Control.PAUSE)) && !panel().paused(), "Resume click failed");
                    require(press(panel().button(Control.CLEAR)), "Clear click failed");
                    require(traffic().log().entries().size() <= 1 && panel().matching() == 0, "Clear left rows");
                    require(press(panel().button(Control.COLLAPSE)) && panel().collapsed(), "Collapse click failed");
                    waited = frames;
                    phase = 62;
                }
                case 62 -> {
                    if (frames - waited < 3) { return; }
                    capture("03-collapsed.png");
                    require(press(panel().button(Control.COLLAPSE)) && !panel().collapsed(), "Expand click failed");
                    var input = Gdx.input.getInputProcessor();
                    require(input.keyDown(TrafficOverlay.TOGGLE_KEY) && !panel().visible(), "F9 did not hide");
                    input.keyUp(TrafficOverlay.TOGGLE_KEY);
                    var center = panel().title();
                    var p = overlay().screen(center.x() + 20, center.y() + 10);
                    require(!overlay().input().touchDown(Math.round(p.x), Math.round(p.y), 0, Input.Buttons.LEFT), "Hidden panel kept the click");
                    overlay().input().touchUp(Math.round(p.x), Math.round(p.y), 0, Input.Buttons.LEFT);
                    waited = frames;
                    phase = 7;
                }
                case 7 -> {
                    if (frames - waited < 6) { return; }
                    require(!panelDrawn(), "Hidden panel still drawn");
                    require(Gdx.input.getInputProcessor().keyDown(TrafficOverlay.TOGGLE_KEY) && panel().visible(), "F9 did not show");
                    Gdx.input.getInputProcessor().keyUp(TrafficOverlay.TOGGLE_KEY);
                    dragTitle(620, 500);
                    savedX = panel().x(); savedY = panel().y();
                    openSettings();
                    phase++;
                }
                case 8 -> { if (getScreen() instanceof SettingsScreen && samePlace()) { capture("04-settings.png"); home(); phase++; } }
                case 9 -> { if (getScreen() instanceof HomeScreen && samePlace()) { capture("05-home.png"); preview("snake", "classic"); phase++; } }
                case 10 -> { if (getScreen() instanceof SnakeScreen && samePlace()) { capture("06-local-game.png"); home(); phase++; } }
                case 11 -> { if (getScreen() instanceof HomeScreen) { preview("tank", "multiplayer"); phase++; } }
                case 12 -> {
                    if (!(getScreen() instanceof OnlineArenaScreen screen)) { return; }
                    require(samePlace(), "Panel moved on the online page");
                    match = screen;
                    // 面板此时盖在连接表单上，会按设计吃掉点击，先拖开。
                    dragTitle(5000, -5000);
                    click("online-connect");
                    int port = Integer.getInteger("gamecore.multi.port");
                    opponent = new RemoteArenaSession(tank, "127.0.0.1", port, "tank-1", "", false, true);
                    phase++;
                }
                case 13 -> {
                    var mine = match.remote();
                    if (mine == null || mine.roomState() == null || opponent.roomState() == null || mine.roomState().getSeatsCount() < 2) { return; }
                    mine.control(GameProtocol.RoomAction.READY);
                    opponent.control(GameProtocol.RoomAction.READY);
                    phase++;
                }
                case 14 -> {
                    if (!"RUNNING".equals(match.remote().status())) { return; }
                    require(!overlay().input().keyDown(Input.Keys.W), "Game keys must reach the match");
                    Gdx.input.getInputProcessor().keyDown(Input.Keys.W);
                    phase++;
                }
                case 15 -> {
                    if (!kinds().containsAll(Set.of(TrafficKind.CONNECTION, TrafficKind.HEARTBEAT, TrafficKind.INPUT, TrafficKind.SNAPSHOT, TrafficKind.ROOM))
                            || panel().matching() <= TrafficPanelModel.ROWS + 3) { return; }
                    require(entries().stream().anyMatch(entry -> entry.kind() == TrafficKind.SNAPSHOT && entry.text().startsWith("×")), "Snapshots should be merged");
                    require(panelDrawn(), "Panel not drawn in the match");
                    capture("07-match.png");
                    // 滚轮：指针在面板上时向上翻看历史，向下回到实时；面板外不消费。
                    var inside = overlay().screen(panel().x() + 30, panel().y() + 30);
                    var outside = overlay().screen(20, 20);
                    var input = Gdx.input.getInputProcessor();
                    input.mouseMoved(Math.round(inside.x), Math.round(inside.y));
                    require(input.scrolled(0, -1) && panel().offset() == 3 && panel().frozen(), "Wheel up should open history");
                    require(input.scrolled(0, 1) && panel().offset() == 0 && !panel().frozen(), "Wheel down should return to live");
                    input.mouseMoved(Math.round(outside.x), Math.round(outside.y));
                    require(!overlay().input().scrolled(0, -1) && panel().offset() == 0, "Wheel outside the panel belongs to the page");
                    // 对局中拖动：页面状态和输入都不受影响。
                    controlsSent = roomMessagesSent();
                    pausesSeen = focusPauses;
                    dragTitle(300, 400);
                    waited = frames;
                    phase++;
                }
                case 16 -> {
                    if (frames - waited < 9) { return; }
                    // 桌面端失去焦点时会主动暂停对局，这与面板无关，要单独区分开。
                    require(!match.remote().terminal(), "Match ended while dragging: " + match.remote().status());
                    require(focusPauses > pausesSeen || roomMessagesSent() == controlsSent,
                            "Dragging sent a room message, status " + match.remote().status());
                    Gdx.input.getInputProcessor().keyUp(Input.Keys.W);
                    Gdx.graphics.setWindowedMode(960, 640);
                    waited = frames;
                    phase++;
                }
                case 17 -> {
                    if (frames - waited < 24 || Gdx.graphics.getWidth() != 960) { return; }
                    requireInside(page().stage().getViewport().getWorldWidth(), page().stage().getViewport().getWorldHeight());
                    require(panelDrawn(), "Panel lost after the window changed size");
                    capture("08-resized.png");
                    Gdx.graphics.setWindowedMode(1280, 800);
                    waited = frames;
                    phase++;
                }
                case 18 -> {
                    if (frames - waited < 24) { return; }
                    require(panelDrawn(), "Panel lost after restoring the window");
                    platform().expire();
                    waited = frames;
                    phase++;
                }
                case 19 -> {
                    if (frames - waited < 6) { return; }
                    require(!overlay().active() && entries().isEmpty(), "Logout must clear the log");
                    require(!panelDrawn(), "Panel drawn after logout");
                    require(!overlay().input().keyDown(TrafficOverlay.TOGGLE_KEY), "F9 must do nothing after logout");
                    capture("09-logged-out.png");
                    finish();
                }
                default -> { }
            }
        }

        private boolean samePlace() {
            require(panel().x() == savedX && panel().y() == savedY, "Panel moved between pages");
            return panelDrawn();
        }
        /** 窗口像素（左下原点）转面板逻辑坐标。 */
        private void overlayPoint(Vector2 point) {
            var origin = overlay().screen(0, 0);
            var unit = overlay().screen(1, 1);
            float scaleX = unit.x - origin.x, scaleY = origin.y - unit.y;
            point.set((point.x - origin.x) / scaleX, (point.y - (Gdx.graphics.getHeight() - origin.y)) / scaleY);
        }
    }

}
