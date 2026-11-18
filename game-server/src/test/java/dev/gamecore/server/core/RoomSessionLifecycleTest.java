package dev.gamecore.server.core;

import dev.gamecore.api.event.GameEvent;
import dev.gamecore.api.session.GameSession;
import dev.gamecore.api.session.PlayerInput;
import dev.gamecore.api.session.SessionState;
import dev.gamecore.tank.TankBot;
import dev.gamecore.tank.TankCommand;
import dev.gamecore.tank.TankMode;
import dev.gamecore.tank.TankSession;
import dev.gamecore.tank.TankSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 精简实验模块后，仍以真实Tank规则验证Room会话的单写入者及资源生命周期。 */
class RoomSessionLifecycleTest {
    /** 只记录生命周期与线程，不复制规则；AutoCloseable代表Room可拥有的受信资源。 */
    private static final class TrackedSession implements GameSession<TankCommand, TankSnapshot>, AutoCloseable {
        private final Thread owner = Thread.currentThread();
        private final TankSession delegate;
        private int closes;
        TrackedSession(TankSession delegate) { this.delegate = delegate; }
        private void requireOpen() {
            assertSame(owner, Thread.currentThread());
            if (closes != 0) { throw new IllegalStateException("Closed test session"); }
        }
        @Override public void start() { requireOpen(); delegate.start(); }
        @Override public void pause() { requireOpen(); delegate.pause(); }
        @Override public void resume() { requireOpen(); delegate.resume(); }
        @Override public boolean submitInput(PlayerInput<TankCommand> input) { requireOpen(); return delegate.submitInput(input); }
        @Override public void tick() { requireOpen(); delegate.tick(); }
        @Override public TankSnapshot snapshot() { requireOpen(); return delegate.snapshot(); }
        @Override public SessionState state() { requireOpen(); return delegate.state(); }
        @Override public long tickNumber() { requireOpen(); return delegate.tickNumber(); }
        @Override public int ticksPerSecond() { requireOpen(); return delegate.ticksPerSecond(); }
        @Override public List<GameEvent> events() { requireOpen(); return delegate.events(); }
        @Override public void close() { requireOpen(); closes++; }
    }

    @Test void factoryReplacementAndCloseStayOnRoomOwner() throws Exception {
        var sessions = new ArrayList<TrackedSession>();
        Thread owner = Thread.currentThread();
        var room = new GameRoom(RoomFixtures.open(), 30, 42, (map, seed) -> {
            assertSame(owner, Thread.currentThread());
            var session = new TrackedSession(new TankSession(map, TankMode.DUEL, TankBot.Level.NORMAL, seed));
            sessions.add(session); return session;
        });
        assertTrue(sessions.isEmpty());
        room.step(); assertEquals(1, sessions.size());
        var a = new RoomFixtures.Peer(); var b = new RoomFixtures.Peer();
        room.requestJoin(a, 1); room.requestJoin(b, 2); room.step();
        assertEquals(RoomFrame.State.RUNNING, room.latest().state());
        String match = room.latest().matchId();
        var foreign = new FutureTask<>(() -> { room.step(); return null; });
        var thread = new Thread(foreign); thread.start();
        var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> foreign.get(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, failure.getCause()); thread.join(1000);
        assertEquals(1, sessions.size()); assertEquals(0, sessions.getFirst().closes);
        a.disconnected = true; b.disconnected = true; room.step();
        assertEquals(2, sessions.size()); assertEquals(1, sessions.getFirst().closes);
        assertNotEquals(match, room.latest().matchId());
        assertThrows(IllegalStateException.class, () -> sessions.getFirst().snapshot());
        room.close(); assertEquals(1, sessions.getLast().closes);
        assertThrows(IllegalStateException.class, () -> sessions.getLast().snapshot());
    }
}
