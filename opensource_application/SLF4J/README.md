# SLF4J

| 项目 | 内容 |
| --- | --- |
| 类型 | 平台服务运行依赖 |
| 许可 | MIT |
| 来源 | [官方项目](https://www.slf4j.org/license.html) |
| 许可原文 | [upstream/](upstream/) |

## 作用

Spring Boot 的日志门面和 JUL 桥接。

## 实际制品

| 制品 | 版本 |
| --- | --- |
| `org.slf4j:jul-to-slf4j` | 2.0.20 |
| `org.slf4j:slf4j-api` | 2.0.20 |

## 构建工具中的版本

Spring Boot Gradle 插件在构建 `bootJar` 时也用到这个项目，版本如下。构建工具和平台服务运行在不同的类加载环境中，版本不同并不是同一进程混用。

| 制品 | 版本 |
| --- | --- |
| `org.slf4j:slf4j-api` | 1.7.36 |

## 调用位置

平台服务的日志链路。

## 限制

不带账号或联网功能。功能测试通过不代表吞吐能力或公网安全已经验证。

## 核对方法

完整的 JAR 大小、SHA-256 和 POM 许可声明见 dependencies.json，可用 `.\gradlew.bat -I tools/platform-inventory.init.gradle platformInventory` 重新生成。POM 没有 license 字段时，以上游项目的许可和本目录保存的原文为准，不把缺失字段理解为没有许可。
