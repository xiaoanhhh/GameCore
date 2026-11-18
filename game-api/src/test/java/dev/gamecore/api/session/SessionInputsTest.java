package dev.gamecore.api.session;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 输入邮箱验证独立于具体游戏；覆盖显式顺序、边界和容量，而非依赖集合碰巧稳定。 */
class SessionInputsTest {
    @Test void sortsBySequenceRegardlessOfArrivalAndRejectsDuplicateAcrossPlayers() {
        var queue = new SessionInputs<String>();
        assertTrue(queue.offer(new PlayerInput<>(1, 1, 8, "late"), 1));
        assertTrue(queue.offer(new PlayerInput<>(1, 0, 2, "early"), 1));
        assertFalse(queue.offer(new PlayerInput<>(1, 0, 8, "duplicate"), 1));
        assertEquals(java.util.List.of("early", "late"), queue.drain().stream().map(PlayerInput::value).toList());
        assertTrue(queue.drain().isEmpty());
    }
    @Test void wrongTickIsRejectedAndPauseClearAllowsReusingSequence() {
        var queue = new SessionInputs<String>();
        assertFalse(queue.offer(new PlayerInput<>(1, 0, 0, "old"), 2));
        assertFalse(queue.offer(new PlayerInput<>(3, 0, 0, "future"), 2));
        assertTrue(queue.offer(new PlayerInput<>(2, 0, 0, "discard"), 2));
        queue.clear(); assertTrue(queue.offer(new PlayerInput<>(2, 0, 0, "replacement"), 2));
        assertEquals("replacement", queue.drain().getFirst().value());
    }
    @Test void capacityRejectsAdditionalInputsWithoutReplacingAcceptedEntries() {
        var queue = new SessionInputs<Integer>();
        for (int i = 0; i < SessionInputs.MAX_PER_TICK; i++) { assertTrue(queue.offer(new PlayerInput<>(1, 0, i, i), 1)); }
        assertFalse(queue.offer(new PlayerInput<>(1, 0, 999, 999), 1));
        assertEquals(SessionInputs.MAX_PER_TICK, queue.drain().size());
    }
    @Test void envelopeRejectsInvalidIndicesAndNullPayload() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerInput<>(0, 0, 0, "x"));
        assertThrows(IllegalArgumentException.class, () -> new PlayerInput<>(1, -1, 0, "x"));
        assertThrows(IllegalArgumentException.class, () -> new PlayerInput<>(1, 0, -1, "x"));
        assertThrows(IllegalArgumentException.class, () -> new PlayerInput<>(1, 0, Long.MAX_VALUE, "x"));
        assertThrows(NullPointerException.class, () -> new PlayerInput<>(1, 0, 0, null));
    }
}
