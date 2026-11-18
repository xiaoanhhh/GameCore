# HikariCP

| 项目 | 内容 |
| --- | --- |
| 类型 | 平台服务运行依赖 |
| 许可 | Apache-2.0 |
| 来源 | [官方项目](https://github.com/brettwooldridge/HikariCP) |
| 许可原文 | 本目录未单独保存，以上游项目发布的许可为准 |

## 作用

平台服务的 JDBC 连接池。

## 实际制品

| 制品 | 版本 |
| --- | --- |
| `com.zaxxer:HikariCP` | 7.0.2 |

## 调用位置

配置在 `application.properties`：最大 8 个连接，获取连接超时 2 秒。

## 限制

它只管理数据库连接，和游戏房间的队列没有关系。功能测试通过不代表吞吐能力或公网安全已经验证。

## 核对方法

完整的 JAR 大小、SHA-256 和 POM 许可声明见 dependencies.json，可用 `.\gradlew.bat -I tools/platform-inventory.init.gradle platformInventory` 重新生成。POM 没有 license 字段时，以上游项目的许可和本目录保存的原文为准，不把缺失字段理解为没有许可。
