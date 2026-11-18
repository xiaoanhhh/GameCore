package dev.gamecore.client.traffic;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.MessageLite;
import dev.gamecore.client.network.ConnectionState;
import dev.gamecore.client.network.RemoteArenaSession;
import dev.gamecore.client.network.RemoteGameSession;
import dev.gamecore.client.network.ServerEndpoint;
import dev.gamecore.client.platform.PlatformEndpoint;
import dev.gamecore.client.platform.PlatformSession;
import dev.gamecore.network.experiment.NetworkProfile;
import dev.gamecore.network.transport.TrafficObserver;
import dev.gamecore.online.GameRegistry;
import dev.gamecore.online.tank.TankFactory;
import dev.gamecore.platform.api.PlatformHttpClient;
import dev.gamecore.platform.api.PlatformMessages.Login;
import dev.gamecore.platform.api.PlatformMessages.Profile;
import dev.gamecore.protocol.GameProtocol;
import dev.gamecore.server.NettyGameServer;
import dev.gamecore.server.ServerSettings;
import dev.gamecore.server.multigame.NettyMultiGameServer;
import dev.gamecore.server.multigame.RoomHost;
import dev.gamecore.tank.TankCommand;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** 真实回环 Socket/HTTP 上的端到端采集；测试线程限时轮询，生产路径不等待。 */
@Timeout(30)
class TrafficCaptureIntegrationTest {
    private static final Profile DEVELOPER = new Profile("id-1", "testuser", "开发者", "USER");
    private static final String SECRET_PASSWORD = "sentinel-password", SECRET_TOKEN = "sentinel-token";

    private static void until(BooleanSupplier condition, Runnable update) throws InterruptedException {
        var pause = new CountDownLatch(1);
        long deadline = System.nanoTime() + 12_000_000_000L;
        while (!condition.getAsBoolean()) {
            update.run();
            if (System.nanoTime() >= deadline) { fail("Asynchronous condition timed out"); }
            pause.await(5, TimeUnit.MILLISECONDS);
        }
    }
    private static boolean seen(TrafficMonitor monitor, TrafficKind kind, TrafficDirection direction) {
        return monitor.log().entries().stream().anyMatch(entry -> entry.kind() == kind && entry.direction() == direction);
    }
    private static boolean text(TrafficMonitor monitor, String value) {
        return monitor.log().entries().stream().anyMatch(entry -> entry.text().contains(value));
    }

    @Test void realArenaMatchProducesEveryLiveKindWithoutCredentials() throws Exception {
        var monitor = new TrafficMonitor();
        var recorder = monitor.observer(DEVELOPER);
        // 记下服务器实际下发的连接凭证，结束后确认它没有进入任何一行记录。
        var credential = new AtomicReference<String>();
        var observer = new TrafficObserver() {
            @Override public void sent(MessageLite message) { recorder.sent(message); }
            @Override public void received(MessageLite message) {
                if (message instanceof GameProtocol.Envelope value && value.hasWelcome()) { credential.set(value.getWelcome().getCredential()); }
                recorder.received(message);
            }
            @Override public void connection(String state) { recorder.connection(state); }
        };
        var games = GameRegistry.builtIn();
        var factory = new TankFactory();
        try (var server = NettyMultiGameServer.start("127.0.0.1", 0, games, RoomHost.defaults(games), false)) {
            int port = server.address().getPort();
            try (var a = new RemoteArenaSession(factory, "127.0.0.1", port, "tank-1", "", false, true, observer);
                 var b = new RemoteArenaSession(factory, "127.0.0.1", port, "tank-1", "", false, true)) {
                Runnable step = () -> {
                    a.update(() -> factory.inputs().encode(new TankCommand(1, 0, false)), System.nanoTime());
                    b.update(() -> factory.inputs().encode(TankCommand.IDLE), System.nanoTime());
                    monitor.log().flush();
                };
                until(() -> a.roomState() != null && b.roomState() != null, step);
                a.control(GameProtocol.RoomAction.READY);
                b.control(GameProtocol.RoomAction.READY);
                until(() -> "RUNNING".equals(a.status()), step);
                until(() -> seen(monitor, TrafficKind.INPUT, TrafficDirection.SENT)
                        && seen(monitor, TrafficKind.SNAPSHOT, TrafficDirection.RECEIVED)
                        && text(monitor, "往返") && seen(monitor, TrafficKind.ROOM, TrafficDirection.SENT)
                        && seen(monitor, TrafficKind.ROOM, TrafficDirection.RECEIVED), step);
                assertTrue(text(monitor, "TCP 已连接"));
                assertTrue(text(monitor, "握手请求"));
                assertTrue(text(monitor, "握手完成"));
                assertTrue(text(monitor, "加入房间 tank-1"));
                assertTrue(text(monitor, "已加入 tank-1"));
            }
            until(() -> text(monitor, "TCP 已断开"), () -> { });
        }
        assertFalse(credential.get().isBlank());
        assertTrue(monitor.log().entries().stream().noneMatch(entry -> entry.text().contains(credential.get())));
    }

