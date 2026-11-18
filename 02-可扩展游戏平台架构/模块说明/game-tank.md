# game-tank：Tank Arena 规则

> 扩展角色：**扩展实现**。`GameSession<TankCommand, TankSnapshot>`，60 Hz 连续坐标规则；采用面向对象实现，无 ECS 依赖。

## 职责

固定 60 Hz 规则、TMX 地图解析、移动与碰撞、开火/伤害/维修/胜负、Bot。本地 `TankScreen`、在线 `TankFactory`、服务器房间都运行同一份 `TankSession`。

## 在扩展体系中的位置

- 实现 E1：[TankSession](../../game-tank/src/main/java/dev/gamecore/tank/TankSession.java) 包装 [TankGame](../../game-tank/src/main/java/dev/gamecore/tank/TankGame.java)。
- 持续命令语义：一条命令持续生效到显式 `IDLE` 或暂停；联机层用 `GameInputCodec.held()/neutral()` 与租约配合。
- 是 [02 第 7 节](../02-规则内核契约.md#7-示例一次-tank-移动如何穿过内核)“一次移动如何穿过内核”的示例模块。

## 代码组织（`dev.gamecore.tank`，单包）

| 文件 | 作用 |
| --- | --- |
| `TankSession`、`TankSessionFactory`、`TankMode` | 会话契约实现与创建 |
| `TankGame` | 内部可变世界，按固定顺序推进一步 |
| `TankMovement`、`TankRules`、`Collision`、`TankEvents` | 移动提案/碰撞、冷却/伤害/胜负、事件——共享纯规则核 |
| `TankMap`、`TmxArenaLoader`、`TankMapFingerprint` | 地图模型、TMX 解析、地图指纹核对 |
| `TankBot`、`ArenaBot`、`GridPathfinder` | 人机 AI，只通过快照产生输入 |
| `TankCommand`、`TankSnapshot`、`Vec2` | 输入、快照与坐标值类型 |

## 依赖

运行/编译：`game-api`。

## 验证

```powershell
.\gradlew.bat :game-tank:test --console=plain
.\gradlew.bat :lwjgl3:tankSmokeTest --console=plain    # 图形冒烟
```

关键规则测试：[TankSessionContractTest](../../game-tank/src/test/java/dev/gamecore/tank/TankSessionContractTest.java)。
