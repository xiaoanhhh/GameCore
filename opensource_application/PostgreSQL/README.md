# PostgreSQL

| 项目 | 内容 |
| --- | --- |
| 版本 | 18.6 |
| 类型 | 平台服务的数据库，不随发行包分发 |
| 许可 | [PostgreSQL License](https://www.postgresql.org/about/licence/) |
| 来源 | [官方下载页](https://www.postgresql.org/download/windows/) → [EDB 二进制](https://www.enterprisedb.com/download-postgresql-binaries) |

## 使用方式

| 环境 | 方式 |
| --- | --- |
| 本机测试 | 官方指向的 EDB Windows x64 二进制，zip SHA-256 `E2246BA91D22345BC3D017586C09EDE52D9DF180B1EEB480F050445F1CAD84E2`。只在 `.cache/platform-postgres` 运行隔离的测试库，不安装系统服务 |
| 云主机 | PGDG 软件源的 PostgreSQL 18.6，只监听回环地址 |
| Docker | `compose.platform.yaml` 提供官方 `postgres:18.6` 配置，但开发机的 Docker 服务不可用，所以**没有验证过** |

数据库结构入口是 `platform-service/src/main/resources/db/migration/V1__platform.sql`。访问经 JDBC 和 HikariCP，连接池 8 个连接。房间和战绩的幂等由事务锁和唯一约束保证。游戏 Tick 不访问数据库。

## 限制

- EDB 二进制包含它自己打包的运行库，如果分发要保留该上游包的全部许可。本项目不分发。
- 不把可选扩展当作已使用的功能。
- 升级数据库要先做备份和恢复演练。2 GiB 机器上完整平台的承载能力没有验证。
