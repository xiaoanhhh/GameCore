package dev.gamecore.api.session;

import dev.gamecore.api.event.GameEvent;
import java.util.List;
/**
 * 原则：统一游戏运行方式，同时保留各款游戏的规则差异。
 */
/**
 * 单线程、无 IO 的模拟契约；输入/快照保持各游戏类型，不统一实体、碰撞或渲染。
 * tickNumber 为已完成的模拟步数（并非 Snake 格移动次数）；暂停与终局 tick 为无操作。
 * 输入只允许下一 Tick；true 仅表示进入队列，规则仍可忽略非法转弯等命令。
 * events 是最近一个已完成 Tick 的不可变批次，由外部 SessionLoop 每步发布一次。
 */

//一局游戏对外提供的操作
/**
 * I,S为泛型
 * 该GameSession为接口定义
 * 对于I：坦克输入是油门、转向、开火；贪吃蛇输入是方向；格斗输入是移动、跳跃、出拳等
 * TankSession
 *     implements GameSession<TankCommand, TankSnapshot>
 * SnakeGameSession
 *     implements GameSession<Direction, SnakeSnapshot>
 * FightSession
 *     implements GameSession<FightCommand, FightSnapshot>
 */
public interface GameSession<I, S> {
    //这局游戏处于什么生命周期？
    void start();
    void pause();
    void resume();

    boolean submitInput(PlayerInput<I> input);   //玩家希望做什么
    void tick();                                 //游戏规则向前计算一步后发生了什么？
    S snapshot();                                //外部现在可以看到什么状态？
    SessionState state();
    long tickNumber();
    int ticksPerSecond();
    List<GameEvent> events();                    //这一步发生了哪些事情？
    default boolean isFinished() { return state() == SessionState.FINISHED; }
}
