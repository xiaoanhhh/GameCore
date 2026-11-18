package dev.gamecore.api.replay;

import dev.gamecore.api.event.GameEvent;
import dev.gamecore.api.session.GameSession;
import dev.gamecore.api.session.PlayerInput;
import dev.gamecore.api.session.SessionState;
import java.util.ArrayList;
import java.util.List;

/**
 * 可选的会话装饰器；只录入已接收输入、有效生命周期变化和实际模拟步。
 * 暂停前已接收但被取消的输入仍保留，重放同样的清理操作；内存日志适合短局/测试，不在普通客户端默认开启。
 */
public final class RecordingSession<C, I, S> implements GameSession<I, S> {
    private final SessionFactory<C, I, S> factory;
    private final C configuration;
    private final long seed;
    private final GameSession<I, S> delegate;
    private final S initial;
    private final List<ReplayOperation<I>> operations = new ArrayList<>();
    private final List<GameEvent> history = new ArrayList<>();
    public RecordingSession(SessionFactory<C, I, S> factory, C configuration, long seed) {
        this.factory = factory; this.configuration = configuration; this.seed = seed;
        delegate = factory.create(configuration, seed); initial = delegate.snapshot();
        if (delegate.state() != SessionState.READY || delegate.tickNumber() != 0
                || delegate.ticksPerSecond() != factory.rules().ticksPerSecond()) {
            throw new IllegalArgumentException("Factory must create a fresh compatible session");
        }
    }
    private void control(ReplayOperation.Action action, Runnable operation) {
        SessionState before = state(); operation.run();
        if (state() != before) { operations.add(new ReplayOperation.Control<>(tickNumber(), action)); }
    }
    @Override public void start() { control(ReplayOperation.Action.START, delegate::start); }
    @Override public void pause() { control(ReplayOperation.Action.PAUSE, delegate::pause); }
    @Override public void resume() { control(ReplayOperation.Action.RESUME, delegate::resume); }
    @Override public boolean submitInput(PlayerInput<I> input) {
        if (!delegate.submitInput(input)) { return false; }
        operations.add(new ReplayOperation.Input<>(tickNumber(), input)); return true;
    }
    @Override public void tick() {
        long before = tickNumber(); delegate.tick();
        if (tickNumber() != before) { operations.add(new ReplayOperation.Advance<>(before)); history.addAll(events()); }
    }
    @Override public S snapshot() { return delegate.snapshot(); }
    @Override public SessionState state() { return delegate.state(); }
    @Override public long tickNumber() { return delegate.tickNumber(); }
    @Override public int ticksPerSecond() { return delegate.ticksPerSecond(); }
    @Override public List<GameEvent> events() { return delegate.events(); }
    public ReplayTape<C, I, S> tape() {
        return new ReplayTape<>(factory.rules(), configuration, seed, initial, operations,
                new ReplayTape.Result<>(tickNumber(), state(), snapshot(), history));
    }
}
