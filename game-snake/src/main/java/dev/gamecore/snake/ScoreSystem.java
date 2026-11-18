package dev.gamecore.snake;

import java.util.List;

/** 纯规则计分与速度策略：每颗 +10，每累计三颗加 2 格/秒，双人按共享食物总数一起加速。 */
public final class ScoreSystem {
    public static final int FOOD_POINTS = 10;
    public static final int INITIAL_SPEED = 10;
    public static final int MAX_SPEED = 20;
    private ScoreSystem() { }
    public static int movementRate(List<SnakeSnapshot.Player> players) {
        int total = players.stream().mapToInt(SnakeSnapshot.Player::score).sum();
        return INITIAL_SPEED + Math.min(5, total / (3 * FOOD_POINTS)) * 2;
    }
}
