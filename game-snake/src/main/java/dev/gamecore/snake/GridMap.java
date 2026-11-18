package dev.gamecore.snake;

import java.util.HashSet;
import java.util.Set;

/** 固定地图及边界策略，障碍集合不可变；不携带窗口尺寸、纹理或玩家状态。 */
final class GridMap {
    static final int SIZE = 20;
    private final Set<Cell> obstacles;
    GridMap(Set<Cell> obstacles) { this.obstacles = Set.copyOf(obstacles); }
    Set<Cell> obstacles() { return obstacles; }
    boolean contains(Cell cell) { return cell.x() >= 0 && cell.x() < SIZE && cell.y() >= 0 && cell.y() < SIZE; }
    static GridMap forMode(SnakeMode mode) {
        Set<Cell> cells = new HashSet<>();
        if (mode == SnakeMode.OBSTACLES) {
            for (int x : new int[]{6, 13}) {
                for (int y = 5; y <= 14; y++) { if (y != 9 && y != 10) { cells.add(new Cell(x, y)); } }
            }
        }
        return new GridMap(cells);
    }
}
