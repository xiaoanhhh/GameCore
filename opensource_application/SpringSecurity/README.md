# Spring Security

| 项目 | 内容 |
| --- | --- |
| 类型 | 平台服务运行依赖 |
| 许可 | Apache-2.0 |
| 来源 | [官方项目](https://github.com/spring-projects/spring-security) |
| 许可原文 | [upstream/](upstream/) |

## 作用

平台服务的访问控制：`SecurityFilterChain`、`PasswordEncoder` 和 Bearer 身份。

## 实际制品

| 制品 | 版本 |
| --- | --- |
| `org.springframework.security:spring-security-config` | 7.1.2 |
| `org.springframework.security:spring-security-core` | 7.1.2 |
| `org.springframework.security:spring-security-crypto` | 7.1.2 |
| `org.springframework.security:spring-security-web` | 7.1.2 |

## 调用位置

`SecurityConfiguration`（接口权限，internal 接口只开放给 SERVER 角色）、`AuthService`（密码编码）。

## 限制

密码哈希使用框架提供的实现，项目不自行实现。功能测试通过不代表吞吐能力或公网安全已经验证。

## 核对方法

完整的 JAR 大小、SHA-256 和 POM 许可声明见 dependencies.json，可用 `.\gradlew.bat -I tools/platform-inventory.init.gradle platformInventory` 重新生成。POM 没有 license 字段时，以上游项目的许可和本目录保存的原文为准，不把缺失字段理解为没有许可。
