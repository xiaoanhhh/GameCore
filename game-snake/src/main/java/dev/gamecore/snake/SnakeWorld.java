package dev.gamecore.snake;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** 一局的空间状态：地图、蛇、食物与独立随机序列。食物只有格坐标，用 Cell 表示而不增加空壳类型。 */
final class SnakeWorld {
    final GridMap map;
    final List<SnakePlayer> players;
    Cell food;
    private final Random random;
    SnakeWorld(long seed, List<SnakeSnapshot.Player> players, GridMap map, Cell initialFood) {
        this.map = map; random = new Random(seed);
        this.players = players.stream().map(SnakePlayer::new).toList();
        food = initialFood == null ? spawnFood() : initialFood;
    }
    boolean move() {
        List<Cell> oldHeads = players.stream().map(player -> player.body.getFirst()).toList();
        boolean[] growing = new boolean[players.size()];
        for (int i = 0; i < players.size(); i++) { growing[i] = players.get(i).move(food); }
        CollisionRules.resolve(map, players, oldHeads);
        boolean eaten = false;
        for (int i = 0; i < players.size(); i++) {
            SnakePlayer player = players.get(i);
            if (growing[i] && player.collision == SnakeGame.Collision.NONE) { player.score += ScoreSystem.FOOD_POINTS; eaten = true; }
        }
        return eaten;
    }
    /** 稳定坐标顺序枚举空格再抽取，满盘返回 null，不使用拒绝采样。 */
    Cell spawnFood() {
        Set<Cell> occupied = new HashSet<>(map.obstacles());
        players.forEach(player -> occupied.addAll(player.body));
        List<Cell> free = new ArrayList<>();
        for (int y = 0; y < GridMap.SIZE; y++) {
            for (int x = 0; x < GridMap.SIZE; x++) { Cell cell = new Cell(x, y); if (!occupied.contains(cell)) { free.add(cell); } }
        }
        return free.isEmpty() ? null : free.get(random.nextInt(free.size()));
    }
    List<SnakeSnapshot.Player> snapshots() { return players.stream().map(SnakePlayer::snapshot).toList(); }
}
