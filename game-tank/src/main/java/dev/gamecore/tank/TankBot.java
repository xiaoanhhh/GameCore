package dev.gamecore.tank;

/** 策略边界：只接受不可变观察，返回与人类完全相同的输入命令，不持有可变世界引用。 */
public interface TankBot {
    enum Level { EASY, NORMAL, HARD }
    enum State { PATROL, CHASE, ATTACK, EVADE }
    TankCommand command(TankSnapshot view, int player);
    State state();
}
