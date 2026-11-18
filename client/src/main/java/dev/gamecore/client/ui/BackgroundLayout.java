package dev.gamecore.client.ui;

/** 纯 Java 的背景 cover 几何；零尺寸窗口不绘制，正常尺寸保持比例并居中裁切。 */
public final class BackgroundLayout {
    private BackgroundLayout() { }
    public record Rect(float x, float y, float width, float height) { }

    public static Rect cover(float imageWidth, float imageHeight, float width, float height) {
        if (!Float.isFinite(imageWidth) || !Float.isFinite(imageHeight) || imageWidth <= 0 || imageHeight <= 0
                || !Float.isFinite(width) || !Float.isFinite(height) || width < 0 || height < 0) {
            throw new IllegalArgumentException("Invalid background extent");
        }
        if (width == 0 || height == 0) { return new Rect(0, 0, 0, 0); }
        float scale = Math.max(width / imageWidth, height / imageHeight);
        float drawnWidth = imageWidth * scale, drawnHeight = imageHeight * scale;
        return new Rect((width - drawnWidth) / 2, (height - drawnHeight) / 2, drawnWidth, drawnHeight);
    }
}
