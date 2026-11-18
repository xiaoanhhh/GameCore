package dev.gamecore.client.tank;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.utils.Disposable;
import dev.gamecore.tank.TankMap;
import dev.gamecore.tank.TmxArenaLoader;
import java.io.IOException;

/** 每个 Tank 页面独占一张地图及图块纹理；先用纯 Java 验证，再加载 libGDX 可视数据，切图/离页释放。 */
public final class TankMapAsset implements Disposable {
    public enum Arena {
        CROSSFIRE("crossfire", "交叉火线"), RUINS("ruins", "废墟回廊");
        private final String id;
        private final String label;
        Arena(String id, String label) { this.id = id; this.label = label; }
        public String path() { return "maps/tank/" + id + ".tmx"; }
        public static Arena fromId(String id) {
            for (var arena : values()) { if (arena.id.equals(id)) { return arena; } }
            throw new IllegalArgumentException("Unknown arena");
        }
        @Override public String toString() { return label; }
    }
    private final TankMap logic;
    private final TiledMap visual;
    private boolean disposed;
    public TankMapAsset(Arena arena) throws IOException {
        try (var stream = Gdx.files.internal(arena.path()).read()) { logic = TmxArenaLoader.load(stream); }
        visual = new TmxMapLoader().load(arena.path());
        try {
            var collision = (TiledMapTileLayer) visual.getLayers().get("collision");
            for (int y = 0; y < logic.height(); y++) {
                for (int x = 0; x < logic.width(); x++) {
                    if (logic.wall(x, y) != (collision.getCell(x, y) != null)) { throw new IllegalStateException("Visual/logic collision mismatch"); }
                }
            }
        } catch (RuntimeException failure) { visual.dispose(); throw failure; }
    }
    public TankMap logic() { return logic; }
    public TiledMap visual() { return visual; }
    public boolean isDisposed() { return disposed; }
    @Override public void dispose() { if (!disposed) { disposed = true; visual.dispose(); } }
}
