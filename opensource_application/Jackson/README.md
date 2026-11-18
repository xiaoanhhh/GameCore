# Jackson

| 项目 | 内容 |
| --- | --- |
| 类型 | 平台服务运行依赖 |
| 许可 | Apache-2.0 |
| 来源 | [官方项目](https://github.com/FasterXML/jackson) |
| 许可原文 | [upstream/](upstream/)、[upstream-build/](upstream-build/) |

## 作用

平台 HTTP 接口的 JSON 编解码。`jackson-annotations` 2.21 与 Jackson 3.1.7 配套。

## 实际制品

| 制品 | 版本 |
| --- | --- |
| `com.fasterxml.jackson.core:jackson-annotations` | 2.21 |
| `tools.jackson.core:jackson-core` | 3.1.7 |
| `tools.jackson.core:jackson-databind` | 3.1.7 |

## 构建工具中的版本

Spring Boot Gradle 插件在构建 `bootJar` 时也用到这个项目，版本如下。构建工具和平台服务运行在不同的类加载环境中，版本不同并不是同一进程混用。

| 制品 | 版本 |
| --- | --- |
| `com.fasterxml.jackson.core:jackson-annotations` | 2.21 |
| `tools.jackson.core:jackson-core` | 3.1.7 |
| `tools.jackson.core:jackson-databind` | 3.1.7 |

## 调用位置

`platform-api` 的 `PlatformHttpClient` 使用 `JsonMapper`；平台服务由 Spring Boot 自动配置。客户端和游戏节点通过 `platform-api` 间接使用。

## 限制

JSON 引擎只有 3.x 一个版本，不做 Java 对象反序列化。功能测试通过不代表吞吐能力或公网安全已经验证。

## 核对方法

完整的 JAR 大小、SHA-256 和 POM 许可声明见 dependencies.json，可用 `.\gradlew.bat -I tools/platform-inventory.init.gradle platformInventory` 重新生成。POM 没有 license 字段时，以上游项目的许可和本目录保存的原文为准，不把缺失字段理解为没有许可。
