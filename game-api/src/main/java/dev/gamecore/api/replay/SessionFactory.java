package dev.gamecore.api.replay;

import dev.gamecore.api.session.GameSession;

/** 游戏提供实际工厂；配置 C 必须深度不可变并包含完整初始地图/模式，随机序列只由 seed 初始化。 */
public interface SessionFactory<C, I, S> {
    Rules rules();
    GameSession<I, S> create(C configuration, long seed);

    /** 版本是兼容性标识，规则、随机算法或冲突策略发生变化时应升级；不能宣称跨版本自动兼容。 */
    record Rules(String game, String version, int ticksPerSecond) {
        public Rules {
            if (game == null || game.isBlank() || version == null || version.isBlank() || ticksPerSecond < 1) {
                throw new IllegalArgumentException("Invalid rules metadata");
            }
        }
    }
}
