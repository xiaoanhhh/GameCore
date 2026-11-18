package dev.gamecore.fight;

/** 每 Tick 的完整输入：移动/格挡持续，跳跃与技能由客户端锁存按下沿；缺席输入视为松开。 */
public record FightCommand(int move, boolean jump, boolean punch, boolean ranged, boolean ultimate,
                           boolean guard, boolean dash, boolean down, boolean laugh) {
    /** 旧八参数输入没有娱乐动作，保持既有调用与默认行为。 */
    public FightCommand(int move, boolean jump, boolean punch, boolean ranged, boolean ultimate, boolean guard, boolean dash, boolean down) {
        this(move,jump,punch,ranged,ultimate,guard,dash,down,false);
    }
    /** 兼容原调用方；下蹲/穿台与格挡共用方向下键，只有和跳跃一起才触发穿台。 */
    public FightCommand(int move, boolean jump, boolean punch, boolean ranged, boolean ultimate, boolean guard, boolean dash) {
        this(move, jump, punch, ranged, ultimate, guard, dash, guard);
    }
    public static final FightCommand NONE = new FightCommand(0, false, false, false, false, false, false);
    public FightCommand withLaugh(boolean value){return new FightCommand(move,jump,punch,ranged,ultimate,guard,dash,down,value);}
    public FightCommand {
        if (move < -1 || move > 1) { throw new IllegalArgumentException("move must be -1..1"); }
    }
}
