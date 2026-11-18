package dev.gamecore.server;

import dev.gamecore.tank.TankMap;
import dev.gamecore.tank.Vec2;
import java.util.List;

/** 测试类路径中的同包桥，复用已有包级受信地图注入；不向生产协议开放上传地图/改出生点。 */
public final class ClientTestServer {
    private ClientTestServer() { }
    public static TankMap openMap() {
        return new TankMap("test-arena", 12, 8, new boolean[96], List.of(
                new TankMap.Spawn(new Vec2(2.5, 4.5), 0), new TankMap.Spawn(new Vec2(9.5, 4.5), 180)), List.of());
    }
    public static NettyGameServer start(TankMap map) throws InterruptedException { return NettyGameServer.start(ServerSettings.defaults(0), map); }
}
