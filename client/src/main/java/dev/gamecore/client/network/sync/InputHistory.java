package dev.gamecore.client.network.sync;

import dev.gamecore.protocol.TankProtocol;
import java.util.List;
import java.util.TreeMap;

/** 渲染线程独占的未确认输入；收到不等于执行，过期未执行另行计数，不伪造ack。 */
public final class InputHistory {
    public static final int CAPACITY = 128;
    private final TreeMap<Long, TankProtocol.PlayerInput> entries = new TreeMap<>();
    private long overflow, expired, acknowledged;
    public boolean add(TankProtocol.PlayerInput value) {
        if (value.getSequence() <= 0 || value.getTargetTick() <= 0) { throw new IllegalArgumentException("Invalid input history entry"); }
        if (value.getSequence() <= acknowledged || entries.containsKey(value.getSequence())) { return false; }
        if (entries.size() == CAPACITY) { entries.clear(); overflow++; return false; }
        entries.put(value.getSequence(), value); return true;
    }
    public void acknowledge(long executed, long tick) {
        acknowledged = Math.max(acknowledged, executed);
        entries.entrySet().removeIf(entry -> {
            if (entry.getKey() <= executed) { return true; }
            if (entry.getValue().getTargetTick() <= tick) { expired++; return true; }
            return false;
        });
    }
    public void reject(long sequence) { entries.remove(sequence); }
    public List<TankProtocol.PlayerInput> values() { return List.copyOf(entries.values()); }
    public int size() { return entries.size(); }
    public long overflows() { return overflow; }
    public long expired() { return expired; }
    public void clear() { entries.clear(); }
}
