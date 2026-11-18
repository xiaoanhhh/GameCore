package dev.gamecore.server.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/** 多网络生产者→单房间消费者；先预留每连接额度，失败必须归还，drain捕获当前批次边界。 */
public final class RoomInputQueue {
    public static final int CAPACITY = 128;
    public static final int PER_CONNECTION = 32;
    public record Entry(RoomPeer peer, InputIntent input) { }
    private final ArrayBlockingQueue<Entry> queue = new ArrayBlockingQueue<>(CAPACITY);
    private final AtomicBoolean closed = new AtomicBoolean();
    public boolean offer(RoomPeer peer, InputIntent input) {
        if (closed.get() || peer.disconnected()) { return false; }
        if (peer.queuedInputs().incrementAndGet() > PER_CONNECTION) { peer.queuedInputs().decrementAndGet(); return false; }
        var entry = new Entry(peer, input);
        if (!queue.offer(entry)) { peer.queuedInputs().decrementAndGet(); return false; }
        // 关闭可能与最后一次offer并发；若已被drain取走，额度由消费者归还。
        if (closed.get()) {
            if (queue.remove(entry)) { peer.queuedInputs().decrementAndGet(); }
            return false;
        }
        return true;
    }
    public List<Entry> drain() {
        int boundary = queue.size(); List<Entry> batch = new ArrayList<>(boundary);
        for (int index = 0; index < boundary; index++) {
            var entry = queue.poll(); if (entry == null) { break; }
            entry.peer().queuedInputs().decrementAndGet(); batch.add(entry);
        }
        return batch;
    }
    public int size() { return queue.size(); }
    public void close() { closed.set(true); drain(); }
}
