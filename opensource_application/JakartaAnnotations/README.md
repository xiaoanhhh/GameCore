# Jakarta Annotations

| 项目 | 内容 |
| --- | --- |
| 类型 | 平台服务运行依赖 |
| 许可 | EPL-2.0 / GPL-2.0 with Classpath Exception 双许可 |
| 来源 | [官方项目](https://github.com/jakartaee/common-annotations-api) |
| 许可原文 | [upstream/](upstream/) |

## 作用

Spring 和 Tomcat 使用的生命周期注解，属于传递依赖。

## 实际制品

| 制品 | 版本 |
| --- | --- |
| `jakarta.annotation:jakarta.annotation-api` | 3.0.0 |

## 调用位置

项目代码不直接调用。

## 限制

不定义任何游戏业务。功能测试通过不代表吞吐能力或公网安全已经验证。

## 核对方法

完整的 JAR 大小、SHA-256 和 POM 许可声明见 dependencies.json，可用 `.\gradlew.bat -I tools/platform-inventory.init.gradle platformInventory` 重新生成。POM 没有 license 字段时，以上游项目的许可和本目录保存的原文为准，不把缺失字段理解为没有许可。
