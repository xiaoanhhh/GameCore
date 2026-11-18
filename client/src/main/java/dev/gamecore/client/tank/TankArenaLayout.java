package dev.gamecore.client.tank;

/** 纯 Java 布局计算；地图保持格子比例并居中，操作区固定逻辑宽度，窄屏由视口扩展保留完整内容。 */
public final class TankArenaLayout {
    public record Rect(float x, float y, float width, float height) { }
    public record Layout(Rect board, Rect controls) { }
    private TankArenaLayout() { }
    public static Layout calculate(float width, float height, int columns, int rows) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || columns <= 0 || rows <= 0 || width <= 344 || height <= 24) {
            throw new IllegalArgumentException("Invalid arena extent");
        }
        float available = width - 332;
        float cell = Math.min(available / columns, (height - 24) / rows);
        return new Layout(new Rect(12 + (available - columns * cell) / 2, (height - rows * cell) / 2, columns * cell, rows * cell),
                new Rect(width - 308, 12, 296, height - 24));
    }
}
