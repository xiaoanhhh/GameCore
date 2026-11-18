# Checker Framework（checker-qual）

| 项目 | 内容 |
| --- | --- |
| 类型 | 平台服务运行依赖 |
| 许可 | MIT（checker-qual制品） |
| 来源 | [官方项目](https://github.com/typetools/checker-framework) |
| 许可原文 | [upstream/](upstream/) |

## 作用

PostgreSQL JDBC 携带的注解库，属于传递依赖。

## 实际制品

| 制品 | 版本 |
| --- | --- |
| `org.checkerframework:checker-qual` | 3.55.1 |

## 调用位置

项目代码不直接调用。

## 限制

只有注解，没有引入 Checker 编译器或注解处理器。功能测试通过不代表吞吐能力或公网安全已经验证。

## 核对方法

完整的 JAR 大小、SHA-256 和 POM 许可声明见 dependencies.json，可用 `.\gradlew.bat -I tools/platform-inventory.init.gradle platformInventory` 重新生成。POM 没有 license 字段时，以上游项目的许可和本目录保存的原文为准，不把缺失字段理解为没有许可。
