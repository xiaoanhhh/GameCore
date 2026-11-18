package dev.gamecore.fight;

import dev.gamecore.api.event.GameEvent;
import dev.gamecore.api.session.GameSession;
import dev.gamecore.api.session.PlayerInput;
import dev.gamecore.api.session.SessionInputs;
import dev.gamecore.api.session.SessionState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import static dev.gamecore.fight.FightSnapshot.Action;

/**
 * 终极格斗的 60Hz 单线程规则：启动帧/恢复帧、方向格挡、跳跃、弹道与同步受击。
 * 碰撞采用连续线段检测防止高速弹穿透；先收集双方命中再结算，允许同时 KO。
 * 暂停清空邮箱；终局冻结全部规则。所有外部引用通过不可变快照隔离。
 */
public final class FightSession implements GameSession<FightCommand, FightSnapshot> {
    public static final int TICKS_PER_SECOND = 60;
    public static final int MATCH_TICKS = 99 * TICKS_PER_SECOND;
    public static final int MAX_HEALTH = 100;
    public static final int MAX_ENERGY = 100;
    public static final double WIDTH = 2240;
    public static final double BODY_HEIGHT = 100;
    public static final double BODY_SPACING = 92;
    public static final int RANGED_COST = 18;
    public static final int ULTIMATE_COST = 100;
    public static final int GUARD_COST = 12;
    private final FightSessionFactory.Config config;
    private final FightBot bot;
    private final Fighter[] fighters;
    private final SessionInputs<FightCommand> inputs = new SessionInputs<>();
    private final List<Shot> shots = new ArrayList<>();
    private final List<FightSnapshot.Impact> impacts = new ArrayList<>();
    private final Ritual[] rituals=new Ritual[2];
    private final List<FightSnapshot.Teleport> teleports=new ArrayList<>();
    private List<GameEvent> events = List.of();
    private SessionState state = SessionState.READY;
    private FightSnapshot.Result result = FightSnapshot.Result.NONE;
    private long tick, impactId,specialId;
    private int hitstop;
    private final int[] pulseLife = new int[2];
    /** 顿帧邮箱保存短按，避免动画停止期间吞掉出招；暂停时同样清空。 */
    private final FightCommand[] buffered = {FightCommand.NONE, FightCommand.NONE};
    public static final double WALK_MAX = 4.6, RUN_MAX = 8.4, AIR_MAX = 7, GRAVITY = .62;
    public static final double JUMP_VY = 13.4, DOUBLE_JUMP_VY = 11.2;
    public static final int RUN_HOLD = 12;
    /** 奶蛙娱乐动作持续 2.8 秒；无伤害/无消耗/无无敌，可被操作或受击打断。 */
    public static final int LAUGH_TICKS = 168;

