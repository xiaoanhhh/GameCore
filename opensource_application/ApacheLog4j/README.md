# Apache Log4j API

| 项目 | 内容 |
| --- | --- |
| 类型 | 平台服务运行依赖 |
| 许可 | Apache-2.0 |
| 来源 | [官方项目](https://logging.apache.org/log4j/2.x/) |
| 许可原文 | [upstream/](upstream/) |

## 作用

Spring Boot 引入 `log4j-api` 和 `log4j-to-slf4j` 桥接，把 Log4j API 的日志转给 SLF4J。

## 实际制品

| 制品 | 版本 |
| --- | --- |
| `org.apache.logging.log4j:log4j-api` | 2.25.5 |
| `org.apache.logging.log4j:log4j-to-slf4j` | 2.25.5 |

## 调用位置

项目代码不直接调用。

## 限制

没有引入 `log4j-core`，所以平台服务里只有 Logback 一个日志后端。功能测试通过不代表吞吐能力或公网安全已经验证。

## 核对方法

完整的 JAR 大小、SHA-256 和 POM 许可声明见 dependencies.json，可用 `.\gradlew.bat -I tools/platform-inventory.init.gradle platformInventory` 重新生成。POM 没有 license 字段时，以上游项目的许可和本目录保存的原文为准，不把缺失字段理解为没有许可。
