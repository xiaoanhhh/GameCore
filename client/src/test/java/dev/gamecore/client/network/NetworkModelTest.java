package dev.gamecore.client.network;

import dev.gamecore.network.transport.ProtocolMessages;
import dev.gamecore.protocol.TankProtocol;
import dev.gamecore.server.core.MapResources;
import dev.gamecore.server.core.RoomFrame;
import dev.gamecore.server.network.Wire;
import dev.gamecore.tank.TankCommand;
import dev.gamecore.tank.TankGame;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 非图形客户端边界：邮箱容量、包顺序、快照标量与输入采样，绝不启动Stage或本地规则来预测。 */
class NetworkModelTest {
    static TankProtocol.WorldSnapshot world() throws Exception {
        var game = new TankGame(MapResources.load("crossfire"));
        return Wire.snapshot(new RoomFrame("match", 0, 30, RoomFrame.State.WAITING, game.snapshot(),
                List.of(0L, 0L), List.of(true, false), List.of(), MapResources.digest(game.snapshot().map()), ""));
    }
    @Test void mailboxCoalescesSnapshotsButPreservesJoinResultAndLastResultBeforeDisconnect() throws Exception {
        var dispatcher = new NetworkEventDispatcher();
        var join = ProtocolMessages.envelope(2, builder -> builder.setJoinRoomResponse(TankProtocol.JoinRoomResponse.newBuilder().setMatchId("match")));
        assertTrue(dispatcher.message(join));
        for (int index = 0; index < 1000; index++) {
            long tick = index;
            assertTrue(dispatcher.message(ProtocolMessages.envelope(0, builder -> builder.setWorldSnapshot(TankProtocol.WorldSnapshot.newBuilder().setServerTick(tick)))));
        }
        var result = ProtocolMessages.envelope(0, builder -> builder.setMatchResult(TankProtocol.MatchResult.newBuilder().setOutcome(TankProtocol.Outcome.DRAW)));
        assertTrue(dispatcher.message(result)); dispatcher.fail(ConnectionState.DISCONNECTED, "closed");
        var events = dispatcher.drain(); assertEquals(4, events.size());
        assertEquals(join, ((NetworkEventDispatcher.Message) events.getFirst()).value());
        assertEquals(999, ((NetworkEventDispatcher.Message) events.get(1)).value().getWorldSnapshot().getServerTick());
        assertEquals(result, ((NetworkEventDispatcher.Message) events.get(2)).value()); assertInstanceOf(NetworkEventDispatcher.Notice.class, events.getLast());
        assertEquals(0, dispatcher.size()); dispatcher.close(); assertFalse(dispatcher.message(join)); assertTrue(dispatcher.drain().isEmpty());
    }
    @Test void reliableMailboxAndFailureSlotHaveFixedBounds() {
        var dispatcher = new NetworkEventDispatcher(); var value = ProtocolMessages.envelope(0, builder -> builder.setGameEvent(TankProtocol.GameEvent.getDefaultInstance()));
        for (int index = 0; index < 128; index++) { assertTrue(dispatcher.message(value)); }
        assertFalse(dispatcher.message(value)); assertEquals(128, dispatcher.size()); dispatcher.fail(ConnectionState.FAILED, "overflow");
        assertEquals(129, dispatcher.drain().size()); assertEquals(0, dispatcher.size());
    }
    @Test void endpointAcceptsIpHostnameAndIpv6ButRejectsUrlsCredentialsAndInvalidPorts() {
        for (String host : List.of("127.0.0.1", "localhost", "game.example.com", "[::1]")) { assertDoesNotThrow(() -> new ServerEndpoint(host, 7100)); }
        for (String host : List.of("", "https://localhost", "host/path", "user@host", "bad..host", "999.1.1.1", "-host", "host-")) {
            assertThrows(IllegalArgumentException.class, () -> new ServerEndpoint(host, 7100));
        }
        assertThrows(IllegalArgumentException.class, () -> new ServerEndpoint("localhost", 0));
        assertThrows(IllegalArgumentException.class, () -> new ServerEndpoint("localhost", 65536));
    }
    @Test void stableSnapshotChecksMapAndRejectsNonFiniteOutOfRangeOrDuplicateEntities() throws Exception {
        var value = world(); var receiver = new SnapshotReceiver(); receiver.configure("match", 30);
        var old = receiver.receive(value, TankGame.Result.NONE);
        assertEquals(2.5, old.tanks().getFirst().position().x());
        assertThrows(UnsupportedOperationException.class, () -> old.tanks().clear());
        for (double x : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 1000}) {
            var invalid = value.toBuilder().setPlayers(0, value.getPlayers(0).toBuilder().setX(x)).build();
            assertThrows(IllegalArgumentException.class, () -> receiver.receive(invalid, TankGame.Result.NONE));
        }
        assertThrows(IllegalArgumentException.class, () -> receiver.receive(value.toBuilder().setPlayers(1, value.getPlayers(1).toBuilder().setPlayerSlot(0)).build(), TankGame.Result.NONE));
        assertThrows(IllegalArgumentException.class, () -> receiver.receive(value.toBuilder().setPlayers(0, value.getPlayers(0).toBuilder().setHealth(999)).build(), TankGame.Result.NONE));
        var bullet = TankProtocol.BulletState.newBuilder().setId(1).setX(3).setY(3).setRemainingTicks(20).build();
        assertThrows(IllegalArgumentException.class, () -> receiver.receive(value.toBuilder().addBullets(bullet).addBullets(bullet).build(), TankGame.Result.NONE));
        var mismatch = new SnapshotReceiver(); mismatch.configure("match", 30);
        assertThrows(IllegalArgumentException.class, () -> mismatch.receive(value.toBuilder().setMapDigest("wrong").build(), TankGame.Result.NONE));
        assertEquals(2.5, old.tanks().getFirst().position().x());
    }
    @Test void disconnectedSlotCanClearItsAcknowledgementWithoutRejectingAuthoritativeCancellation() throws Exception {
        var first = world().toBuilder().setState(TankProtocol.RoomState.RUNNING).setServerTick(2).setSimulationTick(4)
                .setInputAcks(0, TankProtocol.InputAck.newBuilder().setPlayerSlot(0).setSequence(2)).build();
        var receiver = new SnapshotReceiver(); receiver.configure("match", 30); receiver.receive(first, TankGame.Result.NONE);
        var cancelled = first.toBuilder().setState(TankProtocol.RoomState.ABORTED)
                .setPlayers(0, first.getPlayers(0).toBuilder().setConnected(false))
                .setInputAcks(0, TankProtocol.InputAck.newBuilder().setPlayerSlot(0)).build();
        assertEquals(TankGame.State.PAUSED, receiver.receive(cancelled, TankGame.Result.NONE).state());
        assertEquals(0, receiver.acknowledgement(0));
    }
    @Test void staleMatchTicksUnknownStatesAndRegressingAcknowledgementsAreRejected() throws Exception {
        var value = world().toBuilder().setState(TankProtocol.RoomState.RUNNING).setServerTick(2).setSimulationTick(4)
                .setInputAcks(0, TankProtocol.InputAck.newBuilder().setSequence(2)).build();
        var receiver = new SnapshotReceiver(); receiver.configure("match", 30); receiver.receive(value, TankGame.Result.NONE);
        for (var invalid : List.of(value.toBuilder().setMatchId("old").build(), value.toBuilder().setServerTick(1).build(),
                value.toBuilder().setStateValue(999).build(), value.toBuilder().setInputAcks(0, TankProtocol.InputAck.getDefaultInstance()).build(),
                value.toBuilder().setSimulationTick(Long.MAX_VALUE).build())) {
            assertThrows(IllegalArgumentException.class, () -> receiver.receive(invalid, TankGame.Result.NONE));
        }
    }
    @Test void predictiveInputStatusRejectsForgedAcknowledgementLeaseAndNonFiniteAim() throws Exception {
        var value = world().toBuilder().setState(TankProtocol.RoomState.RUNNING).setServerTick(2).setSimulationTick(4);
        var active = TankProtocol.PlayerInput.newBuilder().setSequence(1).setTargetTick(2).setMatchId("match").setMove(TankProtocol.MoveDirection.FORWARD);
        var ack = TankProtocol.InputAck.newBuilder().setSequence(1).setReceivedSequence(2).setAppliedTick(2).setExpiresTick(17).setActiveInput(active);
        value.setInputAcks(0, ack);
        var receiver = new SnapshotReceiver(); receiver.configure("match", 30, true); receiver.receive(value.build(), TankGame.Result.NONE);
        for (var invalid : List.of(ack.clone().setReceivedSequence(0), ack.clone().setExpiresTick(18),
                ack.clone().setActiveInput(active.clone().setAimDegrees(Double.NaN)), ack.clone().setActiveInput(active.clone().setMoveValue(999)))) {
            assertThrows(IllegalArgumentException.class, () -> receiver.receive(value.clone().setInputAcks(0, invalid).build(), TankGame.Result.NONE));
        }
    }
    @Test void inputSamplerLimitsRateHasNoCatchupBurstAndReleasesOnlyOwnIntent() {
        var sender = new ClientInputSender();
        var first = sender.sample("m", 10, 30, new TankCommand(1, -1, true), 0, false);
        assertEquals(1, first.getSequence()); assertEquals(13, first.getTargetTick()); assertEquals(TankProtocol.MoveDirection.FORWARD, first.getMove()); assertTrue(first.getFire());
        for (long now = 1; now < 20_000_000; now += 1_000_000) { assertNull(sender.sample("m", 10, 30, TankCommand.IDLE, now, false)); }
        var released = sender.sample("m", 10, 30, TankCommand.IDLE, 50_000_000, true);
        assertFalse(released.getFire()); assertEquals(first.getTargetTick(), released.getTargetTick()); assertEquals(2, released.getSequence());
        var late = sender.sample("m", 100, 30, TankCommand.IDLE, 10_000_000_000L, false); assertEquals(3, late.getSequence());
        assertNull(sender.sample("m", 100, 30, TankCommand.IDLE, 10_000_000_001L, false));
    }
    @Test void remoteSessionRejectsUpdatesFromAnotherThreadAndCloseDropsLateEvents() throws Exception {
        var session = new RemoteGameSession();
        try {
            var action = new FutureTask<>(() -> { session.update(TankCommand.IDLE, 0); return null; });
            var thread = new Thread(action); thread.start();
            var error = assertThrows(java.util.concurrent.ExecutionException.class, () -> action.get(5, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, error.getCause()); thread.join(5000);
        } finally { session.close(); session.network().termination().toCompletableFuture().get(5, TimeUnit.SECONDS); }
        session.update(TankCommand.IDLE, 1); assertEquals(ConnectionState.CLOSED, session.state()); assertTrue(session.network().isTerminated());
    }
}
