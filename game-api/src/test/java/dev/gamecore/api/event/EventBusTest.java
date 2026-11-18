package dev.gamecore.api.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 验证观察者顺序、故障隔离和订阅释放；事件源本身不持有消费者。 */
class EventBusTest {
    private static final GameEvent EVENT = new GameEvent.MatchEnded(1, "DRAW");
    @Test void orderedNotificationsAndSubscriptionReleaseAreStable() {
        var bus = new EventBus(); List<String> seen = new ArrayList<>();
        var first = bus.subscribe(event -> seen.add("a")); bus.subscribe(event -> seen.add("b"));
        assertTrue(bus.publish(List.of(EVENT, EVENT)).isEmpty());
        assertEquals(List.of("a", "b", "a", "b"), seen);
        first.close(); first.close(); seen.clear(); bus.publish(List.of(EVENT)); assertEquals(List.of("b"), seen);
        bus.clear(); seen.clear(); bus.publish(List.of(EVENT)); assertTrue(seen.isEmpty());
    }
    @Test void throwingObserverDoesNotPreventOtherObserversAndFailuresAreReturned() {
        var bus = new EventBus(); List<GameEvent> seen = new ArrayList<>();
        bus.subscribe(event -> { throw new IllegalStateException("audio unavailable"); }); bus.subscribe(seen::add);
        var failures = bus.publish(List.of(EVENT));
        assertEquals(1, failures.size()); assertEquals("audio unavailable", failures.getFirst().getMessage());
        assertEquals(List.of(EVENT), seen);
    }
    @Test void changesDuringBatchApplyToNextBatchAndDuplicateRegistrationsAreIndependent() {
        var bus = new EventBus(); List<String> seen = new ArrayList<>();
        Consumer<GameEvent> same = event -> seen.add("same");
        var first = bus.subscribe(same); bus.subscribe(same); first.close();
        bus.subscribe(event -> { bus.clear(); bus.subscribe(next -> seen.add("new")); });
        bus.publish(List.of(EVENT, EVENT)); assertEquals(List.of("same", "same"), seen);
        seen.clear(); bus.publish(List.of(EVENT)); assertEquals(List.of("new"), seen);
    }
}
