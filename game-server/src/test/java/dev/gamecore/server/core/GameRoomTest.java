package dev.gamecore.server.core;

import dev.gamecore.tank.TankGame;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static dev.gamecore.server.core.RoomFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** 服务器权威与原Tank规则的纯Java对照；测试直接手动step，不依赖墙钟或图形。 */
class GameRoomTest {
    @Test void twoPlayersGetDifferentSlotsAndThirdIsRejected() {
        var a = new Peer(); var b = new Peer(); var room = started(30, a, b); var third = new Peer();
        assertEquals(0, a.slot); assertEquals(1, b.slot); assertEquals(a.match, b.match);
        room.requestJoin(third, 3); room.step(); assertEquals(List.of("ROOM_FULL_OR_CLOSED"), third.errors); assertEquals(-1, third.slot);
    }
    @Test void thirtyTpsPreservesOneSecondMovementAndOldSnapshots() {
        var a = new Peer(); var b = new Peer(); var room = started(30, a, b); var old = room.latest();
        for (int tick = 1; tick <= 30; tick++) { assertTrue(room.inputQueue().offer(a, intent(room, tick, 1, 0, false))); room.step(); }
        assertEquals(5.7, room.latest().world().tanks().getFirst().position().x(), 1e-10);
        assertEquals(62, room.latest().world().tick()); assertEquals(30, room.latest().acknowledgements().getFirst());
        assertEquals(2.5, old.world().tanks().getFirst().position().x()); assertEquals(1, old.serverTick());
        assertThrows(UnsupportedOperationException.class, () -> room.latest().connected().clear());
    }
    @Test void actualRulesDetermineShotsDamageAndConsistentWinnerOnlyOnce() {
        var a = new Peer(); var b = new Peer(); var room = started(30, a, b);
        for (int tick = 1; tick < 500 && room.latest().state() == RoomFrame.State.RUNNING; tick++) {
            room.inputQueue().offer(a, intent(room, tick, 0, 0, true)); room.step();
        }
        assertEquals(TankGame.Result.PLAYER_ONE, room.latest().world().result()); assertEquals(0, room.latest().world().tanks().get(1).health());
        assertEquals(a.result, b.result); assertEquals(1, a.resultCount);
        assertTrue(a.events.stream().anyMatch(event -> event.value().type().name().equals("HIT")));
        var before = room.latest().world(); room.step(); assertEquals(before, room.latest().world()); assertEquals(1, b.resultCount);
    }
    @Test void simultaneousDestructionAndTimeoutUseExistingDrawRules() {
        var a = new Peer(); var b = new Peer(); var room = started(30, a, b);
        for (int tick = 1; tick < 500 && room.latest().state() == RoomFrame.State.RUNNING; tick++) {
            room.inputQueue().offer(a, intent(room, tick, 0, 0, true)); room.inputQueue().offer(b, intent(room, tick, 0, 0, true)); room.step();
        }
        assertEquals(TankGame.Result.DRAW, room.latest().world().result());
        var timeout = started(30, new Peer(), new Peer());
        while (timeout.latest().state() == RoomFrame.State.RUNNING) { timeout.step(); }
        assertEquals(TankGame.MATCH_TICKS, timeout.latest().world().tick()); assertEquals(5400, timeout.latest().serverTick());
        assertEquals(TankGame.Result.DRAW, timeout.latest().world().result());
    }
    @Test void wallCollisionAndBulletAbsorptionComeFromTankGame() {
        var a = new Peer(); var b = new Peer(); var room = new GameRoom(map(true), 30, 42);
        room.requestJoin(a, 1); room.requestJoin(b, 2); room.step();
        for (int tick = 1; tick <= 100; tick++) { room.inputQueue().offer(a, intent(room, tick, 1, 0, true)); room.step(); }
        assertTrue(room.latest().world().tanks().getFirst().position().x() <= 6 - TankGame.TANK_RADIUS);
        assertEquals(5, room.latest().world().tanks().get(1).health());
        assertTrue(a.events.stream().anyMatch(event -> event.value().type().name().equals("WALL_HIT")));
    }
    @Test void duplicatesLateFutureAndTargetReorderingAreRejected() {
        var a = new Peer(); var room = started(30, a, new Peer());
        var first = intent(room, 1, 0, 0, true);
        room.inputQueue().offer(a, first); room.inputQueue().offer(a, first); room.step();
        assertTrue(a.errors.contains("DUPLICATE_SEQUENCE")); assertEquals(1, a.events.stream().filter(event -> event.value().type().name().equals("SHOT")).count());
        long now = room.latest().serverTick();
        room.inputQueue().offer(a, new InputIntent(2, a.match, 2, now, 0, 0, null, false));
        room.inputQueue().offer(a, new InputIntent(3, a.match, 3, now + 16, 0, 0, null, false));
        room.inputQueue().offer(a, new InputIntent(4, a.match, 4, now + 5, 0, 0, null, false));
        room.inputQueue().offer(a, new InputIntent(5, a.match, 5, now + 3, 0, 0, null, false)); room.step();
        assertTrue(a.errors.containsAll(List.of("LATE_INPUT", "EARLY_INPUT", "TARGET_ORDER")));
    }
    @Test void missingInputExpiresAndAimOnlyTurnsAtRuleSpeed() {
        var a = new Peer(); var room = started(30, a, new Peer());
        room.inputQueue().offer(a, new InputIntent(1, a.match, 1, 2, 0, 0, 90.0, false)); room.step();
        assertEquals(5, room.latest().world().tanks().getFirst().heading());
        for (int tick = 0; tick < 20; tick++) { room.step(); }
        assertEquals(0, room.latest().world().tanks().getFirst().velocity().x());
        double heading = room.latest().world().tanks().getFirst().heading(); room.step(); assertEquals(heading, room.latest().world().tanks().getFirst().heading());
    }
    @Test void invalidScalarsAndStaleMatchCannotMutateWorld() {
        var a = new Peer(); var room = started(30, a, new Peer());
        for (Double aim : List.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0, 360.0)) {
            room.inputQueue().offer(a, new InputIntent(1, a.match, 1, 2, 0, 0, aim, true));
        }
        room.inputQueue().offer(a, new InputIntent(2, a.match, 2, 2, 2, 0, null, true));
        room.inputQueue().offer(a, new InputIntent(3, "old", 3, 2, 0, 0, null, true)); room.step();
        assertTrue(a.errors.contains("STALE_MATCH")); assertEquals(6, a.errors.stream().filter("INVALID_INPUT"::equals).count());
        assertTrue(room.latest().world().bullets().isEmpty()); assertEquals(0L, room.latest().acknowledgements().getFirst());
    }
    @Test void disconnectAbortsWithoutForgedWinnerAndEmptyRoomCanStartFresh() {
        var a = new Peer(); var b = new Peer(); var room = started(30, a, b);
        room.inputQueue().offer(a, intent(room, 1, 1, 0, true)); a.disconnected = true; room.step();
        assertEquals(RoomFrame.State.ABORTED, room.latest().state()); assertEquals("DISCONNECTED", b.result.reason());
        assertEquals(TankGame.Result.NONE, b.result.world().result()); assertEquals(List.of(false, true), room.latest().connected());
        assertEquals(0, a.pending.get()); String old = b.match; b.disconnected = true; room.step();
        var c = new Peer(); var d = new Peer(); room.requestJoin(c, 3); room.requestJoin(d, 4); room.step();
        assertNotEquals(old, c.match); assertEquals(RoomFrame.State.RUNNING, room.latest().state()); assertEquals(1, room.latest().serverTick());
    }
    @Test void identicalInputsAndThirtySixtyTpsAlignedSubstepsHaveSameCoreWorld() {
        var a = new Peer(); var b = new Peer(); var first = started(30, a, b);
        var c = new Peer(); var d = new Peer(); var second = started(60, c, d); second.step();
        for (int tick = 1; tick <= 60; tick++) {
            int pair = (tick + 1) / 2; int turn = pair < 15 ? 1 : -1; boolean fire = pair % 5 < 2;
            if (tick % 2 == 1) { first.inputQueue().offer(b, intent(first, pair, 0, -turn, fire)); first.inputQueue().offer(a, intent(first, pair, 1, turn, fire)); first.step(); }
            second.inputQueue().offer(c, intent(second, tick, 1, turn, fire)); second.inputQueue().offer(d, intent(second, tick, 0, -turn, fire)); second.step();
        }
        assertEquals(first.latest().world(), second.latest().world());
    }
    @Test void roomRejectsWritesFromAnotherThread() throws Exception {
        var room = started(30, new Peer(), new Peer()); var action = new FutureTask<>(() -> { room.step(); return null; });
        var thread = new Thread(action); thread.start();
        var error = assertThrows(java.util.concurrent.ExecutionException.class, () -> action.get(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, error.getCause()); thread.join(1000);
    }
    @Test void overloadOfEmptyWaitingRoomDoesNotPermanentlyRejectNewPlayers() {
        var room = new GameRoom(open(), 30, 42); room.step(); room.abort("TICK_OVERLOAD");
        assertEquals(RoomFrame.State.ABORTED, room.latest().state());
        var a = new Peer(); var b = new Peer(); room.requestJoin(a, 1); room.requestJoin(b, 2); room.step();
        assertEquals(RoomFrame.State.RUNNING, room.latest().state()); assertEquals(0, a.slot); assertEquals(1, b.slot);
        assertTrue(a.errors.isEmpty()); assertEquals(a.match, b.match);
    }
}
