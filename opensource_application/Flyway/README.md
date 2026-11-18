# Flyway

| 项目 | 内容 |
| --- | --- |
| 类型 | 平台服务运行依赖 |
| 许可 | Apache-2.0 |
| 来源 | [官方项目](https://github.com/flyway/flyway) |
| 许可原文 | [upstream/](upstream/) |

## 作用

平台服务启动时执行并校验数据库结构迁移。

## 实际制品

| 制品 | 版本 |
| --- | --- |
| `org.flywaydb:flyway-core` | 12.4.0 |
| `org.flywaydb:flyway-database-postgresql` | 12.4.0 |

## 调用位置

迁移脚本是 `platform-service/src/main/resources/db/migration/V1__platform.sql`，由 Spring Boot 自动调用。

## 限制

使用社区版 core 和 PostgreSQL 插件，没有使用 Teams / Enterprise 功能。功能测试通过不代表吞吐能力或公网安全已经验证。

## 核对方法

完整的 JAR 大小、SHA-256 和 POM 许可声明见 dependencies.json，可用 `.\gradlew.bat -I tools/platform-inventory.init.gradle platformInventory` 重新生成。POM 没有 license 字段时，以上游项目的许可和本目录保存的原文为准，不把缺失字段理解为没有许可。
