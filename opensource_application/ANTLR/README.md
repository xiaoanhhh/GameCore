# ANTLR Runtime

| 项目 | 内容 |
| --- | --- |
| 类型 | 构建工具依赖（解析支持） |
| 许可 | BSD-3-Clause |
| 来源 | https://github.com/antlr/antlr4 |
| 许可原文 | [LICENSE.txt](LICENSE.txt) |

## 作用

这些库只在 Gradle 构建 `bootJar` 时运行，由 Spring Boot Gradle 插件引入，不进入客户端、游戏节点或平台服务的运行类路径。项目代码不直接调用它们，也没有启用 Docker 镜像构建。

## 实际制品

| 制品 | 版本 |
| --- | --- |
| `org.antlr:antlr4-runtime` | 4.7.2 |

## 核对方法

全部制品的大小、SHA-256 和 POM 许可声明见 dependencies.json 的 `platform-build` 一节。
