# Logback

| 项目 | 内容 |
| --- | --- |
| 类型 | 平台服务运行依赖 |
| 许可 | EPL-2.0 / LGPL-2.1 双许可 |
| 来源 | [官方项目](https://logback.qos.ch/license.html) |
| 许可原文 | [LICENSE.txt](LICENSE.txt) |

## 作用

Spring Boot 默认的日志实现，属于传递依赖。

## 实际制品

| 制品 | 版本 |
| --- | --- |
| `ch.qos.logback:logback-classic` | 1.5.38 |
| `ch.qos.logback:logback-core` | 1.5.38 |

## 调用位置

平台服务的日志输出。

## 限制

日志中不记录令牌、密码或请求体。功能测试通过不代表吞吐能力或公网安全已经验证。

## 核对方法

完整的 JAR 大小、SHA-256 和 POM 许可声明见 dependencies.json，可用 `.\gradlew.bat -I tools/platform-inventory.init.gradle platformInventory` 重新生成。POM 没有 license 字段时，以上游项目的许可和本目录保存的原文为准，不把缺失字段理解为没有许可。
