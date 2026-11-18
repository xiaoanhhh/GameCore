# 第三方组件声明

每个组件的版本、来源、调用位置、传递依赖和许可全文见 [opensource_application/](opensource_application/README.md)。

## 桌面客户端

| 组件 | 版本 | 许可 | 用途 |
| --- | --- | --- | --- |
| libGDX（gdx、backend-lwjgl3、freetype） | 1.14.2 | Apache-2.0 | 窗口、渲染、输入、Scene2D 界面、字体栅格化 |
| LWJGL 3 | 随 libGDX | BSD-3-Clause | OpenGL / GLFW / OpenAL 原生绑定 |
| FreeType | 随 gdx-freetype | FreeType License | 字体栅格化 |
| JLayer | 1.0.1-gdx | LGPL-2.1 | 音频解码 |
| JOrbis | 0.0.17 | GNU LGPL（POM 未注明版本） | 音频解码 |
| Noto Sans SC（项目子集） | — | SIL OFL 1.1 | 界面中文字体 |
| Netty | 4.2.19.Final | Apache-2.0 | 在线对局连接（内含 JCTools，Apache-2.0） |
| Protocol Buffers Java | 4.34.2 | BSD-3-Clause | 对局协议编解码 |
| Jackson | 见登记 | Apache-2.0 | 平台 HTTP JSON |
| Eclipse Temurin（便携包运行时） | 21 | GPL-2.0 with Classpath Exception | 包内 Java 运行时，随包附带对应源码 |

## 游戏节点与平台服务

| 组件 | 版本 | 许可 | 用途 |
| --- | --- | --- | --- |
| Netty | 4.2.19.Final | Apache-2.0 | 游戏节点网络层 |
| Protocol Buffers Java | 4.34.2 | BSD-3-Clause | 对局协议 |
| Spring Boot / Framework / Security | 见登记 | Apache-2.0 | 平台服务 |
| Apache Tomcat（嵌入式） | 见登记 | Apache-2.0 | 平台 HTTP 容器 |
| Flyway | 见登记 | Apache-2.0 | 数据库结构迁移 |
| PostgreSQL JDBC | 见登记 | BSD-2-Clause | 数据库驱动 |
| HikariCP | 见登记 | Apache-2.0 | 数据库连接池 |
| Logback / SLF4J | 见登记 | EPL-2.0 / LGPL-2.1 双许可；MIT | 日志 |

## 素材

- 地图（TMX 与图块）、程序生成的图标与特效、大厅背景由本项目原创，来源记录见 [assets/README.md](assets/README.md)。
- 部分背景与角色精灵为工具生成，生成记录与提示词保存在 `assets/` 对应目录。
