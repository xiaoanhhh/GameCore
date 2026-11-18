# JSpecify

| 项目 | 内容 |
| --- | --- |
| 类型 | 平台服务运行依赖 |
| 许可 | Apache-2.0 |
| 来源 | [官方项目](https://github.com/jspecify/jspecify) |
| 许可原文 | 本目录未单独保存，以上游项目发布的许可为准 |

## 作用

Jackson 使用的空值注解，属于传递依赖。

## 实际制品

| 制品 | 版本 |
| --- | --- |
| `org.jspecify:jspecify` | 1.0.1 |

## 构建工具中的版本

Spring Boot Gradle 插件在构建 `bootJar` 时也用到这个项目，版本如下。构建工具和平台服务运行在不同的类加载环境中，版本不同并不是同一进程混用。

| 制品 | 版本 |
| --- | --- |
| `org.jspecify:jspecify` | 1.0.1 |

## 调用位置

项目代码不直接调用。

## 限制

注解不代替运行时的输入校验。功能测试通过不代表吞吐能力或公网安全已经验证。

## 核对方法

完整的 JAR 大小、SHA-256 和 POM 许可声明见 dependencies.json，可用 `.\gradlew.bat -I tools/platform-inventory.init.gradle platformInventory` 重新生成。POM 没有 license 字段时，以上游项目的许可和本目录保存的原文为准，不把缺失字段理解为没有许可。
