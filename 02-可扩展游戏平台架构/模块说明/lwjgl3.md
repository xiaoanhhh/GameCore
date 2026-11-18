# lwjgl3：桌面启动与图形验收

> 扩展角色：**宿主外壳**。启动 client、管理多窗口，并承载需要真实桌面的图形验收与 Windows 便携打包。

## 职责

- [Lwjgl3Launcher](../../lwjgl3/src/main/java/dev/gamecore/lwjgl3/Lwjgl3Launcher.java)：桌面入口；`DesktopGameWindows` 独立游戏窗口；`DesktopSettings` 窗口设置。
- `src/smokeTest`：大厅、各游戏、多窗口、缩放、平台页面、在线双 GUI 等图形冒烟，以显式 Gradle 任务运行，不属于普通单元测试。
- `portable.gradle`：Windows 便携包（Temurin 21 运行时），产物在 `dist/`，不入库。

## 在扩展体系中的位置

新增游戏时本模块生产代码**不改**；建议为新游戏增加一个 `xxxSmokeTest` 任务，与已有 `snakeSmokeTest`、`tankSmokeTest`、`fightSmokeTest` 并列。

## 依赖

运行/编译：`client`、`gdx-backend-lwjgl3`。smokeTest 额外依赖 `protocol`、`platform-api`、`game-online`、`network-transport`，在线验收会启动 game-server 安装包。

`smokeTest` 源集依赖 `game-server`，仅用于 `trafficOverlaySmokeTest` 在进程内启动真实游戏服务器，桌面正式产物不包含它。

## 验证

```powershell
.\gradlew.bat :lwjgl3:smokeTest --console=plain
.\gradlew.bat :lwjgl3:snakeSmokeTest :lwjgl3:tankSmokeTest :lwjgl3:fightSmokeTest --console=plain
.\gradlew.bat :lwjgl3:gameWindowTest :lwjgl3:uiScaleTest --console=plain
.\gradlew.bat :lwjgl3:onlineArenaTest --console=plain
```

图形任务需要真实桌面图形会话；失败时区分运行环境与代码错误。
