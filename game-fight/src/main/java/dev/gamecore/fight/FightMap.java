package dev.gamecore.fight;

import java.util.List;
import static dev.gamecore.fight.FightArena.Plate;

/** 固定宽度的不可变对称场地；只包含规则几何/身份，不依赖图片、颜色、设备或文件。 */
public enum FightMap {
    NEON("霓虹天城", "摩天屋顶 · 悬空连廊", List.of(
            new Plate(0,300,650,120), new Plate(1,1590,1940,120), new Plate(2,850,1390,240),
            new Plate(3,580,900,360), new Plate(4,1340,1660,360))),
    TEMPLE("云巅武馆", "青瓦飞檐 · 廊桥石柱", List.of(
            new Plate(0,240,760,95), new Plate(1,1480,2000,95), new Plate(2,850,1390,210),
            new Plate(3,520,950,315), new Plate(4,1290,1720,315))),
    FORGE("熔火铸场", "熔炉顶盖 · 吊架铁柱", List.of(
            new Plate(0,300,720,140), new Plate(1,1520,1940,140), new Plate(2,800,1440,260),
            new Plate(3,600,950,400), new Plate(4,1290,1640,400))),
    BAMBOO("竹影溪谷", "竹楼栈道 · 溪石高台", List.of(
            new Plate(0,270,720,100),new Plate(1,1520,1970,100),new Plate(2,930,1310,190),
            new Plate(3,590,930,245),new Plate(4,1310,1650,245),new Plate(5,820,1080,365),new Plate(6,1160,1420,365))),
    FROST("霜月遗迹", "冰封断桥 · 古殿残柱", List.of(
            new Plate(0,400,700,130),new Plate(1,1540,1840,130),new Plate(2,940,1300,230),
            new Plate(3,650,910,275),new Plate(4,1330,1590,275),new Plate(5,830,1050,390),new Plate(6,1190,1410,390)));

    private final String title, feature;
    private final List<Plate> platforms;
    FightMap(String title, String feature, List<Plate> platforms) {
        this.title=title; this.feature=feature; this.platforms=List.copyOf(platforms);
    }
    public List<Plate> platforms() { return platforms; }
    public String feature() { return feature; }
    @Override public String toString() { return title; }
}