    public FightSession(FightSessionFactory.Config config, long seed) {
        this.config = Objects.requireNonNull(config);
        bot = config.mode() == FightSessionFactory.Mode.BOT ? new FightBot(seed) : null;
        fighters = new Fighter[]{new Fighter(0, config.one(), 560, 1), new Fighter(1, config.two(), 1680, -1)};
    }
    private static final class Fighter {
        final int player; final FighterKind kind;
        double x, y, vx, vy, travel, energy = 30;
        int facing, health = MAX_HEALTH, actionTicks, stun, slow, dashCooldown, jumps, runTicks, lastMove,
            coyote = 4, jumpBuffer, ignored = -1, ignoreTicks, platform = -1, land, poseTick,
            chain, chainWindow, comboCount, comboAge, maxCombo, totalDamage, attacks, hits, parry,teleportCooldown;
        long boundBy;
        /** 普通拳只排队下一拳，多次快按合并；被打断、暂停时丢弃，不能无限自动连打。 */
        boolean grounded = true, knockdown, queuedPunch;
        Action action = Action.IDLE;
        Fighter(int player, FighterKind kind, double x, int facing) { this.player = player; this.kind = kind; this.x = x; this.facing = facing; }
        FightSnapshot.Fighter view() { return new FightSnapshot.Fighter(player, kind, x, y, facing, health, energy,
            action, actionTicks, stun, slow, vx, vy, grounded, jumps, platform, travel, poseTick, comboCount, maxCombo, totalDamage, attacks, hits,teleportCooldown,boundBy); }
    }
    /** 每方最多一段大招。锁点、束缚和多段命中属于规则，暂停/顿帧时不借墙钟偷偷推进。 */
    private final class Ritual {
        final long id=++specialId,born=tick;final int owner;final FighterKind kind;
        final double[] boltX=new double[FightSpecial.BOLT_COUNT],boltY=new double[FightSpecial.BOLT_COUNT];
        final boolean[] aimed=new boolean[FightSpecial.BOLT_COUNT],hit=new boolean[FightSpecial.BOLT_COUNT];
        int age;double x,y;boolean captured,missed;
        Ritual(Fighter caster,Fighter target){owner=caster.player;kind=caster.kind;x=target.x;y=target.y;}
        FightSnapshot.Ultimate view(){
            var strikes=new ArrayList<FightSnapshot.Strike>();
            if(kind==FighterKind.NAIWA){for(int i=0;i<FightSpecial.BOLT_COUNT;i++){strikes.add(new FightSnapshot.Strike(i,boltX[i],boltY[i],age-FightSpecial.boltTick(i),aimed[i],hit[i]));}}
            var caster=fighters[owner];return new FightSnapshot.Ultimate(id,owner,kind,age,caster.x,caster.y,x,y,captured,missed,strikes);
        }
    }
    private static final class Shot {
        final int owner, direction; final FighterKind kind; final boolean ultimate; final double y;
        double x;
        Shot(Fighter f, boolean ultimate) { owner = f.player; direction = f.facing; kind = f.kind; this.ultimate = ultimate; x = f.x + f.facing * 48; y = f.y + 56; }
        FightSnapshot.Projectile view() { return new FightSnapshot.Projectile(owner, kind, x, y, direction, ultimate); }
    }
    private record Hit(int attacker, int defender, int damage, boolean web, double x, double y, int direction, boolean ultimate, boolean heavy,FightSnapshot.Attack attack) { }
    @Override public void start() { if (state == SessionState.READY) { state = SessionState.RUNNING; } }
    @Override public void pause() { inputs.clear(); buffered[0] = buffered[1] = FightCommand.NONE; for (Fighter f : fighters) { f.vx = 0; f.jumpBuffer = 0; f.queuedPunch = false; } if (state == SessionState.RUNNING) { state = SessionState.PAUSED; } }
    @Override public void resume() { if (state == SessionState.PAUSED) { state = SessionState.RUNNING; } }
    @Override public SessionState state() { return state; }
    @Override public long tickNumber() { return tick; }
    @Override public int ticksPerSecond() { return TICKS_PER_SECOND; }
    @Override public List<GameEvent> events() { return events; }
    @Override public boolean submitInput(PlayerInput<FightCommand> input) {
        Objects.requireNonNull(input);
        if (input.player() > 1) { throw new IllegalArgumentException("Unknown fighter"); }
        return state == SessionState.RUNNING && !(bot != null && input.player() == 1) && inputs.offer(input, tick + 1);
    }
    @Override public FightSnapshot snapshot() {
        return new FightSnapshot(tick, state, result, List.of(fighters[0].view(), fighters[1].view()),
                shots.stream().map(Shot::view).toList(), impacts, hitstop, config.map(),
                java.util.Arrays.stream(rituals).filter(Objects::nonNull).map(Ritual::view).toList(),teleports);
    }
    @Override public void tick() {
        if (state != SessionState.RUNNING) { return; }
        FightCommand[] commands = {FightCommand.NONE, FightCommand.NONE};
        for (var input : inputs.drain()) { commands[input.player()] = input.value(); }
        if (bot != null) { commands[1] = bot.command(snapshot(), 1); }
        tick++;
        boolean[] blinkPressed=new boolean[2];
        for (int p = 0; p < 2; p++) {
            var f=fighters[p];
            // 只接受已经格挡后的新跳跃沿；命中硬直/冷却/顿帧中的尝试不进入技能邮箱，不会稍后自动传送。
            if(f.kind==FighterKind.FEIDUDU&&commands[p].guard()&&commands[p].jump()&&(f.action==Action.GUARD||f.action==Action.GUARD_HIT)){
                blinkPressed[p]=hitstop==0&&f.action==Action.GUARD&&f.stun==0&&f.actionTicks==0&&f.grounded&&f.boundBy==0&&f.teleportCooldown==0&&f.energy>=FightSpecial.BLINK_COST;
                commands[p]=withoutJump(commands[p]);buffered[p]=withoutJump(buffered[p]);f.jumpBuffer=0;
            }
            if (hasPulse(commands[p])) { pulseLife[p] = 12; }
            commands[p] = merge(commands[p], buffered[p]); buffered[p] = FightCommand.NONE;
        }
        if (hitstop > 0 && tick < MATCH_TICKS) {
            hitstop--; buffered[0] = commands[0]; buffered[1] = commands[1]; events = List.of(); return;
        }
        List<GameEvent> batch = new ArrayList<>();
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < impacts.size(); i++) {
            impacts.set(i,impacts.get(i).aged());
        }
        impacts.removeIf(v -> v.life() <= 0);
        for(int i=0;i<teleports.size();i++){teleports.set(i,teleports.get(i).aged());}teleports.removeIf(v->v.age()>=FightSpecial.BLINK_VISUAL);
        for (int p = 0; p < 2; p++) { update(fighters[p], fighters[1 - p], commands[p],blinkPressed[p], hits, batch); }
        updateRituals(hits,batch);
        separate();
        for (var iterator = shots.iterator(); iterator.hasNext();) {
            Shot shot = iterator.next(); double previous = shot.x; shot.x += shot.direction * (shot.ultimate ? 28 : shot.kind == FighterKind.NAIWA ? 22 : 18);
            Fighter target = fighters[1 - shot.owner]; double radius = shot.ultimate ? 90 : 22;
            if (Math.max(previous, shot.x) + radius >= target.x - 38 && Math.min(previous, shot.x) - radius <= target.x + 38
                    && shot.y + radius > target.y + 12 && shot.y - radius < target.y + BODY_HEIGHT) {
                hits.add(new Hit(shot.owner, target.player, shot.ultimate ? 4 : shot.kind == FighterKind.NAIWA ? 7 : 6, shot.kind == FighterKind.FEIDUDU,
                        target.x, shot.y, shot.direction, shot.ultimate, false,FightSnapshot.Attack.RANGED)); iterator.remove();
            } else if (shot.x < -100 || shot.x > WIDTH + 100) { iterator.remove(); }
        }
        // 格挡姿态与朝向取本 Tick 命中前的值，先后处理不会令另一方丢失同时命中。
        boolean[] guarding = {isGuard(fighters[0]), isGuard(fighters[1])};
        int[] facing = {fighters[0].facing, fighters[1].facing};
        for (Hit hit : hits) { apply(hit, guarding[hit.defender], facing[hit.defender], batch); }
        if (fighters[0].health <= 0 || fighters[1].health <= 0 || tick >= MATCH_TICKS) {
            state = SessionState.FINISHED; inputs.clear();
            cancelRitual(0);cancelRitual(1);teleports.clear();
            int difference = fighters[0].health - fighters[1].health;
            result = difference == 0 ? FightSnapshot.Result.DRAW : difference > 0 ? FightSnapshot.Result.PLAYER_ONE : FightSnapshot.Result.PLAYER_TWO;
            for (Fighter fighter : fighters) { if (fighter.health == 0) { batch.add(new GameEvent.PlayerDied(tick, fighter.player)); } }
            for (Fighter f : fighters) {
                boolean winner = result == FightSnapshot.Result.PLAYER_ONE && f.player == 0 || result == FightSnapshot.Result.PLAYER_TWO && f.player == 1;
                f.action = result == FightSnapshot.Result.DRAW ? Action.IDLE : winner ? Action.WIN : Action.LOSE;
            }
            batch.add(new GameEvent.MatchEnded(tick, result.name()));
        }
        events = List.copyOf(batch);
    }
    /** 加速度、支撑面和动作均按固定 Tick 推进；跳跃次数只由真正落地重置。 */
    private void update(Fighter f, Fighter target, FightCommand command,boolean blinkPressed, List<Hit> hits, List<GameEvent> batch) {
        if(f.action==Action.LAUGH&&(!f.grounded||command.move()!=0||hasPulse(command)||command.guard())){
            f.actionTicks=0;f.action=Action.IDLE;
        }
        // 出招中收到的下一次短按保留到完整收拳后；不受普通技能 12 步邮箱寿命截断。
        if (FightPunch.from(f.action) != null && f.actionTicks > 0 && command.punch()) {
            f.queuedPunch = true;
            command = withPunch(command, false);
        } else if (f.actionTicks == 0 && f.stun == 0 && f.queuedPunch) {
            command = withPunch(command, true); f.queuedPunch = false;
        }
        boolean initiallyLocked = f.actionTicks > 0 || f.stun > 0 || f.land > 0;
        if (pulseLife[f.player] > 0) { pulseLife[f.player]--; }
        f.poseTick++; f.energy = Math.min(MAX_ENERGY, f.energy + .10);
        if(f.teleportCooldown>0){f.teleportCooldown--;}
        if (f.slow > 0) { f.slow--; } if (f.dashCooldown > 0) { f.dashCooldown--; }
        if (f.chainWindow > 0) { f.chainWindow--; } else if (f.actionTicks == 0) { f.chain = 0; }
        if (++f.comboAge > 45) { f.comboCount = 0; }
        if (f.parry > 0) { f.parry--; }
        if (f.ignoreTicks > 0 && --f.ignoreTicks == 0) { f.ignored = -1; }
        if(f.boundBy!=0){f.action=Action.WEB_BOUND;f.vx=f.vy=0;f.stun=f.actionTicks=f.jumpBuffer=0;f.queuedPunch=false;return;}
        if (command.jump()) { f.jumpBuffer = 6; } else if (f.jumpBuffer > 0) { f.jumpBuffer--; }
        if (f.grounded) { f.coyote = 4; } else if (f.coyote > 0) { f.coyote--; }
        boolean locked = f.actionTicks > 0 || f.stun > 0 || f.land > 0;
        if(blinkPressed){blink(f,target,hits,batch);locked=true;}
        else if (f.stun > 0) {
            f.stun--;
            if (f.action == Action.KNOCKDOWN || f.action == Action.GETUP) { f.action = f.stun > 7 ? Action.KNOCKDOWN : Action.GETUP; }
            else f.action = f.stun > 10 && f.action == Action.HURT_HEAVY ? Action.HURT_HEAVY : f.action == Action.GUARD_HIT ? Action.GUARD_HIT : Action.HURT;
        } else if (f.actionTicks > 0) {
            f.actionTicks--;
            FightPunch boxing = FightPunch.from(f.action);
            int strike = boxing != null ? boxing.duration() - boxing.strikeTick()
                    : switch (f.action) { case AIR_PUNCH -> 9; case UPPERCUT -> 12; default -> -1; };
            if (f.actionTicks == strike) {
                f.attacks++;
                // 连段窗口跟随出拳而非命中；打空同样能切换刺拳、后手直拳和摆拳。
                if (boxing != null) { f.chainWindow = 42; }
                double height = f.action == Action.UPPERCUT ? 190 : f.action == Action.AIR_PUNCH ? 135 : 86;
                if ((target.x - f.x) * f.facing > 0 && Math.abs(target.x - f.x) < 135 && Math.abs(target.y - f.y) < height) {
                    int damage = boxing != null ? boxing.damage() : f.action == Action.UPPERCUT ? 7 : 6;
                    if (f.parry > 0) { damage = (damage * 3 + 1) / 2; }
                    hits.add(new Hit(f.player, target.player, damage, false, target.x, target.y + 60, f.facing, false,
                        f.action == Action.PUNCH_3 || f.action == Action.UPPERCUT,attack(f.action)));
                }
            }
            if (f.action == Action.RANGED && f.actionTicks == 18) { fire(f, false, batch); }
            if (f.action == Action.ULTIMATE && f.poseTick>=FightSpecial.CHARGE) { f.action = Action.ULT_RELEASE; }
        } else if (f.land > 0) { f.land--; f.action = command.guard() ? Action.GUARD : Action.LAND; }
        else {
            if (Math.abs(target.x - f.x) > 1) { f.facing = target.x > f.x ? 1 : -1; }
            if (command.down() && command.jump() && f.grounded && f.platform >= 0) {
                f.ignored = f.platform; f.ignoreTicks = 16; f.platform = -1; f.grounded = false; f.coyote = 0; f.jumps = 1; f.y -= 2; f.vy = -1; f.jumpBuffer = 0;
                f.action=Action.JUMP_FALL;f.poseTick=0;
            } else if (command.punch()) {
                boolean ordinary = f.grounded && !command.jump();
                f.chain = ordinary ? f.chainWindow > 0 ? f.chain % 3 + 1 : 1 : 0;
                Action punch = !f.grounded ? Action.AIR_PUNCH : command.jump() ? Action.UPPERCUT : f.chain == 2 ? Action.PUNCH_2 : f.chain == 3 ? Action.PUNCH_3 : Action.PUNCH;
                FightPunch boxing = FightPunch.from(punch);
                if (!ordinary) { f.chainWindow = 0; }
                begin(f, punch, boxing != null ? boxing.duration() : 16, 0); locked = true;
            } else if (command.ultimate() && f.energy >= ULTIMATE_COST) { begin(f, Action.ULTIMATE, FightSpecial.DURATION, ULTIMATE_COST);rituals[f.player]=new Ritual(f,target);locked = true; }
            else if (command.ranged() && f.energy >= RANGED_COST) { begin(f, Action.RANGED, 30, RANGED_COST); locked = true; }
            else if (command.dash() && f.energy >= 12 && f.dashCooldown == 0) {
                if (command.move() != 0) { f.facing = command.move(); }
                begin(f, Action.DASH, 12, 12); f.dashCooldown = 42; locked = true;
            } else if (command.guard() && f.grounded && f.energy >= .25 && !command.jump()) {
                f.action = Action.GUARD; f.energy = Math.max(0, f.energy - .25); locked = true;
            } else if(command.laugh()&&f.kind==FighterKind.NAIWA&&f.grounded&&command.move()==0&&!command.guard()&&!hasPulse(command)){
                begin(f,Action.LAUGH,LAUGH_TICKS,0);f.vx=0;f.runTicks=0;f.queuedPunch=false;f.chain=f.chainWindow=0;locked=true;
            }
            if (!locked && f.jumpBuffer > 0 && (f.grounded || f.coyote > 0 || f.jumps < 2)) {
                boolean first = f.grounded || f.coyote > 0;
                f.vy = first ? JUMP_VY : DOUBLE_JUMP_VY; f.jumps = first ? 1 : 2;
                f.grounded = false; f.platform = -1; f.coyote = 0; f.jumpBuffer = 0;
                f.action = first ? Action.JUMP_UP : Action.DOUBLE_JUMP; if (!first) { f.actionTicks = 7; }
            }
        }
        boolean reverse = command.move() != 0 && f.vx * command.move() < -.1;
        if (f.action == Action.DASH && f.actionTicks > 0) { f.vx = approach(f.vx, f.facing * 18, 3); }
        else if (locked) { f.vx = approach(f.vx, 0, f.grounded ? 1.6 : .12); }
        else if (reverse && f.grounded) { f.vx = approach(f.vx, 0, 2.8); f.action = Action.SKID; f.runTicks = 0; }
        else {
            if (command.move() != 0 && command.move() == f.lastMove) { f.runTicks++; } else { f.runTicks = command.move() == 0 ? 0 : 1; }
            double limit = f.grounded ? f.runTicks >= RUN_HOLD ? RUN_MAX : WALK_MAX : AIR_MAX;
            if (f.slow > 0) { limit *= .60; }
            f.vx = approach(f.vx, command.move() * limit, command.move() == 0 ? f.grounded ? 2.1 : .12 : f.grounded ? .62 : .34);
            if (f.grounded) { f.action = command.move() == 0 ? Math.abs(f.vx) > .1 ? Action.SKID : Action.IDLE : f.runTicks >= RUN_HOLD ? Action.RUN : Action.WALK; }
            if (command.move() != 0) { f.facing = command.move(); }
        }
        if (initiallyLocked && hasPulse(command) && pulseLife[f.player] > 0) { buffered[f.player] = command; }
        f.lastMove = command.move();
        double oldX = f.x; f.x = clamp(f.x + f.vx, 60, WIDTH - 60); f.travel += Math.abs(f.x - oldX);
        if (f.grounded && f.platform >= 0 && !config.map().platforms().get(f.platform).contains(f.x)) { f.grounded = false; f.platform = -1; }
        if (!f.grounded) {
            double oldY = f.y; f.y += f.vy; f.vy = Math.max(-16, f.vy - GRAVITY);
            int support = FightArena.landing(config.map().platforms(), f.x, oldY, f.y, f.ignored);
            if (support >= 0 || f.y <= 0) {
                f.y = support >= 0 ? config.map().platforms().get(support).height() : 0; f.platform = support;
                f.grounded = true; f.vy = 0; f.jumps = 0; f.land = f.jumpBuffer > 0 ? 0 : 5;
                if (f.knockdown && f.health > 0) { f.knockdown = false; f.stun = 18; f.action = Action.KNOCKDOWN; }
                else if (f.actionTicks == 0 && f.stun == 0) { f.action = Action.LAND; }
            } else if (!locked && f.actionTicks == 0 && f.stun == 0) { f.action = f.vy > 2 ? Action.JUMP_UP : f.vy < -2 ? Action.JUMP_FALL : Action.JUMP_APEX; }
        }
    }
    private void fire(Fighter f, boolean ultimate, List<GameEvent> batch) {
        shots.add(new Shot(f, ultimate)); f.attacks++;
        batch.add(new GameEvent.Effect(tick, GameEvent.Effect.Kind.SHOT, f.player, f.x, f.y + 56, f.facing > 0 ? 0 : 180));
    }
    private static FightSnapshot.Attack attack(Action action){return switch(action){case PUNCH->FightSnapshot.Attack.JAB;case PUNCH_2->FightSnapshot.Attack.CROSS;case PUNCH_3->FightSnapshot.Attack.HOOK;default->FightSnapshot.Attack.UPPERCUT;};}
    private static FightCommand withoutJump(FightCommand c){return new FightCommand(c.move(),false,c.punch(),c.ranged(),c.ultimate(),c.guard(),c.dash(),c.down(),c.laugh());}
    /** 瞬间落在目标背侧，边界不足则换到另一侧；不穿出地图、不重叠身体、不重置空中跳跃预算。 */
    private void blink(Fighter f,Fighter target,List<Hit> hits,List<GameEvent> batch){
        double oldX=f.x,oldY=f.y,destination=target.x-target.facing*BODY_SPACING;
        if(destination<60||destination>WIDTH-60){destination=target.x+target.facing*BODY_SPACING;}
        f.x=clamp(destination,60,WIDTH-60);f.y=target.y;f.facing=target.x>f.x?1:-1;f.vx=f.vy=0;f.travel=0;
        f.grounded=target.grounded;f.platform=target.platform;f.ignored=-1;f.ignoreTicks=f.coyote=f.jumpBuffer=f.land=0;f.jumps=f.grounded?0:1;
        if(f.grounded&&f.platform>=0&&!config.map().platforms().get(f.platform).contains(f.x)){f.grounded=false;f.platform=-1;f.jumps=1;}
        begin(f,Action.TELEPORT,FightSpecial.BLINK_RECOVERY,FightSpecial.BLINK_COST);f.teleportCooldown=FightSpecial.BLINK_COOLDOWN;f.queuedPunch=false;f.chain=f.chainWindow=0;f.attacks++;
        teleports.add(new FightSnapshot.Teleport(++specialId,f.player,oldX,oldY,f.x,f.y,f.facing,0));
        hits.add(new Hit(f.player,target.player,FightSpecial.BLINK_DAMAGE,false,target.x,target.y+55,f.facing,false,true,FightSnapshot.Attack.TELEPORT));
        batch.add(new GameEvent.Effect(tick,GameEvent.Effect.Kind.SHOT,f.player,f.x,f.y+50,f.facing>0?0:180));
    }
    private void cancelRitual(int owner){
        var r=rituals[owner];if(r==null){return;}var target=fighters[1-owner];if(target.boundBy==r.id){target.boundBy=0;target.jumpBuffer=0;}
        rituals[owner]=null;
    }
    /** 雷电逐次提前锁点，允许离开预警区；蛛丝只有实际命中才束缚，爆裂之前不伪造扣血。 */
    private void updateRituals(List<Hit> hits,List<GameEvent> batch){
        for(int owner=0;owner<2;owner++){
            var r=rituals[owner];if(r==null||r.born==tick){continue;}r.age++;var caster=fighters[owner];var target=fighters[1-owner];
            int direction=target.x>=caster.x?1:-1;
            if(r.age<FightSpecial.CHARGE){r.x=target.x;r.y=target.y;}
            if(r.kind==FighterKind.NAIWA){
                for(int i=0;i<FightSpecial.BOLT_COUNT;i++){
                    int strike=FightSpecial.boltTick(i);
                    if(r.age==strike-FightSpecial.BOLT_AIM){r.aimed[i]=true;r.boltX[i]=target.x;r.boltY[i]=target.y;}
                    if(r.age==strike){caster.attacks++;r.hit[i]=Math.abs(target.x-r.boltX[i])<=90&&Math.abs(target.y-r.boltY[i])<=100;
                        if(r.hit[i]){hits.add(new Hit(owner,1-owner,FightSpecial.BOLT_DAMAGE,false,r.boltX[i],r.boltY[i]+55,direction,true,false,FightSnapshot.Attack.ULTIMATE));}}
                }
            }else{
                if(r.age==FightSpecial.WEB_AIM){r.x=target.x;r.y=target.y;}
                if(r.age==FightSpecial.WEB_CAPTURE){
                    boolean overlaps=Math.abs(target.x-r.x)<=125&&Math.abs(target.y-r.y)<=110;
                    boolean shield=overlaps&&isGuard(target)&&target.facing==-direction&&target.energy>=GUARD_COST;
                    if(overlaps&&!shield&&target.boundBy==0){r.captured=true;r.x=target.x;r.y=target.y;target.boundBy=r.id;target.vx=target.vy=0;target.action=Action.WEB_BOUND;target.poseTick=target.actionTicks=target.stun=target.jumpBuffer=0;target.queuedPunch=false;target.knockdown=false;
                        var interrupted=rituals[target.player];if(interrupted!=null&&interrupted.age<FightSpecial.CHARGE){cancelRitual(target.player);}}
                    else{r.missed=true;if(shield){caster.attacks++;hits.add(new Hit(owner,1-owner,4,false,target.x,target.y+55,direction,true,false,FightSnapshot.Attack.ULTIMATE));}}
                }
                if(r.age==FightSpecial.WEB_BURST&&r.captured&&target.boundBy==r.id){
                    target.boundBy=0;target.jumpBuffer=0;caster.attacks++;hits.add(new Hit(owner,1-owner,FightSpecial.WEB_DAMAGE,false,target.x,target.y+55,direction,true,true,FightSnapshot.Attack.ULTIMATE));
                }
            }
            if(r.age>=FightSpecial.DURATION){cancelRitual(owner);}
        }
    }
    private static boolean isGuard(Fighter f) { return f.action == Action.GUARD || f.action == Action.GUARD_HIT; }
    private static boolean hasPulse(FightCommand c) { return c.jump() || c.punch() || c.ranged() || c.ultimate() || c.dash(); }
    /** 只消费普通拳按键，方向和其他技能仍沿用原来的输入处理。 */
    private static FightCommand withPunch(FightCommand c, boolean punch) {
        return new FightCommand(c.move(), c.jump(), punch, c.ranged(), c.ultimate(), c.guard(), c.dash(), c.down(),c.laugh());
    }
    private static FightCommand merge(FightCommand current, FightCommand saved) {
        return new FightCommand(current.move(), current.jump() || saved.jump(), current.punch() || saved.punch(),
            current.ranged() || saved.ranged(), current.ultimate() || saved.ultimate(), current.guard(), current.dash() || saved.dash(), current.down() || saved.jump() && saved.down(),current.laugh());
    }
    private static double approach(double current, double target, double amount) {
        if (Math.abs(target - current) <= amount + 1e-9) { return target; }
        return current < target ? Math.min(target, current + amount) : Math.max(target, current - amount);
    }
    private static void begin(Fighter f, Action action, int ticks, int cost) {
        f.action = action; f.actionTicks = ticks; f.energy -= cost; f.poseTick = 0;
    }
    private void separate() {
        Fighter a = fighters[0], b = fighters[1];
        if (Math.abs(a.y - b.y) > BODY_HEIGHT - 20 || Math.abs(a.x - b.x) >= BODY_SPACING) { return; }
        double half = BODY_SPACING / 2;
        int sign = a.x <= b.x ? 1 : -1; double center = clamp((a.x + b.x) / 2, 60 + half, WIDTH - 60 - half);
        if(a.boundBy!=0){b.x=clamp(a.x+sign*BODY_SPACING,60,WIDTH-60);return;}
        if(b.boundBy!=0){a.x=clamp(b.x-sign*BODY_SPACING,60,WIDTH-60);return;}
        a.x = center - sign * half; b.x = center + sign * half;
    }
    private void apply(Hit hit, boolean guard, int facing, List<GameEvent> batch) {
        Fighter target = fighters[hit.defender], source = fighters[hit.attacker];
        boolean blocked = hit.attack!=FightSnapshot.Attack.TELEPORT&&guard && facing == -hit.direction && target.energy >= GUARD_COST;
        int damage = blocked ? Math.max(1, hit.damage / 4) : hit.damage;
        if (!blocked && !hit.ultimate && source.comboCount >= 3) { damage = Math.max(1, (int) Math.round(damage * .8)); }
        int old = target.health; target.health = Math.max(0, old - damage);
        source.hits++; source.totalDamage += old - target.health;
        if (!blocked) { source.comboCount++; source.comboAge = 0; source.maxCombo = Math.max(source.maxCombo, source.comboCount); }
        else { target.parry = 12; }
        hitstop = Math.max(hitstop, blocked ? 2 : hit.heavy ? 5 : 3);
        target.energy = Math.min(MAX_ENERGY, Math.max(0, target.energy - (blocked ? GUARD_COST : 0)) + (blocked ? 0 : 5));
        source.energy = Math.min(MAX_ENERGY, source.energy + (blocked ? 2 : hit.ultimate ? 0 : 7));
        target.stun = blocked ? 4 : hit.ultimate ? 8 : hit.heavy ? 18 : 10;
        target.queuedPunch = false;
        var interrupted=rituals[target.player];if(interrupted!=null&&interrupted.age<FightSpecial.CHARGE){cancelRitual(target.player);}
        target.actionTicks = 0; target.action = blocked ? Action.GUARD_HIT : hit.heavy ? Action.HURT_HEAVY : Action.HURT;
        if(target.boundBy==0){
            if (hit.heavy && !blocked) { target.knockdown = true; target.vy = 6; target.grounded = false; target.platform = -1; }
            if(hit.attack==FightSnapshot.Attack.TELEPORT){target.vx=hit.direction*10;target.vy=7;}
            else if(hit.ultimate&&hit.heavy&&!blocked){target.vx=hit.direction*8;}
            target.x = clamp(target.x + hit.direction * (blocked ? 4 : hit.heavy ? 30 : 12), 60, WIDTH - 60);
        }
        if (hit.web) { target.slow = blocked ? 20 : 90; }
        impacts.add(new FightSnapshot.Impact(++impactId, hit.x, hit.y, blocked, source.kind, 24, old - target.health, hit.direction, hit.ultimate,hit.attacker,hit.defender,hit.attack));
        batch.add(new GameEvent.HealthChanged(tick, target.player, old, target.health));
        batch.add(new GameEvent.Effect(tick, GameEvent.Effect.Kind.HIT, target.player, hit.x, hit.y, hit.direction > 0 ? 0 : 180));
    }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
