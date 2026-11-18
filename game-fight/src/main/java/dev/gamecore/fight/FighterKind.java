package dev.gamecore.fight;

/** 两位可选角色及技能身份；基础移动/普通拳共享，大招与肥嘟嘟闪袭有独立规则时序。 */
public enum FighterKind {
    NAIWA("奶蛙", "闪电波", "九天雷引"),
    FEIDUDU("肥嘟嘟", "蜘蛛丝", "八方蛛狱");

    private final String label;
    private final String ranged;
    private final String ultimate;
    FighterKind(String label, String ranged, String ultimate) {
        this.label = label; this.ranged = ranged; this.ultimate = ultimate;
    }
    public String ranged() { return ranged; }
    public String ultimate() { return ultimate; }
    @Override public String toString() { return label; }
}
