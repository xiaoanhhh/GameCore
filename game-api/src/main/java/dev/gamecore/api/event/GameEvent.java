package dev.gamecore.api.event;

/** 模拟只产生值事件；订阅、动画、音频和战绩 IO 均在模拟外。tick 使用会话 Tick，玩家编号从 0 开始。 */
public sealed interface GameEvent {
    long tick();
    record PlayerDied(long tick, int player) implements GameEvent { }
    record MatchEnded(long tick, String result) implements GameEvent { }
    record ScoreChanged(long tick, int player, int previous, int current) implements GameEvent { }
    record HealthChanged(long tick, int player, int previous, int current) implements GameEvent { }

    /** 视觉提示的游戏坐标及朝向，不参与物理碰撞，也不要求游戏使用共同实体。 */
    record Effect(long tick, Kind kind, int player, double x, double y, double heading) implements GameEvent {
        public enum Kind { SHOT, HIT, WALL_HIT, DESTROYED, REPAIRED }
    }
}
