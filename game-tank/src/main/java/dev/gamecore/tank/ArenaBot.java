package dev.gamecore.tank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * 三种输入策略：简单随机巡游；普通贪心追踪并绕开邻格障碍；高级 A*、射距决策与短时子弹预测。
 * 策略状态只存在此实例，种子在会话创建时注入；暂停不采样随机数，重新开始重建策略。
 */
public final class ArenaBot implements TankBot {
    private final Level level;
    private final Random random;
    private State state = State.PATROL;
    private TankCommand wandering = TankCommand.IDLE;
    private Vec2 waypoint;
    private long waypointTick;
    public ArenaBot(Level level, long seed) { this.level = Objects.requireNonNull(level); random = new Random(seed); }
    @Override public State state() { return state; }
    @Override public TankCommand command(TankSnapshot view, int player) {
        if (view.state() != TankGame.State.RUNNING) { return TankCommand.IDLE; }
        if (player < 0 || player > 1) { throw new IllegalArgumentException("Unknown Bot player"); }
        if (level == Level.EASY) {
            state = State.PATROL;
            if (view.tick() % 40 == 0) { wandering = new TankCommand(random.nextInt(4) == 0 ? -1 : 1, random.nextInt(3) - 1, random.nextBoolean()); }
            return wandering;
        }
        var self = view.tanks().get(player); var enemy = view.tanks().get(1 - player);
        TankMap map = view.map();
        if (level == Level.HARD && threatened(view, self)) {
            state = State.EVADE;
            Vec2 safest = neighbors(map, self.position()).stream().max(Comparator.comparingDouble(point -> safety(view, self.id(), point))).orElse(self.position());
            waypoint = null; return steer(self, safest, false);
        }
        boolean clear = Collision.wall(map, self.position(), enemy.position(), TankGame.BULLET_RADIUS) > 1;
        double range = level == Level.HARD ? 9 : 12;
        if (clear && self.position().distanceSquared(enemy.position()) <= range * range) {
            state = State.ATTACK; waypoint = null; return steer(self, enemy.position(), true);
        }
        state = State.CHASE;
        if (level == Level.HARD) {
            if (waypoint == null || self.position().distanceSquared(waypoint) < .025 || view.tick() - waypointTick >= 90) {
                var route = GridPathfinder.find(map, self.position(), enemy.position());
                Vec2 center = new Vec2((int) self.position().x() + .5, (int) self.position().y() + .5);
                // 先回到当前格中心，才走四邻接路线，避免旋转时切入墙角。
                waypoint = self.position().distanceSquared(center) > .025 ? center : route.isEmpty() ? enemy.position() : route.getFirst();
                waypointTick = view.tick();
            }
        } else if (waypoint == null || self.position().distanceSquared(waypoint) < .025 || view.tick() - waypointTick >= 100) {
            List<Vec2> options = neighbors(map, self.position());
            waypoint = options.stream().min(Comparator.comparingDouble(point -> point.distanceSquared(enemy.position()))).orElse(self.position());
            waypointTick = view.tick();
        }
        return steer(self, waypoint, false);
    }
    private static TankCommand steer(TankSnapshot.Tank self, Vec2 target, boolean attacking) {
        double heading = StrictMath.toDegrees(StrictMath.atan2(target.y() - self.position().y(), target.x() - self.position().x()));
        double error = (TankGame.normalize(heading - self.heading()) + 180) % 360 - 180;
        int turn = Math.abs(error) < 2 ? 0 : error > 0 ? 1 : -1;
        boolean fire = attacking && Math.abs(error) < 6;
        return new TankCommand(!attacking && Math.abs(error) < 28 ? 1 : 0, turn, fire);
    }
    private static List<Vec2> neighbors(TankMap map, Vec2 position) {
        List<Vec2> points = new ArrayList<>(); int x = (int) position.x(), y = (int) position.y();
        for (int[] d : new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
            if (!map.wall(x + d[0], y + d[1])) { points.add(new Vec2(x + d[0] + .5, y + d[1] + .5)); }
        }
        return points;
    }
    private static boolean threatened(TankSnapshot view, TankSnapshot.Tank tank) {
        return view.bullets().stream().anyMatch(b -> b.owner() != tank.id()
                && Collision.circle(b.position(), b.position().add(b.velocity().scale(.4)), tank.position(), .8) <= 1);
    }
    private static double safety(TankSnapshot view, int player, Vec2 point) {
        return view.bullets().stream().filter(b -> b.owner() != player)
                .mapToDouble(b -> point.distanceSquared(b.position().add(b.velocity().scale(.25)))).min().orElse(100);
    }
}
