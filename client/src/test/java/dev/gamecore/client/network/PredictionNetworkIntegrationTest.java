package dev.gamecore.client.network;

import dev.gamecore.network.experiment.NetworkProfile;
import dev.gamecore.server.NettyGameServer;
import dev.gamecore.server.ServerSettings;
import dev.gamecore.tank.TankCommand;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static dev.gamecore.client.network.RemoteIntegrationTest.until;
import static org.junit.jupiter.api.Assertions.*;

/** 真实Netty/长度帧/Protobuf路径上的开发故障注入，纯模型实验不能替代这些集成检查。 */
@Timeout(40)
class PredictionNetworkIntegrationTest {
    private static void close(RemoteGameSession session) throws Exception {
        session.close(); session.network().termination().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(session.network().isTerminated()); assertEquals(0, session.predictionMetrics().pendingInputs());
    }
    @Test void delayedRealTcpClientsPredictToggleAndConvergeAfterStop() throws Exception {
        try (var server = NettyGameServer.start(ServerSettings.defaults(0))) {
            var profile = new NetworkProfile(200, 20, 5, 42, 0, 0);
            var a = new RemoteGameSession(new SnapshotReceiver(), profile, true);
            var b = new RemoteGameSession(new SnapshotReceiver(), profile, true);
            try {
                var endpoint = new ServerEndpoint("127.0.0.1", server.address().getPort()); a.connect(endpoint, "a"); b.connect(endpoint, "b");
                Runnable idle = () -> { a.update(TankCommand.IDLE, System.nanoTime()); b.update(TankCommand.IDLE, System.nanoTime()); };
                until(() -> a.state() == ConnectionState.RUNNING && b.state() == ConnectionState.RUNNING, idle);
                assertTrue(a.predictionSupported() && a.predictionEnabled());
                double start = a.authoritativeSnapshot().tanks().get(a.slot()).position().x();
                until(() -> a.acknowledgedSequence() >= 5 && a.authoritativeSnapshot().tanks().get(a.slot()).position().x() != start,
                        () -> { a.update(new TankCommand(1, 1, false), System.nanoTime()); b.update(TankCommand.IDLE, System.nanoTime()); });
                assertTrue(a.network().metrics().rttNanos() >= 100_000_000L);
                assertTrue(a.network().metrics().snapshots() > 3); assertTrue(a.predictionMetrics().pendingInputs() <= 128);
                a.predictionEnabled(false); idle.run();
                assertEquals(a.authoritativeSnapshot().tanks().get(a.slot()), a.snapshot().tanks().get(a.slot()));
                a.interpolationEnabled(false); idle.run(); assertEquals(a.authoritativeSnapshot(), a.snapshot());
                a.predictionEnabled(true); a.interpolationEnabled(true);
                long stoppedAt = System.nanoTime();
                until(() -> System.nanoTime() - stoppedAt > 1_000_000_000L
                        && a.authoritativeSnapshot().tanks().get(a.slot()).position().distanceSquared(a.snapshot().tanks().get(a.slot()).position()) < 1e-8, idle);
                assertNotNull(a.network().simulation()); assertEquals(0, a.network().simulation().outboundMetrics().overflows());
                assertEquals(ConnectionState.RUNNING, a.state());
            } finally { close(a); close(b); }
        }
    }
    @Test void shortApplicationBlackoutRecoversOnSameTcpConnectionAndRealDisconnectClearsPrediction() throws Exception {
        try (var server = NettyGameServer.start(ServerSettings.defaults(0))) {
            var profile = new NetworkProfile(50, 20, 0, 73, 1500, 250);
            var a = new RemoteGameSession(new SnapshotReceiver(), profile, true); var b = new RemoteGameSession();
            try {
                var endpoint = new ServerEndpoint("localhost", server.address().getPort()); a.connect(endpoint, "blackout"); b.connect(endpoint, "peer");
                Runnable update = () -> { a.update(new TankCommand(1, 0, false), System.nanoTime()); b.update(TankCommand.IDLE, System.nanoTime()); };
                until(() -> a.state() == ConnectionState.RUNNING && b.state() == ConnectionState.RUNNING, update);
                until(() -> a.network().simulation().inboundTrace().stream().anyMatch(trace -> trace.action().equals("BLACKOUT")), update);
                long before = a.serverTick();
                until(() -> a.serverTick() > before + 15 && a.acknowledgedSequence() > 5, update);
                assertEquals(ConnectionState.RUNNING, a.state()); assertTrue(a.predictionMetrics().pendingInputs() <= 128);
                a.close(); until(() -> b.state().terminal(), () -> b.update(TankCommand.IDLE, System.nanoTime()));
                assertEquals(ConnectionState.DISCONNECTED, b.state()); assertEquals(0, b.predictionMetrics().pendingInputs());
            } finally { close(a); close(b); }
        }
    }
    @Test void publicEndpointAndNonDevelopmentModeRejectInjectionWithoutOpeningAConnection() throws Exception {
        for (var endpoint : new ServerEndpoint[]{new ServerEndpoint("203.0.113.10", 7100), new ServerEndpoint("127.0.0.1", 7100)}) {
            var profile = new NetworkProfile(100, 20, 5, 42, 0, 0);
            var session = new RemoteGameSession(new SnapshotReceiver(), profile, !endpoint.loopback());
            try {
                session.connect(endpoint, "guard"); until(() -> session.state().terminal(), () -> session.update(TankCommand.IDLE, System.nanoTime()));
                assertEquals(ConnectionState.FAILED, session.state()); assertTrue(session.message().contains("LOCAL DEV"));
                assertNull(session.network().simulation());
            } finally { close(session); }
        }
    }
    @Test void actualDisconnectAllowsFreshMatchWithNewInputHistoryInsteadOfReusingOldPrediction() throws Exception {
        try (var server = NettyGameServer.start(ServerSettings.defaults(0))) {
            var endpoint = new ServerEndpoint("127.0.0.1", server.address().getPort());
            var a = new RemoteGameSession(); var b = new RemoteGameSession(); String oldMatch;
            try {
                a.connect(endpoint, "old-a"); b.connect(endpoint, "old-b");
                until(() -> a.state() == ConnectionState.RUNNING && b.state() == ConnectionState.RUNNING,
                        () -> { a.update(new TankCommand(1, 0, false), System.nanoTime()); b.update(TankCommand.IDLE, System.nanoTime()); });
                oldMatch = a.matchId(); a.close();
                until(() -> b.state().terminal(), () -> b.update(TankCommand.IDLE, System.nanoTime()));
            } finally { close(a); close(b); }
            until(() -> server.connectionCount() == 0, () -> { });
            var c = new RemoteGameSession(); var d = new RemoteGameSession();
            try {
                c.connect(endpoint, "new-a"); d.connect(endpoint, "new-b");
                until(() -> c.state() == ConnectionState.RUNNING && d.state() == ConnectionState.RUNNING,
                        () -> { c.update(TankCommand.IDLE, System.nanoTime()); d.update(TankCommand.IDLE, System.nanoTime()); });
                assertNotEquals(oldMatch, c.matchId()); assertEquals(0, c.predictionMetrics().corrections());
                assertTrue(c.predictionMetrics().pendingInputs() <= 1);
            } finally { close(c); close(d); }
        }
    }
}
