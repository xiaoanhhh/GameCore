package dev.gamecore.tank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** 四邻接 A*；曼哈顿启发，按 f、h、格编号稳定打破平局；只读逻辑地图，返回不含起点的格中心路线。 */
public final class GridPathfinder {
    private record Node(int cell, int cost, int heuristic) { }
    private GridPathfinder() { }
    public static List<Vec2> find(TankMap map, Vec2 start, Vec2 goal) {
        int sx = (int) start.x(), sy = (int) start.y(), gx = (int) goal.x(), gy = (int) goal.y();
        if (map.wall(sx, sy) || map.wall(gx, gy)) { return List.of(); }
        int from = sy * map.width() + sx, to = gy * map.width() + gx;
        int[] cost = new int[map.width() * map.height()], parent = new int[cost.length];
        Arrays.fill(cost, Integer.MAX_VALUE); Arrays.fill(parent, -1); cost[from] = 0;
        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.<Node>comparingInt(n -> n.cost + n.heuristic)
                .thenComparingInt(Node::heuristic).thenComparingInt(Node::cell));
        queue.add(new Node(from, 0, Math.abs(sx - gx) + Math.abs(sy - gy)));
        int[][] neighbors = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        while (!queue.isEmpty()) {
            Node node = queue.remove();
            if (node.cost != cost[node.cell]) { continue; }
            if (node.cell == to) {
                List<Vec2> path = new ArrayList<>();
                for (int cell = to; cell != from; cell = parent[cell]) { path.add(new Vec2(cell % map.width() + .5, cell / map.width() + .5)); }
                Collections.reverse(path); return List.copyOf(path);
            }
            int x = node.cell % map.width(), y = node.cell / map.width();
            for (int[] offset : neighbors) {
                int nx = x + offset[0], ny = y + offset[1];
                if (map.wall(nx, ny)) { continue; }
                int next = ny * map.width() + nx, nextCost = node.cost + 1;
                if (nextCost >= cost[next]) { continue; }
                cost[next] = nextCost; parent[next] = node.cell;
                queue.add(new Node(next, nextCost, Math.abs(nx - gx) + Math.abs(ny - gy)));
            }
        }
        return List.of();
    }
}
