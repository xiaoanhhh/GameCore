# Apache Commons Logging

| 项目 | 内容 |
| --- | --- |
| 类型 | 平台服务运行依赖 |
| 许可 | Apache-2.0 |
| 来源 | [官方项目](https://commons.apache.org/proper/commons-logging/) |
| 许可原文 | [upstream/](upstream/)、[upstream-build/](upstream-build/) |

## 作用

Spring 使用的日志门面，属于传递依赖。

## 实际制品

| 制品 | 版本 |
| --- | --- |
| `commons-logging:commons-logging` | 1.3.6 |

## 构建工具中的版本

Spring Boot Gradle 插件在构建 `bootJar` 时也用到这个项目，版本如下。构建工具和平台服务运行在不同的类加载环境中，版本不同并不是同一进程混用。

| 制品 | 版本 |
| --- | --- |
| `commons-logging:commons-logging` | 1.3.5 |

## 调用位置

项目代码不直接调用。

## 限制

只存在于平台服务；游戏规则和游戏节点没有因此增加日志框架。功能测试通过不代表吞吐能力或公网安全已经验证。

## 核对方法

完整的 JAR 大小、SHA-256 和 POM 许可声明见 dependencies.json，可用 `.\gradlew.bat -I tools/platform-inventory.init.gradle platformInventory` 重新生成。POM 没有 license 字段时，以上游项目的许可和本目录保存的原文为准，不把缺失字段理解为没有许可。
