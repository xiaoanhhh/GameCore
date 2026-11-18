package dev.gamecore.api.session;

import java.util.List;
import java.util.TreeMap;

/** 有界的下一步输入邮箱；稳定序号排序，不依赖 HashMap 或输入到达顺序。由会话验证生命周期/玩家权限。 */
public final class SessionInputs<I> {
    public static final int MAX_PER_TICK = 256;
    private final TreeMap<Long, PlayerInput<I>> pending = new TreeMap<>();
    public boolean offer(PlayerInput<I> input, long nextTick) {
        if (input.tick() != nextTick || pending.containsKey(input.sequence()) || pending.size() >= MAX_PER_TICK) { return false; }
        pending.put(input.sequence(), input);
        return true;
    }
    public long nextSequence() { return pending.isEmpty() ? 0 : pending.lastKey() + 1; }
    public List<PlayerInput<I>> drain() {
        List<PlayerInput<I>> result = List.copyOf(pending.values());
        clear(); return result;
    }
    public void clear() { pending.clear(); }
}
