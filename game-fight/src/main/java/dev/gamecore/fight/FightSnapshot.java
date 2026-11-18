package dev.gamecore.fight;

import dev.gamecore.api.session.SessionState;
import java.util.List;

/** 供 UI、Bot 和重模拟读取的深度不可变值；坐标以脚底为原点，y 向上。 */
public record FightSnapshot(long tick, SessionState state, Result result, List<Fighter> fighters,
                            List<Projectile> projectiles, List<Impact> impacts, int hitstopTicks, FightMap map,
                            List<Ultimate> ultimates,List<Teleport> teleports) {
    public FightSnapshot(long tick,SessionState state,Result result,List<Fighter> fighters,List<Projectile> projectiles,List<Impact> impacts,int hitstopTicks,FightMap map){
        this(tick,state,result,fighters,projectiles,impacts,hitstopTicks,map,List.of(),List.of());
    }
    public FightSnapshot(long tick, SessionState state, Result result, List<Fighter> fighters, List<Projectile> projectiles, List<Impact> impacts, int hitstopTicks) {
        this(tick,state,result,fighters,projectiles,impacts,hitstopTicks,FightMap.NEON);
    }
    public FightSnapshot {
        java.util.Objects.requireNonNull(map);
        fighters = List.copyOf(fighters); projectiles = List.copyOf(projectiles); impacts = List.copyOf(impacts);
        ultimates=List.copyOf(ultimates);teleports=List.copyOf(teleports);
    }
    public enum Result { NONE, PLAYER_ONE, PLAYER_TWO, DRAW }
    public enum Action { IDLE, WALK, RUN, SKID, PUNCH, PUNCH_2, PUNCH_3, AIR_PUNCH, UPPERCUT,
        RANGED, ULTIMATE, ULT_RELEASE, GUARD, GUARD_HIT, HURT, HURT_HEAVY, DASH,
        JUMP_UP, JUMP_APEX, JUMP_FALL, DOUBLE_JUMP, LAND, KNOCKDOWN, GETUP, WIN, LOSE, LAUGH, WEB_BOUND, TELEPORT }
    public record Fighter(int player, FighterKind kind, double x, double y, int facing, int health,
                          double energy, Action action, int actionTicks, int stunTicks, int slowTicks,
                          double vx, double vy, boolean grounded, int jumpCount, int platform, double travel,
                          int poseTick, int comboCount, int maxCombo, int totalDamage, int attacks, int hits,int teleportCooldown,long boundBy) {
        public Fighter(int player,FighterKind kind,double x,double y,int facing,int health,double energy,Action action,int actionTicks,int stunTicks,int slowTicks,
                       double vx,double vy,boolean grounded,int jumpCount,int platform,double travel,int poseTick,int comboCount,int maxCombo,int totalDamage,int attacks,int hits){
            this(player,kind,x,y,facing,health,energy,action,actionTicks,stunTicks,slowTicks,vx,vy,grounded,jumpCount,platform,travel,poseTick,comboCount,maxCombo,totalDamage,attacks,hits,0,0);
        }
    }
    public record Projectile(int owner, FighterKind kind, double x, double y, int direction, boolean ultimate) { }
    public enum Attack { UNKNOWN, JAB, CROSS, HOOK, UPPERCUT, RANGED, ULTIMATE, TELEPORT }
    /** 来源写入规则快照，客户端不再需要猜测新命中的拳型/受击侧；旧构造仅供兼容样本。 */
    public record Impact(long id, double x, double y, boolean blocked, FighterKind kind, int life,
                         int damage, int direction, boolean ultimate,int attacker,int defender,Attack attack) {
        public Impact(long id,double x,double y,boolean blocked,FighterKind kind,int life,int damage,int direction,boolean ultimate){this(id,x,y,blocked,kind,life,damage,direction,ultimate,-1,-1,Attack.UNKNOWN);}
        public Impact aged(){return new Impact(id,x,y,blocked,kind,life-1,damage,direction,ultimate,attacker,defender,attack);}
    }
    public record Strike(int index,double x,double y,int age,boolean aimed,boolean hit) { }
    /** 目标为真实锁定坐标；captured 仅在规则实际束缚成功后为真，missed 不伪装包裹对手。 */
    public record Ultimate(long id,int owner,FighterKind kind,int age,double casterX,double casterY,double targetX,double targetY,boolean captured,boolean missed,List<Strike> strikes){
        public Ultimate{strikes=List.copyOf(strikes);}
    }
    public record Teleport(long id,int owner,double fromX,double fromY,double toX,double toY,int direction,int age){
        public Teleport aged(){return new Teleport(id,owner,fromX,fromY,toX,toY,direction,age+1);}
    }
}
