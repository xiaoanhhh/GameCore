package dev.gamecore.api.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 同线程观察者：按注册顺序逐事件通知；一批内采用订阅快照，增删订阅从下一批生效。
 * 监听器异常收集返回，不能阻止其他消费者或回滚已经完成的规则步；所有者必须处理返回的失败。
 */
public final class EventBus {
    private final List<Consumer<GameEvent>> observers = new ArrayList<>();
    public Subscription subscribe(Consumer<GameEvent> observer) {
        Objects.requireNonNull(observer, "observer");
        Consumer<GameEvent> registration = observer::accept;
        observers.add(registration);
        return () -> observers.remove(registration);
    }
    public List<RuntimeException> publish(List<GameEvent> batch) {
        var listeners = List.copyOf(observers);
        List<RuntimeException> failures = new ArrayList<>();
        for (GameEvent event : List.copyOf(batch)) {
            for (var observer : listeners) {
                try { observer.accept(event); } catch (RuntimeException failure) { failures.add(failure); }
            }
        }
        return List.copyOf(failures);
    }
    public void clear() { observers.clear(); }
    @FunctionalInterface public interface Subscription extends AutoCloseable {
        @Override void close();
    }
}
