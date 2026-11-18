package dev.gamecore.client.snake;

/** 纯 Java 棋盘布局：保留紧凑操作区后，在窗口内放入最大的正方形，不拉伸 20×20 网格。 */
public final class SnakeArenaLayout {
    public static final float PADDING = 12;
    public static final float GAP = 12;
    public static final float CONTROLS_WIDTH = 280;
    private SnakeArenaLayout() { }
    public record Rect(float x, float y, float width, float height) { }
    public record Layout(Rect board, Rect controls) { }
    public static Layout calculate(float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height)) { throw new IllegalArgumentException("Invalid extent"); }
        float side = Math.min(width - 2 * PADDING - GAP - CONTROLS_WIDTH, height - 2 * PADDING);
        if (side <= 0) { throw new IllegalArgumentException("Arena is too small"); }
        float left = (width - side - GAP - CONTROLS_WIDTH) / 2;
        return new Layout(new Rect(left, (height - side) / 2, side, side),
                new Rect(left + side + GAP, PADDING, CONTROLS_WIDTH, height - 2 * PADDING));
    }
}
