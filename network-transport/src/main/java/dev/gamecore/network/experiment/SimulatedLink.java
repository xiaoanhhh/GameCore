package dev.gamecore.network.experiment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 纯Java有界虚拟链路：调用者给nanoTime，可无等待推进实验；默认保持同方向TCP消息顺序。 */
public final class SimulatedLink<T> {
    public static final int CAPACITY = 128, TRACE_CAPACITY = 2048;
    public enum Admission { QUEUED, DROPPED, FULL }
    public record Trace(long id, String label, long enqueuedNanos, long dueNanos, long deliveredNanos, String action) { }
    public record Metrics(long offered, long delivered, long dropped, long overflows, int queued) { }
    private record Pending<T>(long id, String label, long enqueue, long due, T value) { }
    private final NetworkProfile profile;
    private final Random random;
    private final ArrayDeque<Pending<T>> queue = new ArrayDeque<>();
    private final ArrayDeque<Trace> trace = new ArrayDeque<>();
    private long epoch, lastDue, offered, delivered, dropped, overflows;
    private boolean initialized;
    public SimulatedLink(NetworkProfile profile, long directionSalt) { this.profile = profile; random = new Random(profile.seed() ^ directionSalt); }
    public synchronized Admission offer(T value, long now, boolean droppable, String label) {
        if (!initialized) { epoch = now; lastDue = now; initialized = true; }
        long id = ++offered;
        long elapsed = Math.max(0, now - epoch) / 1_000_000L;
        boolean blackout = profile.blackoutDurationMillis() > 0 && elapsed >= profile.blackoutStartMillis()
                && elapsed < (long) profile.blackoutStartMillis() + profile.blackoutDurationMillis();
        // 先固定取样，trace/调度消费次数不会改变后续随机序列。
        int jitter = profile.jitterMillis() == 0 ? 0 : random.nextInt(2 * profile.jitterMillis() + 1) - profile.jitterMillis();
        boolean randomDrop = droppable && random.nextInt(100) < profile.dropPercent();
        if (blackout || randomDrop) { dropped++; record(new Trace(id, label, now, now, now, blackout ? "BLACKOUT" : "DROP")); return Admission.DROPPED; }
        if (queue.size() == CAPACITY) { overflows++; record(new Trace(id, label, now, now, now, "FULL")); return Admission.FULL; }
        long delay = Math.max(0, profile.rttMillis() / 2L + jitter) * 1_000_000L;
        long due = Math.max(lastDue, now + delay); lastDue = due;
        queue.addLast(new Pending<>(id, label, now, due, value)); record(new Trace(id, label, now, due, -1, "QUEUE"));
        return Admission.QUEUED;
    }
    public synchronized List<T> drain(long now) {
        var values = new ArrayList<T>();
        while (!queue.isEmpty() && queue.getFirst().due() <= now) {
            var entry = queue.removeFirst(); delivered++; values.add(entry.value());
            record(new Trace(entry.id(), entry.label(), entry.enqueue(), entry.due(), now, "DELIVER"));
        }
        return List.copyOf(values);
    }
    /** 生命周期清理交还未发送对象；Netty适配器负责完成其promise，不留悬挂future。 */
    public synchronized List<T> clear() {
        var values = queue.stream().map(Pending::value).toList(); queue.clear(); return values;
    }
    public synchronized Metrics metrics() { return new Metrics(offered, delivered, dropped, overflows, queue.size()); }
    public synchronized List<Trace> trace() { return List.copyOf(trace); }
    private void record(Trace value) { if (trace.size() == TRACE_CAPACITY) { trace.removeFirst(); } trace.addLast(value); }
}
