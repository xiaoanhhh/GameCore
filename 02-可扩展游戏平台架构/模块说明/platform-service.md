# platform-service：在线平台模块化单体

> 扩展角色：**平台宿主**。按 `GameRegistry` 元数据提供账户、大厅、票据、结算；业务代码不含具体游戏逻辑。

## 职责

Spring Boot 4.1.2 / Security 7.1.2 / PostgreSQL / Flyway：注册登录、公开游客大厅摘要、建房/加入/准备、比赛分配与一次性票据、SERVER 角色结果接口与幂等结算、好友、排行、战绩。实时世界不在这里推进。

## 在扩展体系中的位置

- [LobbyService](../../platform-service/src/main/java/dev/gamecore/platform/LobbyService.java) 持有 `GameRegistry.builtIn()`：`games()` 列出全部已注册游戏；建房 `require(gameId)` 校验并取人数；票据携带游戏版本。
- 数据库只存 `game_id varchar(32)` 等通用列（[V1__platform.sql](../../platform-service/src/main/resources/db/migration/V1__platform.sql)），没有按游戏的枚举或表。
- 因此新增游戏**不需改动本模块代码**；接入时仍应补平台集成测试确认。详见 [03 第 7 节](../03-在线扩展点.md#7-平台按元数据工作不含游戏代码)。

## 代码组织（`dev.gamecore.platform`）

| 文件 | 作用 |
| --- | --- |
| `PlatformApplication`、`PlatformController`、`ApiProblem` | 入口、HTTP 路由、错误响应 |
| `SecurityConfiguration`、`AuthService`、`Tokens` | 认证、角色（internal 仅 SERVER）、令牌 |
| `LobbyService` | 大厅、房间、票据签发与兑换 |
| `MatchService` | 结果核对与事务幂等结算、战绩历史、按游戏 ID 的排行（`/ranks/{game}`） |
| `SocialService` | 好友与房间邀请 |
| `Maintenance` | 定时清理失效会话等业务数据 |

## 依赖

运行/编译：`platform-api`、`game-online`、Spring Boot（webmvc、security、jdbc、flyway）、PostgreSQL 驱动。测试：`game-server`、`network-transport`。

## 验证

```powershell
.\gradlew.bat :platform-service:test --console=plain
.\gradlew.bat :platform-service:postgresTest --console=plain   # 只接受回环 *_test 库，随机 schema
```
