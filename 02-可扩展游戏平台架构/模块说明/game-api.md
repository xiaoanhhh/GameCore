# game-api：扩展契约

> 扩展角色：**契约层**。定义平台与游戏之间的全部接口；不依赖任何工程模块，也不依赖 libGDX、Netty、Protobuf、Spring、JDBC。

## 职责

规定“一局游戏”怎样被运行、被联机、被展示在目录中、被录像。只有接口、值类型和少量通用工具，没有任何具体游戏规则。

## 提供的扩展点

| 包 | 类型 | 作用 | 详见 |
| --- | --- | --- | --- |
| `session` | `GameSession<I,S>`、`PlayerInput<I>`、`SessionState` | 规则会话契约（E1） | [02](../02-规则内核契约.md) |
| `session` | `SessionInputs<I>`、`SessionLoop<I,S>` | 通用有界输入队列、逐步执行并发布事件 | [02](../02-规则内核契约.md) |
| `event` | `GameEvent`（sealed）、`EventBus` | 五种通用事件与同线程观察者 | [02](../02-规则内核契约.md) |
| `online` | `GameFactory<I,S>`、`GameSessionFactory`、`GameInputCodec`、`GameSnapshotCodec`、`GameType`、`GameRuleConfig` | 联机适配契约（E2、E3）及其硬边界 | [03](../03-在线扩展点.md) |
| 根包 | `GameModule`、`GameDescriptor`、`GameMode` | 大厅目录元数据（E6） | [04](../04-客户端扩展点.md) |
| `replay` | `SessionFactory<C,I,S>`、`RecordingSession`、`ReplayRunner`、`ReplayTape`、`ReplayOperation` | 进程内录像与重模拟校验（E9） | [02](../02-规则内核契约.md) |

## 新增游戏时是否修改

通常**不修改**。只有当新游戏需要新的通用事件种类（`GameEvent` 为 sealed）时才需改动本模块，并同步 game-online 的 `ProtocolValues`。修改任何接口都会影响全部三款游戏，须先检查影响面。

## 依赖

无工程依赖。被 game-snake、game-tank、game-fight、game-online、client、game-server 依赖。

## 阅读入口

[GameSession](../../game-api/src/main/java/dev/gamecore/api/session/GameSession.java) → [SessionInputs](../../game-api/src/main/java/dev/gamecore/api/session/SessionInputs.java) → [SessionLoop](../../game-api/src/main/java/dev/gamecore/api/session/SessionLoop.java) → [GameFactory](../../game-api/src/main/java/dev/gamecore/api/online/GameFactory.java) → [RecordingSession](../../game-api/src/main/java/dev/gamecore/api/replay/RecordingSession.java)

## 验证

```powershell
.\gradlew.bat :game-api:test --console=plain
```

测试覆盖描述符校验、输入队列边界、事件总线异常收集等（`game-api/src/test`）。
