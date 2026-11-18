package dev.gamecore.client.network;

import dev.gamecore.client.network.sync.ClientPrediction;
import dev.gamecore.client.network.sync.InputHistory;
import dev.gamecore.client.network.sync.SnapshotInterpolator;
import dev.gamecore.protocol.TankProtocol;
import dev.gamecore.server.ClientTestServer;
import dev.gamecore.server.core.GameRoom;
import dev.gamecore.server.core.InputIntent;
import dev.gamecore.server.core.RoomFrame;
import dev.gamecore.server.core.RoomPeer;
import dev.gamecore.server.network.Wire;
import dev.gamecore.tank.TankCommand;
import dev.gamecore.tank.TankGame;
import dev.gamecore.tank.TankSnapshot;
import dev.gamecore.tank.Vec2;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 固定虚拟时间的预测/校正测试，实际权威来源是已有GameRoom/TankSession。 */
class PredictionModelTest {
    static final class Peer implements RoomPeer {
        final AtomicInteger queued = new AtomicInteger(); final List<String> errors = new ArrayList<>();
        String match; int slot; boolean disconnected;
        @Override public String identity() { return "virtual-player"; }
        @Override public boolean disconnected() { return disconnected; }
        @Override public AtomicInteger queuedInputs() { return queued; }
        @Override public void joined(long request, int assigned, String id) { slot = assigned; match = id; }
        @Override public void reject(long request, String code) { errors.add(code); }
        @Override public void offerFrame(RoomFrame frame) { }
        @Override public void result(RoomFrame frame) { }
    }
    static GameRoom room(Peer a, Peer b) {
        var room = new GameRoom(ClientTestServer.openMap(), 30, 42); room.requestJoin(a, 1); room.requestJoin(b, 2); room.step(); return room;
    }
    static TankProtocol.PlayerInput input(String match, long sequence, long target, int move, int turn) {
        return TankProtocol.PlayerInput.newBuilder().setMatchId(match).setSequence(sequence).setTargetTick(target)
                .setMove(move > 0 ? TankProtocol.MoveDirection.FORWARD : move < 0 ? TankProtocol.MoveDirection.BACKWARD : TankProtocol.MoveDirection.MOVE_IDLE)
                .setTurn(turn > 0 ? TankProtocol.TurnDirection.LEFT : turn < 0 ? TankProtocol.TurnDirection.RIGHT : TankProtocol.TurnDirection.TURN_IDLE).build();
    }
    static InputIntent intent(TankProtocol.PlayerInput value) {
        return new InputIntent(value.getSequence(), value.getMatchId(), value.getSequence(), value.getTargetTick(),
                value.getMove() == TankProtocol.MoveDirection.FORWARD ? 1 : value.getMove() == TankProtocol.MoveDirection.BACKWARD ? -1 : 0,
                value.getTurn() == TankProtocol.TurnDirection.LEFT ? 1 : value.getTurn() == TankProtocol.TurnDirection.RIGHT ? -1 : 0, null, false);
    }
    @Test void immediateLocalMotionThenAuthorityReplaysOnlyFutureInputsWithoutChangingHealth() {
        var a = new Peer(); var room = room(a, new Peer()); var engine = new ClientPrediction();
        var first = Wire.snapshot(room.latest()); engine.reconcile(room.latest().world(), first, 0, 0, 0);
        var input = input(a.match, 1, 3, 1, 0); engine.input(input);
        var shown = engine.display(0); assertTrue(shown.position().x() > 2.5); assertEquals(5, shown.health());
        assertEquals(2.5, room.latest().world().tanks().getFirst().position().x());
        room.inputQueue().offer(a, intent(input)); room.step();
        assertEquals(0, room.latest().acknowledgements().getFirst());
        engine.reconcile(room.latest().world(), Wire.snapshot(room.latest()), 0, 33_333_333L, 33_333_333L);
        assertEquals(1, engine.metrics().pendingInputs());
        room.step(); engine.reconcile(room.latest().world(), Wire.snapshot(room.latest()), 0, 66_666_666L, 66_666_666L);
        assertEquals(0, engine.metrics().pendingInputs()); assertEquals(1, room.latest().acknowledgements().getFirst());
        assertTrue(room.latest().world().bullets().isEmpty());
    }
    @Test void supersededSameTickInputsDoNotMultiplyTimeAndDuplicateSequenceIsNotReplayedTwice() {
        var a = new Peer(); var room = room(a, new Peer());
        var first = new ClientPrediction(); var second = new ClientPrediction();
        for (var engine : List.of(first, second)) { engine.reconcile(room.latest().world(), Wire.snapshot(room.latest()), 0, 0, 0); }
        first.input(input(a.match, 1, 3, -1, 0)); first.input(input(a.match, 2, 3, 1, 0)); first.input(input(a.match, 2, 3, 1, 0));
        second.input(input(a.match, 2, 3, 1, 0));
        assertEquals(first.display(0).position(), second.display(0).position()); assertEquals(2, first.metrics().pendingInputs());
        var history = new InputHistory(); history.add(input(a.match, 1, 3, 1, 0)); history.acknowledge(1, 3);
        assertFalse(history.add(input(a.match, 1, 3, 1, 0))); assertEquals(0, history.size());
    }
    @Test void droppedDirectionChangeIsCorrectedAndStoppedClientEventuallyConverges() {
        var a = new Peer(); var room = room(a, new Peer()); var engine = new ClientPrediction();
        engine.reconcile(room.latest().world(), Wire.snapshot(room.latest()), 0, 0, 0);
        engine.input(input(a.match, 1, 3, 1, 1)); engine.display(0); // 模拟发送后在应用层丢弃。
        for (int index = 1; index <= 40; index++) {
            room.step(); long now = index * 33_333_334L;
            engine.display(now); engine.reconcile(room.latest().world(), Wire.snapshot(room.latest()), 0, now, now);
        }
        assertTrue(engine.metrics().corrections() > 0); assertTrue(engine.metrics().maxError() > 0);
        assertEquals(0, engine.metrics().pendingInputs()); assertTrue(engine.metrics().expiredInputs() > 0);
        engine.enabled(false); assertEquals(room.latest().world().tanks().getFirst(), engine.display(2_000_000_000L));
        engine.enabled(true);
        assertEquals(room.latest().world().tanks().getFirst().position(), engine.display(3_000_000_000L).position());
    }
    @Test void historyOverflowFailsClosedAndReplayAlwaysHasHardBound() {
        var a = new Peer(); var room = room(a, new Peer()); var engine = new ClientPrediction();
        engine.reconcile(room.latest().world(), Wire.snapshot(room.latest()), 0, 0, 0);
        for (int index = 1; index <= 129; index++) { engine.input(input(a.match, index, 1_000_000L + index, 1, 0)); }
        assertEquals(1, engine.metrics().overflows()); assertEquals(0, engine.metrics().pendingInputs());
        assertEquals(room.latest().world().tanks().getFirst(), engine.display(0));
        engine.reconcile(room.latest().world(), Wire.snapshot(room.latest()), 0, 1, 1);
        engine.input(input(a.match, 130, 1_000_131, 1, 0)); engine.display(1);
        assertTrue(engine.metrics().replayTicks() <= ClientPrediction.MAX_REPLAY_TICKS);
        engine.clearPending(); assertEquals(0, engine.metrics().pendingInputs());
    }
    @Test void movingOpponentContactsUseSharedKernelAndUnknownRemoteChangeIsReconciled() {
        var a = new Peer(); var b = new Peer(); var room = room(a, b); var engine = new ClientPrediction();
        engine.reconcile(room.latest().world(), Wire.snapshot(room.latest()), 0, 0, 0);
        for (int tick = 1; tick <= 80; tick++) {
            long now = tick * 33_333_334L;
            var own = input(a.match, tick, room.latest().serverTick() + 1, 1, 0); engine.input(own);
            engine.display(now); room.inputQueue().offer(a, intent(own));
            room.inputQueue().offer(b, intent(input(b.match, tick, room.latest().serverTick() + 1, tick < 35 ? 1 : -1, 0)));
            room.step(); engine.reconcile(room.latest().world(), Wire.snapshot(room.latest()), 0, now, now);
            var tanks = room.latest().world().tanks();
            assertTrue(tanks.get(0).position().x() < tanks.get(1).position().x());
            assertFalse(room.latest().world().map().blocked(engine.display(now).position(), TankGame.TANK_RADIUS));
        }
        assertTrue(engine.metrics().corrections() > 0); assertEquals(5, room.latest().world().tanks().getFirst().health());
    }
    @Test void interpolationSortsTicksUsesShortestHeadingAndHoldsWhenSnapshotsMissing() {
        var a = new Peer(); var room = room(a, new Peer()); var original = room.latest().world(); var timeline = new SnapshotInterpolator();
        var first = remotePose(original, 3, 350); var second = remotePose(original, 5, 10);
        timeline.bufferMillis(50); timeline.offer(10, 20, first, 0); timeline.offer(12, 20, second, 100_000_000L);
        var middle = timeline.display(second, 0, null, 100_000_000L);
        assertEquals(4, middle.tanks().get(1).position().x(), 1e-9); assertEquals(0, middle.tanks().get(1).heading(), 1e-9);
        assertEquals(original.tanks().getFirst(), middle.tanks().getFirst());
        assertEquals(5, timeline.display(second, 0, null, 5_000_000_000L).tanks().get(1).position().x());
        assertEquals(3, first.tanks().get(1).position().x()); assertEquals(5, second.tanks().get(1).position().x());
        timeline.offer(11, 20, first, 101_000_000L); assertEquals(1, timeline.outOfOrder());
        for (int tick = 13; tick < 200; tick++) { timeline.offer(tick, 20, second, tick * 50_000_000L); }
        assertEquals(SnapshotInterpolator.CAPACITY, timeline.size()); timeline.enabled(false);
        assertEquals(second, timeline.display(second, 0, null, 10_000_000_000L));
    }
    private static TankSnapshot remotePose(TankSnapshot original, double x, double heading) {
        var tanks = new ArrayList<>(original.tanks()); var old = tanks.get(1);
        tanks.set(1, new TankSnapshot.Tank(1, new Vec2(x, old.position().y()), heading, old.velocity(), old.health(), old.cooldown()));
        return new TankSnapshot(original.map(), original.tick(), TankGame.State.RUNNING, original.result(), tanks, original.bullets(), original.events());
    }
}
