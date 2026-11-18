package dev.gamecore.api.online;
/** 在线兼容性身份与人数/节拍；展示版本与规则版本分别协商，不等于客户端目录GameDescriptor。 */
public record GameType(String id, String gameVersion, String rulesVersion, int minPlayers, int maxPlayers, int simulationTps, int defaultRoomTps) {
    public GameType {
        if (id == null || !id.matches("[a-z][a-z0-9-]{0,31}") || gameVersion == null || gameVersion.isBlank() || rulesVersion == null || rulesVersion.isBlank()
                || minPlayers < 1 || maxPlayers < minPlayers || maxPlayers > 8 || simulationTps < 1 || simulationTps > 120 || defaultRoomTps < 1 || simulationTps % defaultRoomTps != 0) { throw new IllegalArgumentException("Invalid game type"); }
    }
}
