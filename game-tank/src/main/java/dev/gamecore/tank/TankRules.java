package dev.gamecore.tank;
/** 与存储模型无关的规则核；OOP/ECS共用数值、碰撞优先级和维修/胜负决策。 */
public final class TankRules {
    public record ProjectileStep(Vec2 position, int life, int target, TankSnapshot.EventType event, boolean removed) { }
    public record Repair(int health, int ticks, boolean repaired) { }
    private TankRules() { }
    public static int cooldown(int value) { return Math.max(0, value - 1); }
    public static boolean fires(boolean pressed, int cooldown, int health) { return pressed && cooldown == 0 && health > 0; }
    public static int health(int current, int damage) { return Math.max(0, current - damage); }
    public static boolean finished(int a, int b, long tick) { return a == 0 || b == 0 || tick >= TankGame.MATCH_TICKS; }
    public static TankGame.Result result(int a, int b) {
        return a == b ? TankGame.Result.DRAW : a > b ? TankGame.Result.PLAYER_ONE : TankGame.Result.PLAYER_TWO;
    }
    public static Repair repair(TankMap map, Vec2 position, int health, int ticks) {
        boolean zone = map.repairZones().stream().anyMatch(area -> area.contains(position));
        if (!zone || health == TankGame.MAX_HEALTH) { return new Repair(health, 0, false); }
        if (++ticks >= 120) { return new Repair(health + 1, 0, true); }
        return new Repair(health, ticks, false);
    }
    public static ProjectileStep projectile(TankMap map, Vec2 position, Vec2 velocity, int life, int owner, Vec2 targetPosition) {
        Vec2 end = position.add(velocity.scale(1.0 / TankGame.TICKS_PER_SECOND));
        double wall = Collision.wall(map, position, end, TankGame.BULLET_RADIUS);
        double tank = Collision.circle(position, end, targetPosition, TankGame.TANK_RADIUS + TankGame.BULLET_RADIUS);
        if (wall <= 1 || tank <= 1) {
            double time = Math.min(wall, tank);
            Vec2 impact = position.add(new Vec2(end.x() - position.x(), end.y() - position.y()).scale(time));
            return new ProjectileStep(impact, life, tank < wall ? 1 - owner : -1,
                    tank < wall ? TankSnapshot.EventType.HIT : TankSnapshot.EventType.WALL_HIT, true);
        }
        return new ProjectileStep(end, life - 1, -1, null, life == 1);
    }
}
