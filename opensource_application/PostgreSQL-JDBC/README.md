# PostgreSQL JDBC

| 项目 | 内容 |
| --- | --- |
| 类型 | 平台服务运行依赖 |
| 许可 | BSD-2-Clause |
| 来源 | [官方项目](https://jdbc.postgresql.org/) |
| 许可原文 | [upstream/](upstream/) |

## 作用

平台服务连接 PostgreSQL 的驱动。

## 实际制品

| 制品 | 版本 |
| --- | --- |
| `org.postgresql:postgresql` | 42.7.14 |

## 调用位置

通过 HikariCP 和 `JdbcTemplate` 访问，SQL 使用预编译语句绑定参数，业务代码在 `LobbyService`、`MatchService`、`AuthService`。

## 限制

数据库访问只发生在平台服务中，游戏 Tick 不访问数据库。功能测试通过不代表吞吐能力或公网安全已经验证。

## 核对方法

完整的 JAR 大小、SHA-256 和 POM 许可声明见 dependencies.json，可用 `.\gradlew.bat -I tools/platform-inventory.init.gradle platformInventory` 重新生成。POM 没有 license 字段时，以上游项目的许可和本目录保存的原文为准，不把缺失字段理解为没有许可。
