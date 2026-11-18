package dev.gamecore.lwjgl3;

/**
 * 不依赖图形环境的窗口启动参数。提前拒绝非法值，避免进入本地库后才报错。
 * 属于桌面适配层，不作为未来游戏规则的公共接口。
 */
public record DesktopSettings(String title, int width, int height, int foregroundFps) {
    public DesktopSettings {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Window title must not be blank");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Window dimensions must be positive");
        }
        if (foregroundFps <= 0) {
            throw new IllegalArgumentException("Foreground FPS must be positive");
        }
    }

    /** 本步骤使用固定默认值；设置页面和持久化配置在后续功能中实现。 */
    public static DesktopSettings defaults() {
        return new DesktopSettings("GameCore", 1280, 720, 60);
    }
}
