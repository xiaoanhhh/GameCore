package dev.gamecore.snake;

import java.util.List;
import java.util.Set;

/** 深度不可变的渲染/断言快照，不暴露模拟内部集合；food 在填满棋盘时为 null。 */
public record SnakeSnapshot(SnakeMode mode, SnakeGame.State state, SnakeGame.Result result,
                            long tick, List<Player> players, Set<Cell> obstacles, Cell food, long simulationTick, int movementCredit) {
    /** 规则层/成绩构造只描述世界；会话输出另填真实模拟 Tick 和移动相位。 */
    public SnakeSnapshot(SnakeMode mode, SnakeGame.State state, SnakeGame.Result result, long tick,
                         List<Player> players, Set<Cell> obstacles, Cell food) {
        this(mode, state, result, tick, players, obstacles, food, 0, 0);
    }
    public SnakeSnapshot { players = List.copyOf(players); obstacles = Set.copyOf(obstacles); }
    public record Player(List<Cell> body, Direction direction, int score, SnakeGame.Collision collision) {
        public Player { body = List.copyOf(body); }
        public boolean alive() { return collision == SnakeGame.Collision.NONE; }
        public Cell head() { return body.getFirst(); }
    }
}
