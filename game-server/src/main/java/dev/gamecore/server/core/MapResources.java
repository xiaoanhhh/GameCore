package dev.gamecore.server.core;

import dev.gamecore.tank.TankMap;
import dev.gamecore.tank.TmxArenaLoader;
import dev.gamecore.tank.TankMapFingerprint;
import java.io.IOException;

/** 启动控制面加载受信classpath地图并计算规范化逻辑摘要；Tick不读文件/图片。 */
public final class MapResources {
    private MapResources() { }
    public static TankMap load(String id) throws IOException {
        if (!(id.equals("crossfire") || id.equals("ruins"))) { throw new IllegalArgumentException("Unknown map"); }
        try (var input = MapResources.class.getResourceAsStream("/maps/tank/" + id + ".tmx")) {
            if (input == null) { throw new IOException("Packaged map missing: " + id); }
            return TmxArenaLoader.load(input);
        }
    }
    public static String digest(TankMap map) {
        return TankMapFingerprint.digest(map);
    }
}
