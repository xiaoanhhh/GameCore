package dev.gamecore.api.session;

import dev.gamecore.api.event.EventBus;
import java.util.List;
import java.util.Objects;

/** 客户端、测试和回放共用的逐步执行器；每次实际前进后立即发布事件，追赶多步也不会丢事件或重复发布。 */
public final class SessionLoop<I, S> {
    private final GameSession<I, S> session;
    private final EventBus events = new EventBus();
    public SessionLoop(GameSession<I, S> session) { this.session = Objects.requireNonNull(session); }
    public EventBus events() { return events; }
    public List<RuntimeException> tick() {
        if (session.state() != SessionState.RUNNING) { return List.of(); }
        long before = session.tickNumber();
        session.tick();
        return session.tickNumber() == before ? List.of() : events.publish(session.events());
    }
}
