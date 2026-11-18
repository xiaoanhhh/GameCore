package dev.gamecore.tank;

import java.io.IOException;
import java.util.List;

/** 使用公开构造契约建立小型规则场景；正式地图测试直接读取打包的 TMX。 */
final class TankFixtures {
    private TankFixtures() { }
    static TankMap open(int width, int height, Vec2 first, Vec2 second) {
        return map(width, height, first, second, new boolean[width * height], List.of());
    }
    static TankMap map(int width, int height, Vec2 first, Vec2 second, boolean[] walls, List<TankMap.RepairZone> zones) {
        return new TankMap("test", width, height, walls,
                List.of(new TankMap.Spawn(first, 0), new TankMap.Spawn(second, 180)), zones);
    }
    static TankMap arena(String id) throws IOException {
        try (var stream = TankFixtures.class.getResourceAsStream("/maps/tank/" + id + ".tmx")) {
            if (stream == null) { throw new IOException("Missing map"); }
            return TmxArenaLoader.load(stream);
        }
    }
    static void tick(TankGame game, int count) { for (int i = 0; i < count; i++) { game.tick(); } }
}
