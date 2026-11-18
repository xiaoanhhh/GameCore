package dev.gamecore.client.settings;

import java.util.Objects;

/** 已应用设置的不可变快照；界面草稿不直接修改当前生效值。 */
public record ClientSettings(int volume, Resolution resolution, boolean vsync, KeyBindings keys) {
    public enum Resolution {
        SMALL(960, 540), MEDIUM(1280, 720), LARGE(1600, 900), FULL_HD(1920, 1080);
        private final int width;
        private final int height;
        Resolution(int width, int height) { this.width = width; this.height = height; }
        public int width() { return width; }
        public int height() { return height; }
        @Override public String toString() { return width + " × " + height; }
    }

    public ClientSettings {
        if (volume < 0 || volume > 100) {
            throw new IllegalArgumentException("Volume must be between 0 and 100");
        }
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(keys, "keys");
    }

    public static ClientSettings defaults() {
        return new ClientSettings(70, Resolution.MEDIUM, true, KeyBindings.defaults());
    }
}
