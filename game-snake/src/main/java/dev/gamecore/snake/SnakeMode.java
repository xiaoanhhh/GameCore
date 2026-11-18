package dev.gamecore.snake;

/** 实际可玩模式；目录 ID 保持与大厅兼容。 */
public enum SnakeMode {
    CLASSIC("classic"), OBSTACLES("obstacles"), DUO("duo");
    private final String id;
    SnakeMode(String id) { this.id = id; }
    public String id() { return id; }
    public static SnakeMode fromId(String id) {
        for (SnakeMode mode : values()) { if (mode.id.equals(id)) { return mode; } }
        throw new IllegalArgumentException("Unknown Snake mode: " + id);
    }
}
