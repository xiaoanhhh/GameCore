package dev.gamecore.client.ui;

/** 根据实际 framebuffer 像素决定字体栅格密度，保持 1280×720 的布局单位不变。 */
public final class UiFontScale {
    public static final int DESIGN_WIDTH = 1280;
    public static final int DESIGN_HEIGHT = 720;
    public static final float MAX_DENSITY = 4f;
    private static final float SETTLE_SECONDS = .15f;
    private float density;
    private float pending;
    private float stableSeconds;

    public UiFontScale(float initialDensity) {
        density = initialDensity;
        pending = initialDensity;
    }

    /** 以四分之一档向上取整，避免每拖动一个像素都重建图集；上限约束显存开销。 */
    public static float densityFor(int backBufferWidth, int backBufferHeight) {
        if (backBufferWidth <= 0 || backBufferHeight <= 0) { return 1f; }
        double scale = Math.min(backBufferWidth / (double) DESIGN_WIDTH, backBufferHeight / (double) DESIGN_HEIGHT);
        return (float) Math.max(1, Math.min(MAX_DENSITY, Math.ceil(scale * 4) / 4));
    }

    public static int rasterSize(int logicalSize, float density) {
        if (logicalSize <= 0 || !Float.isFinite(density) || density < 1 || density > MAX_DENSITY) {
            throw new IllegalArgumentException("Invalid logical font size or raster density");
        }
        return (int) Math.ceil(logicalSize * (double) density);
    }

    /** 窗口尺寸稳定片刻后才切档；最小化期间不产生字体重建。 */
    public float advance(int backBufferWidth, int backBufferHeight, float delta) {
        if (backBufferWidth <= 0 || backBufferHeight <= 0) { return density; }
        float desired = densityFor(backBufferWidth, backBufferHeight);
        if (desired == density) {
            pending = desired;
            stableSeconds = 0;
            return density;
        }
        if (desired != pending) {
            pending = desired;
            stableSeconds = 0;
        }
        if (Float.isFinite(delta) && delta > 0) { stableSeconds += Math.min(delta, .1f); }
        if (stableSeconds >= SETTLE_SECONDS) {
            density = desired;
            stableSeconds = 0;
        }
        return density;
    }
}
