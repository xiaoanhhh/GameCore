package dev.gamecore.api.replay;

import dev.gamecore.api.event.GameEvent;
import dev.gamecore.api.session.SessionLoop;
import dev.gamecore.api.session.SessionState;
import java.util.ArrayList;
import java.util.List;

/** 同一执行框架重建两款游戏；拒绝规则版本/初始状态漂移、错序日志、被拒输入及最终状态或事件差异。 */
public final class ReplayRunner {
    private ReplayRunner() { }
    public static <C, I, S> ReplayTape.Result<S> verify(SessionFactory<C, I, S> factory, ReplayTape<C, I, S> tape) {
        if (!factory.rules().equals(tape.rules())) { throw new IllegalArgumentException("Incompatible replay rules"); }
        var session = factory.create(tape.configuration(), tape.seed());
        if (session.tickNumber() != 0 || session.state() != SessionState.READY
                || session.ticksPerSecond() != tape.rules().ticksPerSecond() || !session.snapshot().equals(tape.initial())) {
            throw new IllegalArgumentException("Initial replay state differs");
        }
        var loop = new SessionLoop<>(session);
        List<GameEvent> history = new ArrayList<>(); loop.events().subscribe(history::add);
        for (var operation : tape.operations()) {
            if (operation.tick() != session.tickNumber()) { throw new IllegalArgumentException("Replay tick is out of order"); }
            switch (operation) {
                case ReplayOperation.Input<I> input -> {
                    if (!session.submitInput(input.input())) { throw new IllegalArgumentException("Replay input rejected"); }
                }
                case ReplayOperation.Control<I> control -> {
                    var before = session.state();
                    switch (control.action()) {
                        case START -> session.start(); case PAUSE -> session.pause(); case RESUME -> session.resume();
                    }
                    if (session.state() == before) { throw new IllegalArgumentException("Replay control rejected"); }
                }
                case ReplayOperation.Advance<I> ignored -> {
                    long before = session.tickNumber();
                    if (!loop.tick().isEmpty() || session.tickNumber() != before + 1) {
                        throw new IllegalArgumentException("Replay cannot advance");
                    }
                }
            }
        }
        var result = new ReplayTape.Result<>(session.tickNumber(), session.state(), session.snapshot(), history);
        if (!result.equals(tape.expected())) { throw new IllegalStateException("Replay final state or events differ"); }
        return result;
    }
}
