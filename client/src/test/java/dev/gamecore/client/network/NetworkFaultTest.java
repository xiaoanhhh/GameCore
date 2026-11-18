package dev.gamecore.client.network;

import dev.gamecore.network.transport.ProtocolMessages;
import dev.gamecore.network.transport.ProtocolPipeline;
import dev.gamecore.protocol.TankProtocol;
import dev.gamecore.tank.TankCommand;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

/** 受控坏服务器只在测试控制线程使用阻塞Socket；验证异常协议最终成为友好状态而非抛向UI。 */
@Timeout(25)
class NetworkFaultTest {
    @FunctionalInterface private interface Scenario { void run(DataInputStream input, DataOutputStream output) throws Exception; }
    private static TankProtocol.ServerHello hello(boolean heartbeat) {
        return TankProtocol.ServerHello.newBuilder().setConnectionId("test-connection").setJoinCredential("token")
                .setServerTps(30).setSimulationTps(60).setRulesVersion("tank/1").setHeartbeatSupported(heartbeat).build();
    }
    private static TankProtocol.Envelope read(DataInputStream input) throws Exception {
        int size = input.readInt(); assertTrue(size > 0 && size <= ProtocolPipeline.MAX_BODY);
        return TankProtocol.Envelope.parseFrom(input.readNBytes(size));
    }
    private static void write(DataOutputStream output, TankProtocol.Envelope value) throws Exception {
        var bytes = value.toByteArray(); output.writeInt(bytes.length); output.write(bytes); output.flush();
    }
    private static final class Endpoint implements AutoCloseable {
        private final ServerSocket listener;
        private volatile Socket child;
        private final java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();
        private final java.util.concurrent.Future<?> task;
        Endpoint(Scenario scenario) throws Exception {
            listener = new ServerSocket(0);
            task = executor.submit(() -> {
                try (var socket = listener.accept()) {
                    child = socket; socket.setSoTimeout(6000);
                    var input = new DataInputStream(socket.getInputStream()); var output = new DataOutputStream(socket.getOutputStream());
                    scenario.run(input, output);
                    while (input.read() != -1) { /* 等客户端真正退出，不因正常输入误关受控服务器。 */ }
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            });
        }
        @Override public void close() {
            try {
                task.get(8, TimeUnit.SECONDS);
                listener.close(); if (child != null) { child.close(); }
            } catch (Exception failure) { throw new AssertionError("Fault server failed", failure); }
            finally {
                try { listener.close(); if (child != null) { child.close(); } } catch (java.io.IOException ignored) { /* 测试清理。 */ }
                executor.shutdownNow();
                try { assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
            }
        }
    }
    private static void rejectedBy(Scenario scenario, ConnectionState expected, String text) throws Exception {
        try (var endpoint = new Endpoint(scenario)) {
            var remote = new RemoteGameSession();
            try {
                remote.connect(new ServerEndpoint("127.0.0.1", endpoint.listener.getLocalPort()), "desktop");
                RemoteIntegrationTest.until(() -> remote.state().terminal(), () -> assertDoesNotThrow(() -> remote.update(TankCommand.IDLE, System.nanoTime())));
                assertEquals(expected, remote.state()); assertTrue(remote.message().contains(text), remote.message());
            } finally { remote.close(); remote.network().termination().toCompletableFuture().get(5, TimeUnit.SECONDS); }
        }
    }
    @Test void incompatibleProtocolVersionIsExplicit() throws Exception {
        rejectedBy((input, output) -> {
            assertTrue(read(input).hasClientHello());
            write(output, ProtocolMessages.envelope(1, builder -> builder.setServerHello(hello(true))).toBuilder().setProtocolVersion(999).build());
        }, ConnectionState.INCOMPATIBLE, "协议版本不兼容");
    }
    @Test void oldServerWithoutHeartbeatCapabilityIsRejectedBeforeJoin() throws Exception {
        rejectedBy((input, output) -> { read(input); write(output, ProtocolMessages.envelope(1, builder -> builder.setServerHello(hello(false)))); }, ConnectionState.INCOMPATIBLE, "请更新服务器");
    }
    @Test void oldServerWithoutPredictionFallsBackAndStaleSnapshotsDoNotCrashTheUi() throws Exception {
        try (var endpoint = new Endpoint((input, output) -> {
            read(input); write(output, ProtocolMessages.envelope(1, builder -> builder.setServerHello(hello(true))));
            read(input); write(output, ProtocolMessages.envelope(2, builder -> builder.setJoinRoomResponse(TankProtocol.JoinRoomResponse.newBuilder()
                    .setRoomId("tank-1").setMatchId("match").setPlayerSlot(0))));
            var value = NetworkModelTest.world().toBuilder().setState(TankProtocol.RoomState.RUNNING).setServerTick(2).setSimulationTick(4).build();
            write(output, ProtocolMessages.envelope(0, builder -> builder.setWorldSnapshot(value)));
            write(output, ProtocolMessages.envelope(0, builder -> builder.setWorldSnapshot(value.toBuilder().setServerTick(1).setSimulationTick(2))));
        })) {
            var remote = new RemoteGameSession();
            try {
                remote.connect(new ServerEndpoint("127.0.0.1", endpoint.listener.getLocalPort()), "legacy");
                RemoteIntegrationTest.until(() -> remote.serverTick() == 2 && remote.staleSnapshots() > 0,
                        () -> assertDoesNotThrow(() -> remote.update(TankCommand.IDLE, System.nanoTime())));
                assertEquals(ConnectionState.RUNNING, remote.state()); assertFalse(remote.predictionSupported()); assertFalse(remote.predictionEnabled());
                assertEquals(remote.authoritativeSnapshot().tanks().getFirst(), remote.snapshot().tanks().getFirst());
            } finally { remote.close(); remote.network().termination().toCompletableFuture().get(5, TimeUnit.SECONDS); }
        }
    }
    @Test void invalidSnapshotDoesNotEscapeRemoteUpdateOrReachRenderingObjects() throws Exception {
        rejectedBy((input, output) -> {
            read(input); write(output, ProtocolMessages.envelope(1, builder -> builder.setServerHello(hello(true))));
            assertTrue(read(input).hasJoinRoomRequest());
            write(output, ProtocolMessages.envelope(2, builder -> builder.setJoinRoomResponse(TankProtocol.JoinRoomResponse.newBuilder().setRoomId("tank-1").setMatchId("match").setPlayerSlot(0))));
            var value = NetworkModelTest.world(); var bad = value.toBuilder().setPlayers(0, value.getPlayers(0).toBuilder().setX(Double.NaN)).build();
            write(output, ProtocolMessages.envelope(0, builder -> builder.setWorldSnapshot(bad)));
        }, ConnectionState.FAILED, "数据异常");
    }
    @Test void oversizedOrMalformedFramesProduceSafeDisconnect() throws Exception {
        rejectedBy((input, output) -> { read(input); output.writeInt(ProtocolPipeline.MAX_BODY + 1); output.flush(); }, ConnectionState.DISCONNECTED, "异常数据");
        rejectedBy((input, output) -> { read(input); output.writeInt(1); output.writeByte(0x80); output.flush(); }, ConnectionState.DISCONNECTED, "异常数据");
    }
    @Test void serverCannotSendPlayerInputsAsIfTheyWereWorldState() throws Exception {
        rejectedBy((input, output) -> { read(input); write(output, ProtocolMessages.envelope(1, builder -> builder.setPlayerInput(TankProtocol.PlayerInput.getDefaultInstance()))); }, ConnectionState.FAILED, "消息异常");
    }
}
