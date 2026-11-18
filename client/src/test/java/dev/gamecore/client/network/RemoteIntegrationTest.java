package dev.gamecore.client.network;

import dev.gamecore.server.ClientTestServer;
import dev.gamecore.server.NettyGameServer;
import dev.gamecore.server.ServerSettings;
import dev.gamecore.tank.TankCommand;
import dev.gamecore.tank.TankGame;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

/** 与真实服务器的非图形联调；仅在测试控制线程限时轮询异步回调，网络/渲染生产路径均不等待。 */
@Timeout(30)
class RemoteIntegrationTest {
    private static final CountDownLatch CALLBACK_WAIT = new CountDownLatch(1);
    static void until(BooleanSupplier condition, Runnable update) throws InterruptedException {
        long deadline = System.nanoTime() + 8_000_000_000L;
        while (!condition.getAsBoolean()) {
            update.run(); if (System.nanoTime() >= deadline) { fail("Asynchronous condition timed out"); }
            CALLBACK_WAIT.await(5, TimeUnit.MILLISECONDS);
        }
    }
    private static void connect(RemoteGameSession remote, NettyGameServer server) { remote.connect(new ServerEndpoint("127.0.0.1", server.address().getPort()), "desktop"); }
    private static void close(RemoteGameSession remote) throws Exception { remote.close(); remote.network().termination().toCompletableFuture().get(5, TimeUnit.SECONDS); assertTrue(remote.network().isTerminated()); }
    @Test void twoRemotesMoveAssignedSlotsAndConsumeAcknowledgementsWithoutLocalSimulation() throws Exception {
        try (var server = NettyGameServer.start(ServerSettings.defaults(0))) {
            var a = new RemoteGameSession(); var b = new RemoteGameSession();
            try {
                connect(a, server); connect(b, server);
                until(() -> a.state() == ConnectionState.RUNNING && b.state() == ConnectionState.RUNNING,
                        () -> { a.update(TankCommand.IDLE, System.nanoTime()); b.update(TankCommand.IDLE, System.nanoTime()); });
                assertNotEquals(a.slot(), b.slot()); double before = a.snapshot().tanks().get(a.slot()).position().x();
                until(() -> a.acknowledgedSequence() >= 3, () -> { a.update(new TankCommand(1, 0, true), System.nanoTime()); b.update(TankCommand.IDLE, System.nanoTime()); });
                assertNotEquals(before, a.snapshot().tanks().get(a.slot()).position().x()); assertEquals(a.serverTick() * 2, a.snapshot().tick());
                assertEquals(5, a.snapshot().tanks().get(a.slot()).health()); assertTrue(a.eventId() > 0);
            } finally { close(a); close(b); }
        }
    }
    @Test void serverCollisionDamageAndWinnerProduceExactlySameFinalResultInBothRemotes() throws Exception {
        var map = ClientTestServer.openMap();
        try (var server = ClientTestServer.start(map)) {
            var a = new RemoteGameSession(new SnapshotReceiver(id -> map)); var b = new RemoteGameSession(new SnapshotReceiver(id -> map));
            try {
                connect(a, server); connect(b, server);
                until(() -> a.result() != null && b.result() != null,
                        () -> { a.update(new TankCommand(0, 0, true), System.nanoTime()); b.update(TankCommand.IDLE, System.nanoTime()); });
                assertEquals(ConnectionState.FINISHED, a.state()); assertEquals(ConnectionState.FINISHED, b.state()); assertEquals(a.result(), b.result());
                assertEquals(a.slot() == 0 ? TankGame.Result.PLAYER_ONE : TankGame.Result.PLAYER_TWO, a.snapshot().result());
                assertEquals(a.snapshot(), b.snapshot()); assertEquals(0, a.snapshot().tanks().get(b.slot()).health());
            } finally { close(a); close(b); }
        }
    }
    @Test void heartbeatKeepsWaitingPlayerAliveWithoutTickingOrSubmittingGameInput() throws Exception {
        try (var server = NettyGameServer.start(new ServerSettings("127.0.0.1", 0, 30, "crossfire", 42, 60, 2))) {
            var remote = new RemoteGameSession();
            try {
                connect(remote, server); until(() -> remote.snapshot() != null, () -> remote.update(TankCommand.IDLE, System.nanoTime()));
                long end = System.nanoTime() + 3_200_000_000L;
                until(() -> System.nanoTime() >= end, () -> { remote.update(TankCommand.IDLE, System.nanoTime()); assertEquals(ConnectionState.WAITING, remote.state()); });
                assertEquals(0, remote.serverTick()); assertEquals(0, remote.acknowledgedSequence()); assertEquals(1, server.connectionCount());
            } finally { close(remote); }
        }
    }
    @Test void thirdPlayerGetsFriendlyRefusalAndDisconnectIsSafeForOtherPlayer() throws Exception {
        try (var server = NettyGameServer.start(ServerSettings.defaults(0))) {
            var a = new RemoteGameSession(); var b = new RemoteGameSession(); var third = new RemoteGameSession();
            try {
                connect(a, server); connect(b, server);
                until(() -> a.state() == ConnectionState.RUNNING && b.state() == ConnectionState.RUNNING,
                        () -> { a.update(TankCommand.IDLE, System.nanoTime()); b.update(TankCommand.IDLE, System.nanoTime()); });
                connect(third, server); until(() -> third.state().terminal(), () -> third.update(TankCommand.IDLE, System.nanoTime()));
                assertEquals(ConnectionState.REJECTED, third.state()); assertTrue(third.message().contains("房间已满"));
                a.close(); until(() -> b.state().terminal(), () -> b.update(TankCommand.IDLE, System.nanoTime()));
                assertEquals(ConnectionState.DISCONNECTED, b.state()); assertTrue(b.message().contains("比赛取消"));
            } finally { close(a); close(b); close(third); }
        }
    }
    @Test void repeatedEntryExitAndImmediateCancelReleaseEveryClientLoop() throws Exception {
        try (var server = NettyGameServer.start(ServerSettings.defaults(0))) {
            for (int round = 0; round < 10; round++) {
                var remote = new RemoteGameSession();
                try {
                    connect(remote, server);
                    if (round % 2 == 0) { until(() -> remote.snapshot() != null, () -> remote.update(TankCommand.IDLE, System.nanoTime())); assertEquals(ConnectionState.WAITING, remote.state()); }
                } finally { close(remote); }
                until(() -> server.connectionCount() == 0, () -> { }); assertEquals(0, server.queuedInputs());
            }
        }
    }
    @Test void refusedConnectionDoesNotThrowIntoUpdateAndCanBeDisposed() throws Exception {
        int port; try (var reserved = new ServerSocket(0)) { port = reserved.getLocalPort(); }
        var remote = new RemoteGameSession();
        try {
            remote.connect(new ServerEndpoint("localhost", port), "desktop");
            until(() -> remote.state().terminal(), () -> assertDoesNotThrow(() -> remote.update(TankCommand.IDLE, System.nanoTime())));
            assertEquals(ConnectionState.FAILED, remote.state()); assertTrue(remote.message().contains("无法连接"));
        } finally { close(remote); }
    }
    @Test void serverShutdownReachesTerminalAndAllNetworkResourcesTerminate() throws Exception {
        var server = NettyGameServer.start(ServerSettings.defaults(0)); var remote = new RemoteGameSession();
        try {
            connect(remote, server); until(() -> remote.snapshot() != null, () -> remote.update(TankCommand.IDLE, System.nanoTime()));
            server.close(); until(() -> remote.state().terminal(), () -> remote.update(TankCommand.IDLE, System.nanoTime()));
            assertEquals(ConnectionState.DISCONNECTED, remote.state()); assertTrue(server.isTerminated());
        } finally { close(remote); server.close(); }
    }
}
