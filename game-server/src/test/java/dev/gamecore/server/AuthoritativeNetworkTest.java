package dev.gamecore.server;

import com.google.protobuf.UnknownFieldSet;
import dev.gamecore.protocol.TankProtocol;
import dev.gamecore.server.client.GameCliClient;
import dev.gamecore.server.core.RoomFixtures;
import dev.gamecore.network.transport.ProtocolPipeline;
import dev.gamecore.server.network.Wire;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

/** 随机端口上的真TCP权威对局；无图形、无sleep，客户端只发送Protobuf意图。 */
@Timeout(30)
class AuthoritativeNetworkTest {
    private static GameCliClient connect(NettyGameServer server, String name) throws InterruptedException {
        return GameCliClient.connect("127.0.0.1", server.address().getPort(), name);
    }
    private static TankProtocol.WorldSnapshot world(GameCliClient client, Predicate<TankProtocol.WorldSnapshot> condition) throws InterruptedException {
        long end = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < end) {
            var value = client.latest(); if (value != null && condition.test(value)) { return value; }
            var message = client.receive(Duration.ofMillis(10));
            if (message != null && message.hasError()) { fail("Unexpected error: " + message.getError().getCode()); }
        }
        throw new AssertionError("World state timed out");
    }
    private static TankProtocol.Envelope message(GameCliClient client, Predicate<TankProtocol.Envelope> condition) throws InterruptedException {
        long end = System.nanoTime() + 8_000_000_000L;
        while (System.nanoTime() < end) {
            var value = client.receive(Duration.ofMillis(20)); if (value != null && condition.test(value)) { return value; }
        }
        throw new AssertionError("Message timed out");
    }
    private static TankProtocol.PlayerInput.Builder input(GameCliClient client, long sequence, long target) {
        return TankProtocol.PlayerInput.newBuilder().setSequence(sequence).setTargetTick(target).setMatchId(client.join().getMatchId());
    }
    @Test void twoPlayersJoinAndAuthorityMovesOnlyAssignedSlotWithCorrelatedAcknowledgement() throws Exception {
        try (var server = NettyGameServer.start(ServerSettings.defaults(0), RoomFixtures.open());
             var first = connect(server, "first"); var second = connect(server, "second")) {
            assertNotEquals(first.hello().getConnectionId(), second.hello().getConnectionId());
            assertNotEquals(first.join().getPlayerSlot(), second.join().getPlayerSlot()); assertEquals(30, first.hello().getServerTps()); assertEquals(60, first.hello().getSimulationTps());
            var before = world(first, value -> value.getState() == TankProtocol.RoomState.RUNNING);
            int slot = first.join().getPlayerSlot();
            first.sendInput(input(first, 1, before.getServerTick() + 3).setMove(TankProtocol.MoveDirection.FORWARD).build()).sync();
            var after = world(first, value -> value.getInputAcks(slot).getSequence() == 1);
            assertTrue(after.getPlayers(slot).getX() > before.getPlayers(slot).getX());
            assertEquals(before.getPlayers(1 - slot).getX(), after.getPlayers(1 - slot).getX());
            assertEquals(after.getServerTick() * 2, after.getSimulationTick()); assertTrue(server.metrics().steps() > 0);
        }
    }
    @Test void serverShotsCollisionsAndWinnerAreIdenticalForBothClients() throws Exception {
        try (var server = NettyGameServer.start(ServerSettings.defaults(0), RoomFixtures.open());
             var first = connect(server, "first"); var second = connect(server, "second")) {
            world(first, value -> value.getState() == TankProtocol.RoomState.RUNNING);
            var sender = Executors.newSingleThreadScheduledExecutor(); var sequence = new AtomicLong();
            var sending = sender.scheduleWithFixedDelay(() -> {
                var snapshot = first.latest();
                if (snapshot != null && snapshot.getState() == TankProtocol.RoomState.RUNNING) {
                    first.sendInput(input(first, sequence.incrementAndGet(), snapshot.getServerTick() + 3).setFire(true).build());
                }
            }, 0, 100, TimeUnit.MILLISECONDS);
            try {
                List<TankProtocol.GameEvent> events = new ArrayList<>();
                var result = message(first, value -> {
                    if (value.hasGameEvent()) { events.add(value.getGameEvent()); } return value.hasMatchResult();
                }).getMatchResult();
                var other = message(second, TankProtocol.Envelope::hasMatchResult).getMatchResult();
                assertEquals(TankProtocol.Outcome.PLAYER_ONE, result.getOutcome()); assertEquals(result, other);
                assertEquals(0, result.getFinalSnapshot().getPlayers(1).getHealth());
                assertTrue(events.stream().anyMatch(value -> value.getKind().equals("HIT")));
                assertEquals(events.size(), events.stream().map(TankProtocol.GameEvent::getEventId).distinct().count());
            } finally { sending.cancel(false); sender.shutdownNow(); assertTrue(sender.awaitTermination(5, TimeUnit.SECONDS)); }
        }
    }
    @Test void duplicateInputAndIllegalScalarsAreRejectedWithoutDamageOrAckAdvance() throws Exception {
        try (var server = NettyGameServer.start(ServerSettings.defaults(0), RoomFixtures.open()); var first = connect(server, "a"); var second = connect(server, "b")) {
            assertEquals(first.join().getMatchId(), second.join().getMatchId());
            var snapshot = world(first, value -> value.getState() == TankProtocol.RoomState.RUNNING);
            var command = input(first, 1, snapshot.getServerTick() + 3).build(); first.sendInput(command).sync(); first.sendInput(command).sync();
            assertEquals("DUPLICATE_SEQUENCE", message(first, TankProtocol.Envelope::hasError).getError().getCode());
            world(first, value -> value.getInputAcks(first.join().getPlayerSlot()).getSequence() == 1);
            List<TankProtocol.PlayerInput> bad = new ArrayList<>();
            for (double aim : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1, 360}) { bad.add(input(first, 2, 100).setAimDegrees(aim).setFire(true).build()); }
            bad.add(input(first, 2, 100).setMoveValue(999).build()); bad.add(input(first, 2, 100).setTurnValue(999).build());
            bad.add(input(first, -1, 100).build()); bad.add(input(first, 2, -1).build());
            var unknown = UnknownFieldSet.newBuilder().addField(99, UnknownFieldSet.Field.newBuilder().addFixed64(Double.doubleToLongBits(999)).build()).build();
            bad.add(input(first, 2, 100).setUnknownFields(unknown).build());
            for (var value : bad) {
                first.send(Wire.envelope(900, builder -> builder.setPlayerInput(value))).sync();
                var error = message(first, TankProtocol.Envelope::hasError); assertEquals(900, error.getRequestId()); assertEquals("INVALID_INPUT", error.getError().getCode());
            }
            assertEquals(1, first.latest().getInputAcks(first.join().getPlayerSlot()).getSequence());
            assertEquals(5, first.latest().getPlayers(1).getHealth()); assertEquals(0, first.latest().getBulletsCount());
        }
    }
    @Test void lateEarlyAndStaleMatchPoliciesAreVisibleOnWire() throws Exception {
        try (var server = NettyGameServer.start(ServerSettings.defaults(0)); var first = connect(server, "a"); var second = connect(server, "b")) {
            assertEquals(first.join().getMatchId(), second.join().getMatchId());
            var snapshot = world(first, value -> value.getState() == TankProtocol.RoomState.RUNNING);
            first.sendInput(input(first, 1, snapshot.getServerTick()).build()).sync(); assertEquals("LATE_INPUT", message(first, TankProtocol.Envelope::hasError).getError().getCode());
            first.sendInput(input(first, 2, Long.MAX_VALUE).build()).sync(); assertEquals("EARLY_INPUT", message(first, TankProtocol.Envelope::hasError).getError().getCode());
            first.sendInput(input(first, 3, snapshot.getServerTick() + 3).setMatchId("old").build()).sync(); assertEquals("STALE_MATCH", message(first, TankProtocol.Envelope::hasError).getError().getCode());
        }
    }
    @Test void clientCannotUploadAuthoritativeWorldAndThirdPlayerCannotJoin() throws Exception {
        try (var server = NettyGameServer.start(ServerSettings.defaults(0)); var first = connect(server, "a"); var second = connect(server, "b")) {
            var full = assertThrows(IllegalStateException.class, () -> { try (var third = connect(server, "third")) { fail("Unexpected third slot: " + third.join().getPlayerSlot()); } });
            assertEquals("ROOM_FULL_OR_CLOSED", full.getMessage());
            first.send(Wire.envelope(700, builder -> builder.setWorldSnapshot(TankProtocol.WorldSnapshot.newBuilder()
                    .addPlayers(TankProtocol.PlayerState.newBuilder().setHealth(9999).setX(9999))))).sync();
            assertEquals("UNEXPECTED_PAYLOAD", message(first, TankProtocol.Envelope::hasError).getError().getCode());
            var finalState = message(second, TankProtocol.Envelope::hasMatchResult).getMatchResult();
            assertEquals(TankProtocol.Outcome.CANCELLED, finalState.getOutcome()); assertEquals(5, finalState.getFinalSnapshot().getPlayers(0).getHealth());
        }
    }
    @Test void perConnectionRateLimitClosesOffenderAndCancelsMatchWithoutFakeWinner() throws Exception {
        var settings = new ServerSettings("127.0.0.1", 0, 30, "crossfire", 42, 1, 15);
        try (var server = NettyGameServer.start(settings); var first = connect(server, "a"); var second = connect(server, "b")) {
            var snapshot = world(first, value -> value.getState() == TankProtocol.RoomState.RUNNING);
            first.sendInput(input(first, 1, snapshot.getServerTick() + 3).build()).sync(); first.sendInput(input(first, 2, snapshot.getServerTick() + 4).build()).sync();
            assertEquals("RATE_LIMIT", message(first, TankProtocol.Envelope::hasError).getError().getCode());
            var result = message(second, TankProtocol.Envelope::hasMatchResult).getMatchResult();
            assertEquals(TankProtocol.Outcome.CANCELLED, result.getOutcome()); assertEquals("DISCONNECTED", result.getReason());
        }
    }
    @Test void resetDisconnectCleansMembershipAndQueuedIntents() throws Exception {
        try (var server = NettyGameServer.start(ServerSettings.defaults(0)); var observer = connect(server, "observer")) {
            try (var raw = new Raw(server)) {
                var hello = raw.hello("reset"); raw.send(Wire.envelope(2, builder -> builder.setJoinRoomRequest(TankProtocol.JoinRoomRequest.newBuilder().setRoomId("tank-1").setCredential(hello.getJoinCredential()))));
                raw.until(TankProtocol.Envelope::hasJoinRoomResponse); world(observer, value -> value.getState() == TankProtocol.RoomState.RUNNING);
                raw.socket.setSoLinger(true, 0);
            }
            var result = message(observer, TankProtocol.Envelope::hasMatchResult).getMatchResult(); assertEquals("DISCONNECTED", result.getReason());
            var state = world(observer, value -> !value.getPlayers(1).getConnected()); assertEquals(TankProtocol.RoomState.ABORTED, state.getState()); assertEquals(0, server.queuedInputs());
        }
    }
    @Test void joinCredentialIsBoundToOriginalConnectionAndBadFramesAreClosed() throws Exception {
        try (var server = NettyGameServer.start(ServerSettings.defaults(0)); var first = new Raw(server); var second = new Raw(server)) {
            var stolen = first.hello("first").getJoinCredential(); second.hello("second");
            second.send(Wire.envelope(2, builder -> builder.setJoinRoomRequest(TankProtocol.JoinRoomRequest.newBuilder().setRoomId("tank-1").setCredential(stolen))));
            assertEquals("JOIN_DENIED", second.until(TankProtocol.Envelope::hasError).getError().getCode());
        }
        try (var server = NettyGameServer.start(ServerSettings.defaults(0)); var raw = new Raw(server)) {
            raw.output.writeInt(ProtocolPipeline.MAX_BODY + 1); raw.output.flush(); assertEquals(-1, raw.input.read());
        }
    }
    @Test void idleTimeoutAndNormalShutdownTerminateOwnedResources() throws Exception {
        var server = NettyGameServer.start(new ServerSettings("127.0.0.1", 0, 30, "crossfire", 42, 60, 1));
        var client = connect(server, "idle");
        try { assertTrue(client.closeFuture().await(3, TimeUnit.SECONDS)); }
        finally { client.close(); server.close(); server.close(); }
        assertTrue(client.terminated()); assertTrue(server.isTerminated()); assertEquals(0, server.connectionCount()); assertEquals(0, server.queuedInputs());
    }
    @Test void unjoinedConnectionTimesOutEvenWithoutAnyPayload() throws Exception {
        try (var server = NettyGameServer.start(ServerSettings.defaults(0)); var raw = new Raw(server)) {
            assertEquals("JOIN_TIMEOUT", raw.until(TankProtocol.Envelope::hasError).getError().getCode());
        }
    }
    @Test void serverShutdownClosesActivePlayersClearsRoomAndTerminatesAllExecutors() throws Exception {
        var server = NettyGameServer.start(ServerSettings.defaults(0));
        try (var first = connect(server, "first"); var second = connect(server, "second")) {
            world(first, value -> value.getState() == TankProtocol.RoomState.RUNNING);
            server.close(); server.close();
            assertTrue(first.closeFuture().await(5, TimeUnit.SECONDS)); assertTrue(second.closeFuture().await(5, TimeUnit.SECONDS));
            assertTrue(server.isTerminated()); assertEquals(0, server.connectionCount()); assertEquals(0, server.queuedInputs());
            assertEquals(List.of(false, false), server.latest().connected());
        } finally { server.close(); }
    }
    @Test void heartbeatIsCorrelatedAndDoesNotEnterRoomInputOrAdvanceWaitingWorld() throws Exception {
        try (var server = NettyGameServer.start(ServerSettings.defaults(0)); var client = connect(server, "heartbeat")) {
            client.send(Wire.envelope(501, builder -> builder.setHeartbeat(TankProtocol.Heartbeat.getDefaultInstance()))).sync();
            var response = message(client, TankProtocol.Envelope::hasHeartbeat); assertEquals(501, response.getRequestId());
            assertTrue(client.hello().getHeartbeatSupported()); assertEquals(0, server.latest().serverTick()); assertEquals(0, server.queuedInputs());
        }
    }
    @Test void heartbeatFloodIsBoundedIndependentlyOfGameInputLimit() throws Exception {
        try (var server = NettyGameServer.start(ServerSettings.defaults(0)); var client = connect(server, "flood")) {
            for (int index = 0; index < 5; index++) { client.send(Wire.envelope(600 + index, builder -> builder.setHeartbeat(TankProtocol.Heartbeat.getDefaultInstance()))).sync(); }
            assertEquals("RATE_LIMIT", message(client, TankProtocol.Envelope::hasError).getError().getCode());
            assertTrue(client.closeFuture().await(3, TimeUnit.SECONDS)); assertEquals(0, server.queuedInputs());
        }
    }
    private static final class Raw implements AutoCloseable {
        final Socket socket = new Socket(); final DataInputStream input; final DataOutputStream output;
        Raw(NettyGameServer server) throws Exception {
            socket.connect(server.address(), 3000); socket.setSoTimeout(6000); socket.setTcpNoDelay(true);
            input = new DataInputStream(socket.getInputStream()); output = new DataOutputStream(socket.getOutputStream());
        }
        void send(TankProtocol.Envelope value) throws Exception { byte[] bytes = value.toByteArray(); output.writeInt(bytes.length); output.write(bytes); output.flush(); }
        TankProtocol.Envelope until(Predicate<TankProtocol.Envelope> condition) throws Exception {
            for (int index = 0; index < 256; index++) {
                int length = input.readInt(); assertTrue(length <= ProtocolPipeline.MAX_BODY && length >= 0);
                byte[] bytes = input.readNBytes(length); if (bytes.length != length) { throw new EOFException(); }
                var value = TankProtocol.Envelope.parseFrom(bytes); if (condition.test(value)) { return value; }
            }
            throw new AssertionError("No expected message");
        }
        TankProtocol.ServerHello hello(String name) throws Exception {
            send(Wire.envelope(1, builder -> builder.setClientHello(TankProtocol.ClientHello.newBuilder().setClientName(name))));
            return until(TankProtocol.Envelope::hasServerHello).getServerHello();
        }
        @Override public void close() throws java.io.IOException { socket.close(); }
    }
}
