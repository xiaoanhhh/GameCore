package dev.gamecore.server.multigame;

import dev.gamecore.api.online.GameRuleConfig;
import java.util.Map;

/** 部署资源限额；默认构造保留既有实验行为，房间TPS按游戏验证，不改变核心模拟步长。 */
public record ServerLimits(int maxRooms, int maxConnections, int nettyWorkerThreads, int logicThreads,
        int maxPendingInputs, int pendingPerConnection, int snapshotRate, Map<String,Integer> tickRates) {
    public ServerLimits {
        if (maxRooms < 1 || maxRooms > 128 || maxConnections < 2 || maxConnections > 512
                || nettyWorkerThreads < 1 || nettyWorkerThreads > 8 || logicThreads < 1 || logicThreads > 8
                || maxPendingInputs < 2 || maxPendingInputs > 256 || pendingPerConnection < 1
                || pendingPerConnection > 32 || pendingPerConnection > maxPendingInputs || snapshotRate < 0 || snapshotRate > 60) {
            throw new IllegalArgumentException("Server resource limits");
        }
        tickRates = Map.copyOf(tickRates);
    }
    public static ServerLimits legacy() { return new ServerLimits(128,512,2,2,256,32,0,Map.of()); }
    public GameRuleConfig config(String game, GameRuleConfig original) {
        return new GameRuleConfig(original.players(), tickRates.getOrDefault(game,original.roomTps()),original.options());
    }
}
