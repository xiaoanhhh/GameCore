package dev.gamecore.server.core;

import dev.gamecore.server.network.Wire;
import org.junit.jupiter.api.Test;
import static dev.gamecore.server.core.RoomFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** 合法收到、未来排队和真正执行必须可区分；ack不能提前授权客户端丢弃输入。 */
class InputAcknowledgementTest {
    @Test void futureInputIsReceivedButNotAcknowledgedUntilItsActualSimulationTick() {
        var a = new Peer(); var room = started(30, a, new Peer());
        long target = room.latest().serverTick() + 4;
        room.inputQueue().offer(a, new InputIntent(7, a.match, 7, target, 1, 0, null, false)); room.step();
        var early = Wire.snapshot(room.latest()).getInputAcks(0);
        assertEquals(7, early.getReceivedSequence()); assertEquals(0, early.getSequence()); assertFalse(early.hasActiveInput());
        while (room.latest().serverTick() < target) { room.step(); }
        var applied = Wire.snapshot(room.latest()).getInputAcks(0);
        assertEquals(7, applied.getSequence()); assertEquals(target, applied.getAppliedTick()); assertEquals(target + 15, applied.getExpiresTick());
        assertEquals(7, applied.getActiveInput().getSequence()); assertTrue(room.latest().world().tanks().getFirst().position().x() > 2.5);
        while (room.latest().serverTick() < target + 15) { room.step(); }
        var expired = Wire.snapshot(room.latest()).getInputAcks(0);
        assertEquals(7, expired.getSequence()); assertFalse(expired.hasActiveInput()); assertEquals(0, room.latest().world().tanks().getFirst().velocity().x());
        assertTrue(early.getSequence() == 0 && !early.hasActiveInput());
    }
    @Test void sameTickReplacementExecutesOnlyLatestSequenceAndDuplicatesDoNotRepeatShot() {
        var a = new Peer(); var room = started(30, a, new Peer()); long target = room.latest().serverTick() + 2;
        room.inputQueue().offer(a, new InputIntent(1, a.match, 1, target, 1, 0, null, false));
        var replacement = new InputIntent(2, a.match, 2, target, 0, 0, null, true);
        room.inputQueue().offer(a, replacement); room.inputQueue().offer(a, replacement); room.step(); room.step();
        assertEquals(2, room.latest().acknowledgements().getFirst()); assertEquals(2.5, room.latest().world().tanks().getFirst().position().x());
        assertEquals(1, a.events.stream().filter(event -> event.value().type().name().equals("SHOT")).count());
        assertTrue(a.errors.contains("DUPLICATE_SEQUENCE"));
    }
    @Test void rejectionCorrelatesInputAndNeverRaisesExecutionAcknowledgement() {
        var a = new Peer(); var room = started(30, a, new Peer());
        var input = new InputIntent(11, a.match, 20, room.latest().serverTick(), 1, 0, null, false);
        room.inputQueue().offer(a, input); room.step();
        assertEquals(0, room.latest().inputStatus().getFirst().received()); assertEquals(0, room.latest().acknowledgements().getFirst());
        var error = Wire.inputError(input, "LATE_INPUT");
        assertEquals(11, error.getRequestId()); assertEquals(20, error.getError().getInputSequence()); assertEquals(input.targetTick(), error.getError().getTargetTick());
    }
}
