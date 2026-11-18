package dev.gamecore.fight;

/** 地面拳击的共享时序：规则决定命中，客户端据同一时间表绘制蓄势、击出和收拳。 */
public enum FightPunch {
    JAB(FightSnapshot.Action.PUNCH, 16, 5, 5),
    CROSS(FightSnapshot.Action.PUNCH_2, 18, 6, 6),
    HOOK(FightSnapshot.Action.PUNCH_3, 24, 8, 8);

    private final FightSnapshot.Action action;
    private final int duration, strikeTick, damage;
    FightPunch(FightSnapshot.Action action, int duration, int strikeTick, int damage) {
        this.action = action; this.duration = duration; this.strikeTick = strikeTick; this.damage = damage;
    }
    public FightSnapshot.Action action() { return action; }
    public int duration() { return duration; }
    public int strikeTick() { return strikeTick; }
    public int damage() { return damage; }
    /** 非地面普通拳返回 null，避免空中拳/上勾拳误用拳击图集或连段缓存。 */
    public static FightPunch from(FightSnapshot.Action action) {
        return switch (action) { case PUNCH -> JAB; case PUNCH_2 -> CROSS; case PUNCH_3 -> HOOK; default -> null; };
    }
}
