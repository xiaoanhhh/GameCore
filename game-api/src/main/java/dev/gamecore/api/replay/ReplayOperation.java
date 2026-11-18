package dev.gamecore.api.replay;

import dev.gamecore.api.session.PlayerInput;
import java.util.Objects;

/** 日志列表顺序就是操作顺序；tick 是操作前已完成的步数，避免暂停清队列与输入的先后关系丢失。 */
public sealed interface ReplayOperation<I> {
    long tick();
    enum Action { START, PAUSE, RESUME }
    record Input<I>(long tick, PlayerInput<I> input) implements ReplayOperation<I> {
        public Input { Objects.requireNonNull(input); }
    }
    record Control<I>(long tick, Action action) implements ReplayOperation<I> {
        public Control { Objects.requireNonNull(action); }
    }
    record Advance<I>(long tick) implements ReplayOperation<I> { }
}
