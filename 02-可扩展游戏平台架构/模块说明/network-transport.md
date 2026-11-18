# network-transport：共享异步传输

> 扩展角色：**传输层**。客户端与服务器共用的帧、IO、TLS 与有界发送；与游戏种类无关。

## 职责

- 长度帧 Protobuf 管线、Netty IO 资源、TLS（JDK JSSE，校验 CA 与 SAN，无跳过选项）。
- [AsyncOutbound](../../network-transport/src/main/java/dev/gamecore/network/transport/AsyncOutbound.java)：可靠消息有界队列（容量 128，溢出触发 fatal 回调由连接方断开）+ 快照仅保留最新一份（新快照覆盖未发送的旧快照）。
- [GameNetworkConnection](../../network-transport/src/main/java/dev/gamecore/network/transport/GameNetworkConnection.java)：客户端连接，收包转为不可变消息交给渲染线程。

## 在扩展体系中的位置

新增游戏**不修改**本模块。它让 client 不必依赖 game-server 也能复用同一套传输代码，保持“宿主之间互不依赖”。

## 代码组织

| 包 | 内容 |
| --- | --- |
| `dev.gamecore.network.transport` | `GameNetworkConnection`、`AsyncOutbound`、`ProtocolPipeline`、`ProtocolMessages`、`IoResources`、`TransportTls` |
| `dev.gamecore.network.transport`（诊断） | `TrafficObserver` 只读观察端口、`TrafficTapHandler` 旁路处理器：安装在编解码之后，原样透传消息，隔离观察方异常；只在开发账户登录时安装，服务器不使用 |
| `dev.gamecore.network.experiment` | 网络故障模拟（延迟/丢包），仅显式开发开关 + 回环目标启用，正式连接默认无注入 |

## 依赖

运行/编译（`api`）：`protocol`、Netty（transport、codec、handler、resolver-dns）。

## 验证

```powershell
.\gradlew.bat :network-transport:test --console=plain
```
