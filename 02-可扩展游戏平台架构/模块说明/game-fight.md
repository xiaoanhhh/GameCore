# game-fight：终极格斗规则

> 扩展角色：**扩展实现**。`GameSession<FightCommand, FightSnapshot>`，60 Hz，规则版本 `fight/7`。

## 职责

两名斗士（奶蛙、肥嘟嘟）、五张地图的真实落脚布局、跳跃/穿台、拳击、远程、格挡、大招 Ritual、闪袭、大笑动作与 Bot。规则不依赖渲染：开场、胜者庆祝、打击特效都是客户端对快照的只读投影。

## 在扩展体系中的位置

- 实现 E1：[FightSession](../../game-fight/src/main/java/dev/gamecore/fight/FightSession.java) 自身承载较多规则，未拆出 `XxxGame` 类——契约不强制内部结构。
- 实现 E9：[FightSessionFactory](../../game-fight/src/main/java/dev/gamecore/fight/FightSessionFactory.java) 是当前唯一的 `replay.SessionFactory` 生产实现；在线 `FightFactory` 也通过它创建会话。
- 游戏专属事实（Impact、Ritual、Teleport 等）放在 [FightSnapshot](../../game-fight/src/main/java/dev/gamecore/fight/FightSnapshot.java) 中，而非扩展通用 `GameEvent`——这是“差异留在游戏内部”的示例，见 [06 第 4 节](../06-扩展边界与演进方向.md)。
- 地图在 `FightMap`/配置/快照中一致携带，Bot 与回放使用同一地图。

## 代码组织（`dev.gamecore.fight`，单包）

| 文件 | 作用 |
| --- | --- |
| `FightSession`、`FightSessionFactory` | 会话与回放工厂 |
| `FightArena`、`FightMap` | 场地与五张地图的平台几何（碰撞依据，材质图不参与） |
| `FightPunch`、`FightSpecial` | 近战与大招/特殊技规则 |
| `FighterKind`、`FightCommand`、`FightSnapshot` | 角色、输入、快照 |
| `FightBot` | 人机 AI |

## 依赖

运行/编译：`game-api`。

## 验证

```powershell
.\gradlew.bat :game-fight:test --console=plain
.\gradlew.bat :lwjgl3:fightSmokeTest --console=plain   # 图形冒烟
```
