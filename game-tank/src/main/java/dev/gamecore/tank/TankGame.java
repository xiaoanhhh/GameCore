package dev.gamecore.tank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import static dev.gamecore.tank.TankSnapshot.EventType.*;


//利用TankGame创建一局比赛对象
public final class TankGame {
    public static final int TICKS_PER_SECOND = 60;      //每模拟秒执行 60 次规则更新
    public static final int MAX_HEALTH = 5;     //每辆坦克最多 5 点生命
    public static final int FIRE_COOLDOWN = 24;     //连续两次射击间隔 24 Tick，即 0.4 模拟秒
    public static final int BULLET_LIFETIME = 150;      //没撞到东西的子弹最多存活 150 Tick，即 2.5 模拟秒
    public static final int MATCH_TICKS = 180 * TICKS_PER_SECOND;  //一局最多 10800 Tick，即 180 模拟秒
    public static final double TANK_RADIUS = .32;   //坦克碰撞圆的半径为 0.32 格
    public static final double BULLET_RADIUS = .07; //子弹碰撞半径为 0.07 格
    public static final double MOVE_SPEED = 3.2;  //坦克每模拟秒移动 3.2 格
    public static final double TURN_SPEED = 150;  //坦克每模拟秒转动 150 度
    public static final double BULLET_SPEED = 12; //子弹每模拟秒移动 12 格
    public enum State { READY, RUNNING, PAUSED, FINISHED }  //枚举比赛状态
    public enum Result { NONE, PLAYER_ONE, PLAYER_TWO, DRAW } //枚举比赛结果

    /**
     * 游戏内的tank对象
     */
    private static final class Tank {
        private Vec2 position; //tank位置
        private Vec2 velocity = new Vec2(0, 0); //tank当前的实际速度
        private double heading; //朝向
        private int health = MAX_HEALTH; //生命
        private int cooldown;
        private int repairTicks;
        //利用TankMap中的tank出生点初始化tank
        private Tank(TankMap.Spawn spawn) { position = spawn.position(); heading = normalize(spawn.heading()); }
    }

    /**
     * 子弹对象
     */
    private static final class Bullet {
        private final long id;
        private final int owner;
        private Vec2 position;
        private final Vec2 velocity;
        private int life = BULLET_LIFETIME;
        private Bullet(long id, int owner, Vec2 position, Vec2 velocity) {
            this.id = id; this.owner = owner; this.position = position; this.velocity = velocity;
        }
    }
    private final TankMap map;
    private final Tank[] tanks;
    private final TankCommand[] commands = {TankCommand.IDLE, TankCommand.IDLE};
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<TankSnapshot.Event> events = new ArrayList<>();
    private State state = State.READY;
    private Result result = Result.NONE;
    private long tick;
    private long nextBullet;

    public TankGame(TankMap map) {
        this.map = Objects.requireNonNull(map);
        tanks = map.spawns().stream().map(Tank::new).toArray(Tank[]::new);
    }
    /** 只读标量供会话调度使用，避免为了判断生命周期反复复制整个世界。 */
    public State state() { return state; }
    public long tickNumber() { return tick; }
    public void start() { if (state == State.READY || state == State.PAUSED) { state = State.RUNNING; } }
    public void pause() {
        if (state == State.RUNNING) { state = State.PAUSED; }
        clearCommands();
    }
    public void clearCommands() { Arrays.fill(commands, TankCommand.IDLE); }
    public void command(int player, TankCommand command) {
        if (player < 0 || player >= tanks.length) { throw new IllegalArgumentException("Unknown player"); }
        if (state == State.RUNNING) { commands[player] = Objects.requireNonNull(command); }
    }
    public void tick() {
        if (state != State.RUNNING) { return; }
        tick++; events.clear();
        moveTanks();
        for (int p = 0; p < tanks.length; p++) {
            Tank tank = tanks[p];
            tank.cooldown = TankRules.cooldown(tank.cooldown);
            if (TankRules.fires(commands[p].fire(), tank.cooldown, tank.health)) {
                bullets.add(new Bullet(nextBullet++, p, tank.position, Vec2.direction(tank.heading).scale(BULLET_SPEED)));
                tank.cooldown = FIRE_COOLDOWN; event(SHOT, p, -1, tank.position);
            }
        }
        int[] damage = advanceBullets();
        for (int p = 0; p < tanks.length; p++) {
            Tank tank = tanks[p]; tank.health = TankRules.health(tank.health, damage[p]);
            if (tank.health == 0) { event(DESTROYED, p, p, tank.position); }
        }
        if (TankRules.finished(tanks[0].health, tanks[1].health, tick)) {
            result = TankRules.result(tanks[0].health, tanks[1].health);
            state = State.FINISHED; clearCommands(); event(FINISHED, -1, -1, new Vec2(0, 0));
        } else { repair(); }
    }
    private void moveTanks() {
        var motions = Arrays.stream(tanks).map(tank -> new TankMovement.Motion(tank.position, tank.heading, tank.velocity)).toList();
        var next = TankMovement.step(map, motions, List.of(commands[0], commands[1]));
        for (int p = 0; p < 2; p++) {
            tanks[p].position = next.get(p).position(); tanks[p].heading = next.get(p).heading(); tanks[p].velocity = next.get(p).velocity();
        }
    }
    private int[] advanceBullets() {
        int[] damage = new int[2];
        for (Iterator<Bullet> iterator = bullets.iterator(); iterator.hasNext();) {
            Bullet bullet = iterator.next();
            var step = TankRules.projectile(map, bullet.position, bullet.velocity, bullet.life, bullet.owner, tanks[1 - bullet.owner].position);
            if (step.event() != null) {
                if (step.target() >= 0) { damage[step.target()]++; }
                event(step.event(), bullet.owner, step.target(), step.position());
            }
            if (step.removed()) { iterator.remove(); }
            else { bullet.position = step.position(); bullet.life = step.life(); }
        }
        return damage;
    }
    private void repair() {
        for (int p = 0; p < 2; p++) {
            Tank tank = tanks[p];
            var repair = TankRules.repair(map, tank.position, tank.health, tank.repairTicks);
            tank.health = repair.health(); tank.repairTicks = repair.ticks();
            if (repair.repaired()) { event(REPAIRED, p, p, tank.position); }
        }
    }
    private void event(TankSnapshot.EventType type, int actor, int target, Vec2 position) { events.add(new TankSnapshot.Event(type, actor, target, position)); }
    public static double normalize(double angle) { return (angle % 360 + 360) % 360; }
    public TankSnapshot snapshot() {
        List<TankSnapshot.Tank> views = new ArrayList<>();
        for (int p = 0; p < 2; p++) {
            Tank t = tanks[p]; views.add(new TankSnapshot.Tank(p, t.position, t.heading, t.velocity, t.health, t.cooldown));
        }
        return new TankSnapshot(map, tick, state, result, views,
                bullets.stream().map(b -> new TankSnapshot.Bullet(b.id, b.owner, b.position, b.velocity, b.life)).toList(), events);
    }
}
