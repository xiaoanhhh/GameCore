package dev.gamecore.server;

/** 启动配置只允许既有60Hz规则的整数子步；网络/模拟速率不同且在协议中显式声明。 */
public record ServerSettings(String host, int port, int tps, String mapId, long seed,
                             int inputsPerSecond, int idleSeconds) {
    public ServerSettings {
        if (host == null || host.isBlank() || port < 0 || port > 65535 || tps < 10 || tps > 60 || 60 % tps != 0
                || !("crossfire".equals(mapId) || "ruins".equals(mapId)) || inputsPerSecond < 1 || inputsPerSecond > 120
                || idleSeconds < 1 || idleSeconds > 300) { throw new IllegalArgumentException("Invalid server settings"); }
    }
    public static ServerSettings defaults(int port) { return new ServerSettings("127.0.0.1", port, 30, "crossfire", 42, 60, 15); }
}
