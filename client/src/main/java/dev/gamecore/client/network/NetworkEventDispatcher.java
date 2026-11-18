package dev.gamecore.client.network;

import dev.gamecore.protocol.TankProtocol;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** IO→渲染消费者的有界邮箱；所有接收消息先入队，同步块内只有有界内存操作，不调用UI。 */
public final class NetworkEventDispatcher {
    public static final int CAPACITY = 128;
    public sealed interface Event permits Message, Notice { }
    public record Message(TankProtocol.Envelope value, long arrivalNanos) implements Event { }
    public record Notice(ConnectionState state, String text) implements Event { }
    private final ArrayDeque<Event> queue = new ArrayDeque<>();
    private Notice failure;
    private long staleSnapshots;
    private boolean closed;
    public synchronized boolean message(TankProtocol.Envelope value) {
        return message(value, System.nanoTime());
    }
    public synchronized boolean message(TankProtocol.Envelope value, long arrivalNanos) {
        if (closed || failure != null) { return false; }
        // 中间全量显示帧可合并；Join/Event/Result始终保留接收顺序。
        if (value.hasWorldSnapshot()) {
            boolean stale = queue.stream().anyMatch(event -> event instanceof Message message && message.value().hasWorldSnapshot()
                    && message.value().getWorldSnapshot().getMatchId().equals(value.getWorldSnapshot().getMatchId())
                    && message.value().getWorldSnapshot().getServerTick() > value.getWorldSnapshot().getServerTick());
            if (stale) { staleSnapshots++; return true; }
            queue.removeIf(event -> event instanceof Message message && message.value().hasWorldSnapshot());
        }
        if (queue.size() == CAPACITY) { return false; }
        queue.addLast(new Message(value, arrivalNanos)); return true;
    }
    public synchronized boolean notice(ConnectionState state, String text) {
        if (closed || failure != null || queue.size() == CAPACITY) { return false; }
        queue.addLast(new Notice(state, text)); return true;
    }
    public synchronized void fail(ConnectionState state, String text) {
        if (!closed && failure == null) { failure = new Notice(state, text); }
    }
    public synchronized List<Event> drain() {
        if (closed) { return List.of(); }
        var result = new ArrayList<Event>(queue.size() + 1);
        while (!queue.isEmpty()) { result.add(queue.removeFirst()); }
        if (failure != null) { result.add(failure); failure = null; }
        return List.copyOf(result);
    }
    public synchronized int size() { return queue.size(); }
    public synchronized long staleSnapshots() { return staleSnapshots; }
    public synchronized void close() { closed = true; queue.clear(); failure = null; }
}
