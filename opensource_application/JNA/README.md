# JNA

| 项目 | 内容 |
| --- | --- |
| 类型 | 构建工具依赖（本地接口访问） |
| 许可 | LGPL-2.1 / Apache-2.0 双许可 |
| 来源 | https://github.com/java-native-access/jna |
| 许可原文 | [upstream-build/](upstream-build/) |

## 作用

这些库只在 Gradle 构建 `bootJar` 时运行，由 Spring Boot Gradle 插件引入，不进入客户端、游戏节点或平台服务的运行类路径。项目代码不直接调用它们，也没有启用 Docker 镜像构建。

## 实际制品

| 制品 | 版本 |
| --- | --- |
| `net.java.dev.jna:jna` | 5.17.0 |
| `net.java.dev.jna:jna-platform` | 5.17.0 |

## 核对方法

全部制品的大小、SHA-256 和 POM 许可声明见 dependencies.json 的 `platform-build` 一节。
