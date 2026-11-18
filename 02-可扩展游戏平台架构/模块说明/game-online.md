# game-online：联机适配层与在线注册表

> 扩展角色：**适配层**。为每款游戏实现 `GameFactory`，并提供服务器、平台、在线客户端共用的唯一在线注册表；把异构泛型游戏收敛为统一的字节步进接口。

## 职责

- 每款游戏一个工厂：类型元数据、受信配置白名单、创建原有会话、输入/快照与 Protobuf 互转、选项归属、测试 Bot。
- `GameRegistry`：显式登记全部工厂，构造后不可变，拒绝重复/未知 ID。
- `GameInstance`：唯一的类型擦除边界，绑定 owner 线程。
- 不运行网络、不在 Netty 中模拟、不重写任何规则。

## 在扩展体系中的位置

| 类 | 角色 | 新增游戏时 |
| --- | --- | --- |
| [GameRegistry](../../game-online/src/main/java/dev/gamecore/online/GameRegistry.java) | E5 登记点 | `builtIn()` 列表加一项 |
| `tank/` `snake/` `fight/` 子包 | E2/E3 实现 | 新增 `xxx/XxxFactory`、`XxxSnapshotCodec` |
| [GameInstance](../../game-online/src/main/java/dev/gamecore/online/runtime/GameInstance.java)、`GameStep`、`AppliedInput` | 通用运行时 | 不改 |
| [ProtocolValues](../../game-online/src/main/java/dev/gamecore/online/ProtocolValues.java)、`StateHashes` | 通用事件/配置转换与状态哈希 | 仅当新增通用事件种类时改 |

详见 [03-在线扩展点](../03-在线扩展点.md)。

## 代码组织

| 包 | 内容 |
| --- | --- |
| `dev.gamecore.online` | `GameRegistry`、`ProtocolValues` |
| `dev.gamecore.online.runtime` | `GameInstance`、`GameStep`、`AppliedInput`、`StateHashes` |
| `dev.gamecore.online.tank` / `snake` / `fight` | 各游戏工厂与快照编解码器 |

## 依赖

运行/编译（均为 `api`）：`game-api`、`protocol`、`game-tank`、`game-snake`、`game-fight`。被 client、game-server、platform-service 依赖。

## 验证

```powershell
.\gradlew.bat :game-online:test --console=plain
```

[AdapterTest](../../game-online/src/test/java/dev/gamecore/online/AdapterTest.java) 覆盖注册表重复/未知 ID、错误游戏负载拒绝、同 seed 双实例确定性往返、字节所有权、外来线程访问拒绝；`FightAdapterTest`、`PresentationEventTest` 覆盖格斗适配与表现事件。
