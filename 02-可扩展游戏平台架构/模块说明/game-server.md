# game-server：权威房间服务器

> 扩展角色：**宿主**。以单写入者房间驱动任意已注册游戏；房间、调度、网络代码中没有按游戏 ID 的业务分支。

## 职责

- v2 多游戏节点：RoomHost 逻辑分片调度、RoomActor 单写入者房间、Netty 接入、输入校验与确认、房间协调状态（选择/准备/开场/暂停/重开）。
- 平台节点：票据后台兑换、结果 outbox 落盘与重试（`PlatformGateway`）。
- 部署：`serve --config=...`、ServerLimits 容量限额、回环只读健康端点。
- 开发工具：文件录像/回放、TickDebugger（仅回环开发模式）、回环负载工具、JFR 报告。
- 兼容保留：旧 Tank v1 单房服务器（`core` 包、7100 端点）。

## 在扩展体系中的位置

| 类 | 与扩展的关系 |
| --- | --- |
| [RoomHost](../../game-server/src/main/java/dev/gamecore/server/multigame/RoomHost.java) | 构造时接收 `GameRegistry`；`registry.require(spec.game())` 创建房间；`defaults(games)` 为每款已注册游戏生成默认房间 |
| [RoomActor](../../game-server/src/main/java/dev/gamecore/server/multigame/RoomActor.java) | 只持有 `GameFactory<?,?>` 与 `GameInstance`，处理字节与协调状态 |
| [NettyMultiGameServer](../../game-server/src/main/java/dev/gamecore/server/multigame/NettyMultiGameServer.java) | EventLoop 只做解码与 offer 到有界邮箱，不执行 Tick |
| [ReplayStore](../../game-server/src/main/java/dev/gamecore/server/devtools/ReplayStore.java) / [ReplayPlayer](../../game-server/src/main/java/dev/gamecore/server/devtools/ReplayPlayer.java) | 按录像头游戏 ID 从注册表取工厂重模拟 |
| [DeploymentConfig](../../game-server/src/main/java/dev/gamecore/server/deployment/DeploymentConfig.java) | 仍按三款游戏读取 `*.tickRate`，新游戏回退为工厂默认值（见 [06](../06-扩展边界与演进方向.md)） |

新增游戏时本模块**通常不改**。详见 [03-在线扩展点](../03-在线扩展点.md)。

## 代码组织

| 包 | 内容 |
| --- | --- |
| `dev.gamecore.server` | `GameServerMain` 命令分派（`serve`/`platform`/`multi`/`bot`/开发工具/旧 `server`、`client`） |
| `server.multigame` | v2：`RoomHost`、`RoomActor`、`NettyMultiGameServer`、`ServerLimits`、无头客户端 |
| `server.platform` | `PlatformGateway`、`PlatformServerMain` |
| `server.deployment` | `DeploymentConfig`、`DeploymentMain`、`HealthEndpoint` |
| `server.devtools` | 录像、回放、调试、遥测、负载、JFR |
| `server.core`、`server.network`、`server.client` | 旧 Tank v1 房间、连接与命令行客户端 |

## 依赖

运行/编译：`protocol`、`network-transport`、`game-api`、`game-tank`、`game-online`、`platform-api`、Netty。`linuxRelease` 打包任务会带上 platform-service 的 BootJar。

## 验证

```powershell
.\gradlew.bat :game-server:test --console=plain
```

关键测试：[RoomActorTest](../../game-server/src/test/java/dev/gamecore/server/multigame/RoomActorTest.java)（received/executed、重复与跨游戏拒绝、有界输入、持续命令租约、单写入者）、[InteractiveRoomTest](../../game-server/src/test/java/dev/gamecore/server/multigame/InteractiveRoomTest.java)、[ReplayTest](../../game-server/src/test/java/dev/gamecore/server/devtools/ReplayTest.java)。部署与运维见 [部署环境](../../03-客户端与服务器网络通信/04-部署环境.md)。
