# JSR305 Annotations

| 项目 | 内容 |
| --- | --- |
| 制品 | `com.google.code.findbugs:jsr305:3.0.2` |
| 类型 | 构建工具依赖（注解元数据） |
| 许可 | Apache-2.0，依据 [Maven Central POM](https://repo.maven.apache.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.pom) |
| 来源 | POM 登记的来源是 `https://code.google.com/p/jsr-305/`；Spring 构建清单登记的来源是 https://github.com/findbugsproject/findbugs |
| 许可原文 | [LICENSE](LICENSE)（Apache-2.0 标准全文，取自 OS Detector 官方许可文本，不是从该项目的历史仓库取回的文件） |

## 作用

构建期的注解元数据，有两条引入路径：

- `os-maven-plugin:1.7.1`（Protobuf 代码生成链）。
- Spring Boot Gradle 插件（`bootJar` 构建）。

项目代码不直接调用它，它也不进入客户端或服务器的运行类路径。

## 限制

这个旧注解依赖由上游构建工具带入，没有为它单独做漏洞扫描或替换。升级生成链时要确认是否仍然需要。
