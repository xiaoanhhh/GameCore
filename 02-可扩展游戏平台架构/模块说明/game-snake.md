# game-snake：Snake Arena 规则

> 扩展角色：**扩展实现**。`GameSession<Direction, SnakeSnapshot>`，20 Hz 网格规则。

## 职责

经典、障碍、本地双人、Bot 与确定种子会话。在线双人（含受信障碍场景）由 game-online 的 `SnakeFactory` 直接复用本模块会话，不另写网络规则。

## 在扩展体系中的位置

- 实现 E1：[SnakeGameSession](../../game-snake/src/main/java/dev/gamecore/snake/SnakeGameSession.java) 包装 [SnakeGame](../../game-snake/src/main/java/dev/gamecore/snake/SnakeGame.java)。
- 规则频率 20 Hz，但蛇按 `movementCredit` 积累移动，**一个模拟 Tick 不等于移动一格**——这说明契约只统一 Tick 节奏，不统一游戏内的“步”的含义。
- 快照 [SnakeSnapshot](../../game-snake/src/main/java/dev/gamecore/snake/SnakeSnapshot.java) 由客户端 `SnakeBoard` 和在线 `SnakeSnapshotCodec` 共同消费。

## 代码组织（`dev.gamecore.snake`，单包）

| 文件 | 作用 |
| --- | --- |
| `SnakeGameSession`、`SnakeSessionFactory`、`SnakeMode` | 会话契约实现、按模式创建会话 |
| `SnakeGame`、`SnakeWorld`、`SnakePlayer` | 内部可变世界与推进 |
| `CollisionRules`、`ScoreSystem` | 碰撞与计分纯规则 |
| `GridMap`、`Cell`、`Direction` | 网格、坐标与输入类型 |
| `SnakeSnapshot` | 只读快照 |

## 依赖

运行/编译：`game-api`（`api project(':game-api')`，无其他依赖）。

## 验证

```powershell
.\gradlew.bat :game-snake:test --console=plain
.\gradlew.bat :lwjgl3:snakeSmokeTest --console=plain   # 图形冒烟，需真实桌面会话
```
