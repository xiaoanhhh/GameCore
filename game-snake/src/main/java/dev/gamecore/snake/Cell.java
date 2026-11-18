package dev.gamecore.snake;

/** 网格坐标，原点位于左下；规则不使用像素或图形向量。 */
public record Cell(int x, int y) {
    public Cell move(Direction direction) { return new Cell(x + direction.dx(), y + direction.dy()); }
}
