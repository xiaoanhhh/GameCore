package dev.gamecore.fight;

import java.util.List;

/** 不可变单向平台；全部高度以地面脚底 0 为基准，画面基线不进入物理计算。 */
public final class FightArena {
    public record Plate(int id, double left, double right, double height) {
        public boolean contains(double x) { return x >= left && x <= right; }
    }
    /** 旧调用的默认场地入口保留；真实会话必须使用配置/快照中的地图列表。 */
    public static final List<Plate> PLATFORMS = FightMap.NEON.platforms();
    private FightArena() { }
    /** 下落时取本步穿越的最高支撑面；从下往上完全不阻挡，穿台只忽略原支撑台。 */
    public static int landing(double x, double previous, double next, int ignored) {
        return landing(PLATFORMS,x,previous,next,ignored);
    }
    public static int landing(List<Plate> platforms, double x, double previous, double next, int ignored) {
        int result = -1; double highest = -1;
        if (next > previous) { return result; }
        for (Plate p : platforms) {
            if (p.id != ignored && p.contains(x) && previous >= p.height && next <= p.height && p.height > highest) {
                result = p.id; highest = p.height;
            }
        }
        return result;
    }
}
