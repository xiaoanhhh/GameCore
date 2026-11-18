package dev.gamecore.api.online;
import java.util.Map;
import java.util.TreeMap;
/** 受信房间配置，人数与roomTps另存；插件拒绝未知选项，客户端不能上传任意地图/生命值。 */
public record GameRuleConfig(int players, int roomTps, Map<String, String> options) {
    public GameRuleConfig {
        if (players < 1 || players > 8 || roomTps < 1 || roomTps > 120 || options.size() > 16) { throw new IllegalArgumentException("Invalid rule config"); }
        var copy = new TreeMap<String, String>();
        options.forEach((k, v) -> { if (k == null || v == null || k.length() > 32 || v.length() > 64) { throw new IllegalArgumentException("Oversized option"); } copy.put(k, v); });
        options = java.util.Collections.unmodifiableMap(copy);
    }
    public void require(GameType type) {
        if (players < type.minPlayers() || players > type.maxPlayers() || type.simulationTps() % roomTps != 0) { throw new IllegalArgumentException("Unsupported room config"); }
    }
}
