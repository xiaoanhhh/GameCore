package dev.gamecore.tank;

/** 当前只实现本地双人和本地人机，不预留网络会话或 ECS 空壳。 */
public enum TankMode {
    DUEL("duel"), BOT("bot");
    private final String id;
    TankMode(String id) { this.id = id; }
    public String id() { return id; }
    public static TankMode fromId(String id) {
        for (TankMode mode : values()) { if (mode.id.equals(id)) { return mode; } }
        throw new IllegalArgumentException("Unknown tank mode: " + id);
    }
}
