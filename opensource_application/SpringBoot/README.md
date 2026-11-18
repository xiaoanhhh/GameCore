# Spring Boot

| 项目 | 内容 |
| --- | --- |
| 类型 | 平台服务运行依赖 |
| 许可 | Apache-2.0 |
| 来源 | [官方项目](https://github.com/spring-projects/spring-boot) |
| 许可原文 | [upstream/](upstream/)、[upstream-build/](upstream-build/) |

## 作用

平台服务的启动入口、自动配置和 `bootJar` 打包。

## 实际制品

| 制品 | 版本 |
| --- | --- |
| `org.springframework.boot:spring-boot` | 4.1.2 |
| `org.springframework.boot:spring-boot-autoconfigure` | 4.1.2 |
| `org.springframework.boot:spring-boot-flyway` | 4.1.2 |
| `org.springframework.boot:spring-boot-http-converter` | 4.1.2 |
| `org.springframework.boot:spring-boot-jackson` | 4.1.2 |
| `org.springframework.boot:spring-boot-jdbc` | 4.1.2 |
| `org.springframework.boot:spring-boot-persistence` | 4.1.2 |
| `org.springframework.boot:spring-boot-security` | 4.1.2 |
| `org.springframework.boot:spring-boot-servlet` | 4.1.2 |
| `org.springframework.boot:spring-boot-sql` | 4.1.2 |
| `org.springframework.boot:spring-boot-starter` | 4.1.2 |
| `org.springframework.boot:spring-boot-starter-flyway` | 4.1.2 |
| `org.springframework.boot:spring-boot-starter-jackson` | 4.1.2 |
| `org.springframework.boot:spring-boot-starter-jdbc` | 4.1.2 |
| `org.springframework.boot:spring-boot-starter-logging` | 4.1.2 |
| `org.springframework.boot:spring-boot-starter-security` | 4.1.2 |
| `org.springframework.boot:spring-boot-starter-tomcat` | 4.1.2 |
| `org.springframework.boot:spring-boot-starter-tomcat-runtime` | 4.1.2 |
| `org.springframework.boot:spring-boot-starter-webmvc` | 4.1.2 |
| `org.springframework.boot:spring-boot-tomcat` | 4.1.2 |
| `org.springframework.boot:spring-boot-transaction` | 4.1.2 |
| `org.springframework.boot:spring-boot-web-server` | 4.1.2 |
| `org.springframework.boot:spring-boot-webmvc` | 4.1.2 |

## 构建工具中的版本

Spring Boot Gradle 插件在构建 `bootJar` 时也用到这个项目，版本如下。构建工具和平台服务运行在不同的类加载环境中，版本不同并不是同一进程混用。

| 制品 | 版本 |
| --- | --- |
| `org.springframework.boot:spring-boot-buildpack-platform` | 4.1.2 |
| `org.springframework.boot:spring-boot-gradle-plugin` | 4.1.2 |
| `org.springframework.boot:spring-boot-loader-tools` | 4.1.2 |

## 调用位置

入口是 `platform-service` 的 `PlatformApplication`。

## 限制

只用于平台服务，不进入游戏规则和游戏节点。功能测试通过不代表吞吐能力或公网安全已经验证。

## 核对方法

完整的 JAR 大小、SHA-256 和 POM 许可声明见 dependencies.json，可用 `.\gradlew.bat -I tools/platform-inventory.init.gradle platformInventory` 重新生成。POM 没有 license 字段时，以上游项目的许可和本目录保存的原文为准，不把缺失字段理解为没有许可。
