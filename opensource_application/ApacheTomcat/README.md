# Apache Tomcat（嵌入式）

| 项目 | 内容 |
| --- | --- |
| 类型 | 平台服务运行依赖 |
| 许可 | Apache-2.0 |
| 来源 | [官方项目](https://tomcat.apache.org/) |
| 许可原文 | [upstream/](upstream/) |

## 作用

Spring Boot 内嵌的 HTTP / Servlet 容器，承载平台服务的全部 REST 接口。

## 实际制品

| 制品 | 版本 |
| --- | --- |
| `org.apache.tomcat.embed:tomcat-embed-core` | 11.0.27 |
| `org.apache.tomcat.embed:tomcat-embed-el` | 11.0.27 |
| `org.apache.tomcat.embed:tomcat-embed-websocket` | 11.0.27 |

## 调用位置

配置在 `platform-service/src/main/resources/application.properties`，最大处理线程数为 32。

## 限制

Tomcat 线程只处理平台请求，和游戏节点的 Netty EventLoop 相互隔离。功能测试通过不代表吞吐能力或公网安全已经验证。

## 核对方法

完整的 JAR 大小、SHA-256 和 POM 许可声明见 dependencies.json，可用 `.\gradlew.bat -I tools/platform-inventory.init.gradle platformInventory` 重新生成。POM 没有 license 字段时，以上游项目的许可和本目录保存的原文为准，不把缺失字段理解为没有许可。
