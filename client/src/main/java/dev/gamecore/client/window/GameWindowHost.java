package dev.gamecore.client.window;

import dev.gamecore.client.GameCoreClient;

/** 客户端到桌面多窗口的实际适配口；窗口句柄和后端 API 留在 lwjgl3，不进入规则层。 */
@FunctionalInterface
public interface GameWindowHost {
    void open(GameCoreClient owner, String gameId, String modeId);
}
