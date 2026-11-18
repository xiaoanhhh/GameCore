package dev.gamecore.snake;

import java.util.List;

/** 在全部蛇移动后统一判定，保留旧头用于识别头部交换；判定不依赖玩家遍历顺序。 */
final class CollisionRules {
    private CollisionRules() { }
    static void resolve(GridMap map, List<SnakePlayer> players, List<Cell> oldHeads) {
        for (int i = 0; i < players.size(); i++) {
            SnakePlayer snake = players.get(i);
            Cell head = snake.body.getFirst();
            if (!map.contains(head)) { snake.collision = SnakeGame.Collision.WALL; }
            else if (map.obstacles().contains(head)) { snake.collision = SnakeGame.Collision.OBSTACLE; }
            else if (snake.body.subList(1, snake.body.size()).contains(head)) { snake.collision = SnakeGame.Collision.SELF; }
            for (int j = 0; j < players.size(); j++) {
                if (i == j) { continue; }
                SnakePlayer other = players.get(j);
                if (head.equals(other.body.getFirst())) { snake.collision = SnakeGame.Collision.HEAD_ON; }
                else if (head.equals(oldHeads.get(j)) && other.body.getFirst().equals(oldHeads.get(i))) {
                    snake.collision = SnakeGame.Collision.HEAD_SWAP;
                } else if (snake.collision == SnakeGame.Collision.NONE && other.body.subList(1, other.body.size()).contains(head)) {
                    snake.collision = SnakeGame.Collision.OTHER;
                }
            }
        }
    }
}
