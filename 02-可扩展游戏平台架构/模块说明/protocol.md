# protocol：Protobuf 协议

> 扩展角色：**协议层**。外层信封与游戏无关，内层负载按游戏用 oneof 区分。

## 职责

定义客户端、游戏节点与录像文件的二进制格式。生成代码位于 `build/`，不入库。

| 文件 | 内容 |
| --- | --- |
| [games.proto](../../protocol/src/main/proto/games.proto) | v2 多游戏协议：Envelope、Hello/Welcome、Join/Joined、Input、RoomControl/RoomState、Snapshot、Ack、EventBatch、Result；内层 `GameInput`/`GameSnapshot` oneof 及各游戏消息 |
| [replay.proto](../../protocol/src/main/proto/replay.proto) | 录像文件：Header（游戏类型、配置、seed、初始哈希）、Frame（规范化输入、周期快照、状态哈希）、End |
| [tank.proto](../../protocol/src/main/proto/tank.proto) | 旧 Tank v1 协议（7100 端点），兼容保留，不属于多游戏扩展体系 |

## 在扩展体系中的位置

- 外层 `Input.command` 与 `Snapshot.state` 是不透明 `bytes`，服务器和传输层只读外层字段——这是服务器不认识游戏的协议基础。
- 内层 oneof 是 E4 登记点：新增游戏在 `GameInput`、`GameSnapshot` 各追加一个分支和对应消息。oneof 防止不同游戏相同字段布局被误读。
- 只追加新字段号，不修改已有编号。

详见 [03 第 6 节](../03-在线扩展点.md#6-协议外层通用内层按游戏)。

## 依赖

运行/编译：`protobuf-java`（版本与许可见 [opensource_application](../../opensource_application/)）。被 network-transport、game-online 依赖。

## 验证

```powershell
.\gradlew.bat :protocol:build --console=plain
```

协议行为由 game-online 编解码测试与 game-server 网络测试覆盖。
