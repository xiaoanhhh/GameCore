package dev.gamecore.snake;

/** 一次 Tick 只沿一个轴移动一格；禁止直接反向。 */
public enum Direction {
    UP(0, 1), DOWN(0, -1), LEFT(-1, 0), RIGHT(1, 0);
    private final int dx;
    private final int dy;
    Direction(int dx, int dy) { this.dx = dx; this.dy = dy; }
    public int dx() { return dx; }
    public int dy() { return dy; }
    public boolean opposite(Direction other) { return dx == -other.dx && dy == -other.dy; }
}
