package dev.gamecore.tank;

import java.util.List;
import java.util.Arrays;
import java.util.Objects;

/** 脱离 Tiled 和图形后端的地图值对象；碰撞格、出生点和维修区域可直接供无图形模拟使用。 */
public final class TankMap {
    public record Spawn(Vec2 position, double heading) {
        public Spawn {
            Objects.requireNonNull(position);
            if (!Double.isFinite(heading)) { throw new IllegalArgumentException("Invalid heading"); }
        }
    }
    public record RepairZone(double x, double y, double width, double height) {
        public RepairZone {
            if (!Double.isFinite(x + y + width + height) || x < 0 || y < 0 || width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Invalid repair zone");
            }
        }
        public boolean contains(Vec2 position) {
            return position.x() >= x && position.x() <= x + width && position.y() >= y && position.y() <= y + height;
        }
    }
    private final String id;
    private final int width;
    private final int height;
    private final boolean[] walls;
    private final List<Spawn> spawns;
    private final List<RepairZone> repairZones;

    public TankMap(String id, int width, int height, boolean[] walls, List<Spawn> spawns, List<RepairZone> zones) {
        this.id = Objects.requireNonNull(id);
        if (id.isBlank() || width < 4 || height < 4 || width > 128 || height > 128 || walls.length != width * height) {
            throw new IllegalArgumentException("Invalid map size or id");
        }
        this.width = width; this.height = height; this.walls = walls.clone();
        this.spawns = List.copyOf(spawns); repairZones = List.copyOf(zones);
        if (spawns.size() != 2) { throw new IllegalArgumentException("Exactly two spawns required"); }
        for (Spawn spawn : spawns) {
            if (blocked(spawn.position(), TankGame.TANK_RADIUS)) { throw new IllegalArgumentException("Blocked spawn"); }
        }
        if (spawns.get(0).position().distanceSquared(spawns.get(1).position()) <= 4 * TankGame.TANK_RADIUS * TankGame.TANK_RADIUS) {
            throw new IllegalArgumentException("Overlapping spawns");
        }
        for (RepairZone zone : zones) {
            if (zone.x() + zone.width() > width || zone.y() + zone.height() > height) { throw new IllegalArgumentException("Zone outside map"); }
        }
    }
    /** 回放比较采用地图内容值语义；两个独立加载的同内容地图也必须相等。 */
    @Override public boolean equals(Object other) {
        return other instanceof TankMap map && id.equals(map.id) && width == map.width && height == map.height
                && Arrays.equals(walls, map.walls) && spawns.equals(map.spawns) && repairZones.equals(map.repairZones);
    }
    @Override public int hashCode() { return 31 * Objects.hash(id, width, height, spawns, repairZones) + Arrays.hashCode(walls); }
    public String id() { return id; }
    public int width() { return width; }
    public int height() { return height; }
    public List<Spawn> spawns() { return spawns; }
    public List<RepairZone> repairZones() { return repairZones; }
    public boolean wall(int x, int y) { return x < 0 || y < 0 || x >= width || y >= height || walls[y * width + x]; }

    /** 圆与格子 AABB 的最近点检测；外边界也视为不可通过，不依赖渲染层是否画墙。 */
    public boolean blocked(Vec2 point, double radius) {
        if (!Double.isFinite(radius) || radius < 0) { throw new IllegalArgumentException("Invalid radius"); }
        if (point.x() - radius < 0 || point.y() - radius < 0 || point.x() + radius > width || point.y() + radius > height) { return true; }
        for (int y = (int) StrictMath.floor(point.y() - radius); y <= (int) StrictMath.floor(point.y() + radius); y++) {
            for (int x = (int) StrictMath.floor(point.x() - radius); x <= (int) StrictMath.floor(point.x() + radius); x++) {
                if (!wall(x, y)) { continue; }
                double dx = point.x() - Math.clamp(point.x(), x, x + 1.0);
                double dy = point.y() - Math.clamp(point.y(), y, y + 1.0);
                if (dx * dx + dy * dy < radius * radius || radius == 0) { return true; }
            }
        }
        return false;
    }
}
