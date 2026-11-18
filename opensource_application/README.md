# 开源项目使用详情

项目使用的每个开源项目都有独立目录，记录版本、许可、来源、调用位置和限制，并保存许可全文和传递依赖材料。所有依赖的汇总见[依赖清单](依赖清单.md)，发行包随附的声明见 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。

## 按用途分类

| 用途 | 项目 |
| --- | --- |
| 桌面客户端 | libGDX、LWJGL、FreeType、NotoSansSC、JLayer、JOrbis、gdx-jnigen |
| 网络与协议 | Netty（内含 JCTools）、Protobuf |
| 在线平台 | SpringBoot、SpringFramework、SpringSecurity、ApacheTomcat、Jackson、Flyway、PostgreSQL-JDBC、HikariCP，以及日志和其他传递组件 |
| 构建与测试 | Gradle、JUnit 及其支持组件、Protobuf-Gradle-Plugin、Git、GitHub-Actions、gdx-liftoff |
| 部署与运维 | OpenJDK、PostgreSQL、Nginx、Certbot、systemd、OpenSSL、Python |
| 素材工具 | FontTools、Python、Tiled 地图格式 |

各模块的 `build.gradle` 是直接依赖的来源，实际解析结果要区分运行、测试和构建三类，核对方法见[依赖清单](依赖清单.md#怎样核对)。

素材自身的来源记录保存在 `assets/` 内。角色形象的公开分发权尚待确认。项目自身的开源许可由所有者决定。

## 分项目入口

| 项目 | 说明 |
| --- | --- |
| ANTLR | [使用说明](ANTLR/README.md) |
| ApacheCommonsCodec | [使用说明](ApacheCommonsCodec/README.md) |
| ApacheCommonsCompress | [使用说明](ApacheCommonsCompress/README.md) |
| ApacheCommonsIO | [使用说明](ApacheCommonsIO/README.md) |
| ApacheCommonsLang | [使用说明](ApacheCommonsLang/README.md) |
| ApacheCommonsLogging | [使用说明](ApacheCommonsLogging/README.md) |
| ApacheHttpComponents | [使用说明](ApacheHttpComponents/README.md) |
| ApacheLog4j | [使用说明](ApacheLog4j/README.md) |
| ApacheTomcat | [使用说明](ApacheTomcat/README.md) |
| API-Guardian | [使用说明](API-Guardian/使用说明.md) |
| Certbot | [使用说明](Certbot/使用说明.md) |
| CheckerFramework | [使用说明](CheckerFramework/README.md) |
| Flyway | [使用说明](Flyway/README.md) |
| FontTools | [使用说明](FontTools/使用说明.md) |
| FreeType | [使用说明](FreeType/使用说明.md) |
| gdx-jnigen | [使用说明](gdx-jnigen/使用说明.md) |
| gdx-liftoff | [使用说明](gdx-liftoff/使用说明.md) |
| Git | [使用说明](Git/使用说明.md) |
| GitHub-Actions | [使用说明](GitHub-Actions/使用说明.md) |
| Gradle | [使用说明](Gradle/使用说明.md) |
| HikariCP | [使用说明](HikariCP/README.md) |
| Jackson | [使用说明](Jackson/README.md) |
| JakartaAnnotations | [使用说明](JakartaAnnotations/README.md) |
| JCTools | [使用说明](JCTools/使用说明.md) |
| JLayer | [使用说明](JLayer/使用说明.md) |
| JNA | [使用说明](JNA/README.md) |
| JOrbis | [使用说明](JOrbis/使用说明.md) |
| JSpecify | [使用说明](JSpecify/README.md) |
| JSR305 | [使用说明](JSR305/README.md) |
| JUnit | [使用说明](JUnit/接口与使用说明.md) |
| libGDX | [使用说明](libGDX/接口与使用说明.md) |
| Logback | [使用说明](Logback/README.md) |
| LWJGL | [使用说明](LWJGL/使用说明.md) |
| Micrometer | [使用说明](Micrometer/README.md) |
| Netty | [使用说明](Netty/使用说明.md) |
| Nginx | [使用说明](Nginx/使用说明.md) |
| NotoSansSC | [使用说明](NotoSansSC/使用说明.md) |
| OpenJDK | [使用说明](OpenJDK/使用说明.md) |
| OpenSSL | [使用说明](OpenSSL/使用说明.md) |
| OpenTest4J | [使用说明](OpenTest4J/使用说明.md) |
| OS-Maven-Plugin | [使用说明](OS-Maven-Plugin/使用说明.md) |
| OSDetector-Gradle-Plugin | [使用说明](OSDetector-Gradle-Plugin/使用说明.md) |
| PostgreSQL | [使用说明](PostgreSQL/README.md) |
| PostgreSQL-JDBC | [使用说明](PostgreSQL-JDBC/README.md) |
| Protobuf | [使用说明](Protobuf/使用说明.md) |
| Protobuf-Gradle-Plugin | [使用说明](Protobuf-Gradle-Plugin/使用说明.md) |
| Python | [使用说明](Python/使用说明.md) |
| SLF4J | [使用说明](SLF4J/README.md) |
| SnakeYAML | [使用说明](SnakeYAML/README.md) |
| SpringBoot | [使用说明](SpringBoot/README.md) |
| SpringDependencyManagementPlugin | [使用说明](SpringDependencyManagementPlugin/README.md) |
| SpringFramework | [使用说明](SpringFramework/README.md) |
| SpringSecurity | [使用说明](SpringSecurity/README.md) |
| systemd | [使用说明](systemd/使用说明.md) |
| Tiled | [使用说明](Tiled/使用说明.md) |
| TOMLJ | [使用说明](TOMLJ/README.md) |
