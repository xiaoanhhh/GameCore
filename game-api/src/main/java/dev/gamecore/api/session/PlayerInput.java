package dev.gamecore.api.session;

import java.util.Objects;

/** 玩家编号从 0 开始，目标 Tick 从 1 开始；sequence 在同一 Tick 内全局唯一，与提交到达顺序无关。 */
public record PlayerInput<I>(long tick, int player, long sequence, I value) {
    public PlayerInput {
        if (tick < 1 || player < 0 || sequence < 0 || sequence > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid tick, player or sequence");
        }
        Objects.requireNonNull(value, "value");
    }
}
