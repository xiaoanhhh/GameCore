# platform-api：稳定平台 HTTP 契约

> 扩展角色：**平台契约**。客户端与游戏节点访问平台的值类型与 HTTP 客户端；不引入 Spring 或 JDBC。

## 职责

- [PlatformMessages](../../platform-api/src/main/java/dev/gamecore/platform/api/PlatformMessages.java)：账户、大厅、房间、票据（含游戏 ID、展示/规则版本、可选 TLS）、结果等 JSON 值。
- [PlatformHttpClient](../../platform-api/src/main/java/dev/gamecore/platform/api/PlatformHttpClient.java)：有界超时客户端；公网地址强制 HTTPS。

诊断端口：`PlatformHttpClient.ExchangeObserver` 只提供方法、路径、状态码、错误码和耗时，接触不到请求头、请求体和响应体；观察方抛出的异常不改变请求结果。默认不设置。

## 在扩展体系中的位置

消息中的游戏只以字符串 ID 与版本出现，没有按游戏的类型。新增游戏**不修改**本模块。它把平台业务（Spring）与宿主（client、game-server）隔开，使规则与 Tick 不接触平台依赖。

## 依赖

运行/编译（`api`）：Jackson 3（`jackson-databind`，BOM 3.1.7）。被 client、game-server、platform-service 依赖。

## 验证

```powershell
.\gradlew.bat :platform-api:test --console=plain
```
