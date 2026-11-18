package dev.gamecore.tank;

public record TankCommand(int throttle, int turn, boolean fire) {
    public static final TankCommand IDLE = new TankCommand(0, 0, false);
    public TankCommand {
        if (Math.abs((long) throttle) > 1 || Math.abs((long) turn) > 1) { throw new IllegalArgumentException("Invalid command axis"); }
    }
}
/**
 * 该Java Record 表示坦克每一帧（每 Tick）的控制指令，只存指令数据，不直接操作游戏世界
 * throttle:油门方向，1向前，0向后
 * turn:转向，1转向，0不转向
 * fire:开火，true开，false不开
 * public TankCommand 在自动生成的全参构造之前执行校验.
 */
