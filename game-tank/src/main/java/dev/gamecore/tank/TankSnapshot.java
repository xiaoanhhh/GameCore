package dev.gamecore.tank;

import java.util.List;

/** 规则向外发布的不可变快照；事件仅属于最近一次 Tick，消费方以 tick 去重。 */
public record TankSnapshot(TankMap map, long tick, TankGame.State state, TankGame.Result result,
                           List<Tank> tanks, List<Bullet> bullets, List<Event> events) {
    public record Tank(int id, Vec2 position, double heading, Vec2 velocity, int health, int cooldown) {
        public boolean alive() { return health > 0; }
    }
    public record Bullet(long id, int owner, Vec2 position, Vec2 velocity, int remainingTicks) { }
    public enum EventType { SHOT, HIT, DESTROYED, WALL_HIT, REPAIRED, FINISHED }
    public record Event(EventType type, int actor, int target, Vec2 position) { }
    public TankSnapshot { tanks = List.copyOf(tanks); bullets = List.copyOf(bullets); events = List.copyOf(events); }
}
