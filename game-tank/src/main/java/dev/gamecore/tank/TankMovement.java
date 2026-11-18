package dev.gamecore.tank;

import java.util.List;

/** 原Tank的60Hz移动核：同步提案、先横后纵地图碰撞、双车统一回退；规则与预测共同调用。 */
public final class TankMovement {
    public record Motion(Vec2 position, double heading, Vec2 velocity) {
        public static Motion of(TankSnapshot.Tank tank) { return new Motion(tank.position(), tank.heading(), tank.velocity()); }
    }
    private TankMovement() { }
    public static List<Motion> step(TankMap map, List<Motion> tanks, List<TankCommand> commands) {
        if (tanks.size() != 2 || commands.size() != 2) { throw new IllegalArgumentException("Two tanks required"); }
        return resolve(map, tanks, List.of(propose(tanks.get(0), commands.get(0)), propose(tanks.get(1), commands.get(1))));
    }
    public record Proposal(double heading, Vec2 delta) { }
    /** Movement阶段只形成移动意图；Collision阶段统一提交，仍然使用原来的运算顺序。 */
    public static Proposal propose(Motion tank, TankCommand command) {
        double heading = TankGame.normalize(tank.heading() + command.turn() * TankGame.TURN_SPEED / TankGame.TICKS_PER_SECOND);
        return new Proposal(heading, Vec2.direction(heading).scale(command.throttle() * TankGame.MOVE_SPEED / TankGame.TICKS_PER_SECOND));
    }
    public static List<Motion> resolve(TankMap map, List<Motion> tanks, List<Proposal> proposals) {
        if (tanks.size() != 2 || proposals.size() != 2) { throw new IllegalArgumentException("Two tanks required"); }
        Vec2[] next = new Vec2[2]; double[] headings = new double[2];
        for (int p = 0; p < 2; p++) {
            Motion tank = tanks.get(p); var proposal = proposals.get(p); headings[p] = proposal.heading();
            Vec2 horizontal = tank.position().add(new Vec2(proposal.delta().x(), 0));
            if (map.blocked(horizontal, TankGame.TANK_RADIUS)) { horizontal = tank.position(); }
            Vec2 vertical = horizontal.add(new Vec2(0, proposal.delta().y()));
            next[p] = map.blocked(vertical, TankGame.TANK_RADIUS) ? horizontal : vertical;
        }
        if (next[0].distanceSquared(next[1]) < 4 * TankGame.TANK_RADIUS * TankGame.TANK_RADIUS) {
            next[0] = tanks.get(0).position(); next[1] = tanks.get(1).position();
        }
        return List.of(motion(tanks.get(0), next[0], headings[0]), motion(tanks.get(1), next[1], headings[1]));
    }
    private static Motion motion(Motion previous, Vec2 next, double heading) {
        return new Motion(next, heading, new Vec2(next.x() - previous.position().x(), next.y() - previous.position().y()).scale(TankGame.TICKS_PER_SECOND));
    }
    /** 瞄准仍只能以既有转速旋转；不把网络角度直接写进朝向。 */
    public static TankCommand command(int throttle, int turn, boolean fire, Double aim, double heading) {
        if (aim != null) {
            double error = (TankGame.normalize(aim - heading) + 180) % 360 - 180;
            turn = Math.abs(error) < TankGame.TURN_SPEED / 60 / 2 ? 0 : error > 0 ? 1 : -1;
        }
        return new TankCommand(throttle, turn, fire);
    }
}
