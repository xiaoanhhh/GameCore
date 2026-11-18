package dev.gamecore.api.replay;

import dev.gamecore.api.event.GameEvent;
import dev.gamecore.api.session.SessionState;
import java.util.List;
import java.util.Objects;

/** 内存中的最小回放数据；初始快照用于防止配置漂移，最终快照与逐步事件流都参与严格值校验。无文件格式/播放器。 */
public record ReplayTape<C, I, S>(SessionFactory.Rules rules, C configuration, long seed, S initial,
                                  List<ReplayOperation<I>> operations, Result<S> expected) {
    public ReplayTape {
        Objects.requireNonNull(rules); Objects.requireNonNull(configuration); Objects.requireNonNull(initial);
        operations = List.copyOf(operations); Objects.requireNonNull(expected);
    }
    public record Result<S>(long tick, SessionState state, S snapshot, List<GameEvent> events) {
        public Result {
            Objects.requireNonNull(state); Objects.requireNonNull(snapshot); events = List.copyOf(events);
        }
    }
}