    @Test void disconnectedArenaEndpointIsReportedAsFailure() throws Exception {
        var monitor = new TrafficMonitor();
        var observer = monitor.observer(DEVELOPER);
        int port;
        try (var socket = new java.net.ServerSocket(0)) { port = socket.getLocalPort(); }
        try (var session = new RemoteArenaSession(new TankFactory(), "127.0.0.1", port, "tank-1", "", false, true, observer)) {
            until(() -> text(monitor, "连接失败 CONNECT_FAILED"), () -> { });
            assertFalse(session.network().active());
        }
    }

    @Test void disabledMonitorInstallsNothingAndRealMatchStillWorks() throws Exception {
        var monitor = new TrafficMonitor();
        var games = GameRegistry.builtIn();
        try (var server = NettyMultiGameServer.start("127.0.0.1", 0, games, RoomHost.defaults(games), false);
             var session = new RemoteArenaSession(new TankFactory(), "127.0.0.1", server.address().getPort(), "tank-1", "", false, true, monitor.observer(null))) {
            session.network().ready().get(5, TimeUnit.SECONDS);
            assertTrue(monitor.log().entries().isEmpty());
        }
    }

    @Test void legacyTankConnectionIsCapturedOnTheOriginalEndpoint() throws Exception {
        var monitor = new TrafficMonitor();
        try (var server = NettyGameServer.start(ServerSettings.defaults(0))) {
            var a = new RemoteGameSession(NetworkProfile.NONE, monitor.observer(DEVELOPER));
            var b = new RemoteGameSession(NetworkProfile.NONE);
            try {
                var endpoint = new ServerEndpoint("127.0.0.1", server.address().getPort());
                a.connect(endpoint, "desktop");
                b.connect(endpoint, "desktop");
                Runnable step = () -> { a.update(new TankCommand(1, 0, false), System.nanoTime()); b.update(TankCommand.IDLE, System.nanoTime()); monitor.log().flush(); };
                until(() -> a.state() == ConnectionState.RUNNING && b.state() == ConnectionState.RUNNING, step);
                until(() -> seen(monitor, TrafficKind.INPUT, TrafficDirection.SENT) && seen(monitor, TrafficKind.SNAPSHOT, TrafficDirection.RECEIVED)
                        && text(monitor, "往返"), step);
                assertTrue(text(monitor, "握手请求"));
                assertTrue(text(monitor, "tank/1"));
                assertTrue(text(monitor, "已加入 tank-1"));
            } finally { a.close(); b.close(); }
        }
    }

    @Test void platformHttpIsRecordedThroughTheSessionAfterLogin() throws Exception {
        var monitor = new TrafficMonitor();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 8);
        server.createContext("/api/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            exchange.getRequestBody().readAllBytes();
            Object value = path.equals("/api/auth/login") ? new Login(SECRET_TOKEN, 123, DEVELOPER) : List.of();
            int status = path.equals("/api/games") ? 503 : 200;
            byte[] bytes = PlatformHttpClient.JSON.writeValueAsBytes(value);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
            exchange.close();
        });
        server.start();
        var session = new PlatformSession(new PlatformEndpoint("http://127.0.0.1:" + server.getAddress().getPort(), ""));
        session.observer(monitor::exchange);
        try {
            session.login("testuser", SECRET_PASSWORD, false);
            assertTrue(monitor.log().entries().isEmpty(), "Before the account is known nothing may be stored");
            assertTrue(monitor.sync(session.profile()));
            session.client().call("GET", "/api/rooms", null, Object[].class);
            assertThrows(PlatformHttpClient.Failure.class, () -> session.client().call("GET", "/api/games", null, Object[].class));
            var entries = monitor.log().entries();
            assertEquals(TrafficKind.PLATFORM, entries.get(1).kind());
            assertTrue(entries.get(1).text().startsWith("GET /api/rooms · 200 · "));
            assertEquals(TrafficKind.ERROR, entries.get(2).kind());
            assertTrue(entries.get(2).text().startsWith("GET /api/games · 503 "));
            for (var entry : entries) {
                assertFalse(entry.text().contains(SECRET_PASSWORD));
                assertFalse(entry.text().contains(SECRET_TOKEN));
            }
            session.expire();
            assertFalse(monitor.sync(session.profile()));
            assertTrue(monitor.log().entries().isEmpty());
        } finally { session.closeAsync(); server.stop(0); }
    }
}
