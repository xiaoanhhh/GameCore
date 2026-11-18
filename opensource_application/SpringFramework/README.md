# Spring Framework

| 项目 | 内容 |
| --- | --- |
| 类型 | 平台服务运行依赖 |
| 许可 | Apache-2.0 |
| 来源 | [官方项目](https://github.com/spring-projects/spring-framework) |
| 许可原文 | [upstream/](upstream/)、[upstream-build/](upstream-build/) |

## 作用

平台服务使用 Spring MVC、`JdbcTemplate` 和事务模板。

## 实际制品

| 制品 | 版本 |
| --- | --- |
| `org.springframework:spring-aop` | 7.0.10 |
| `org.springframework:spring-beans` | 7.0.10 |
| `org.springframework:spring-context` | 7.0.10 |
| `org.springframework:spring-core` | 7.0.10 |
| `org.springframework:spring-expression` | 7.0.10 |
| `org.springframework:spring-jdbc` | 7.0.10 |
| `org.springframework:spring-tx` | 7.0.10 |
| `org.springframework:spring-web` | 7.0.10 |
| `org.springframework:spring-webmvc` | 7.0.10 |

## 构建工具中的版本

Spring Boot Gradle 插件在构建 `bootJar` 时也用到这个项目，版本如下。构建工具和平台服务运行在不同的类加载环境中，版本不同并不是同一进程混用。

| 制品 | 版本 |
| --- | --- |
| `org.springframework:spring-core` | 7.0.10 |

## 调用位置

`PlatformController`（HTTP 接口）、`LobbyService`、`MatchService`（数据库事务）。

## 限制

数据库事务只在平台请求线程和维护线程中执行。功能测试通过不代表吞吐能力或公网安全已经验证。

## 核对方法

完整的 JAR 大小、SHA-256 和 POM 许可声明见 dependencies.json，可用 `.\gradlew.bat -I tools/platform-inventory.init.gradle platformInventory` 重新生成。POM 没有 license 字段时，以上游项目的许可和本目录保存的原文为准，不把缺失字段理解为没有许可。
