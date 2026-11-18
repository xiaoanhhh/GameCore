package dev.gamecore.client.fight;

import dev.gamecore.fight.FighterKind;
import dev.gamecore.fight.FightSnapshot.Action;

/** 实测 1254×1254 图集的完整人物边界与身体锚点；生成图并非严格等格，不能平均切片。 */
public final class FightSpriteLayout {
    public static final String ASSET = "fighters/fighters-reference-v2.png";
    public static final int WIDTH = 1254;
    public static final int HEIGHT = 1254;
    private FightSpriteLayout() { }
    public record Frame(int x, int y, int width, int height, int pivotX) { }
    private static final Frame[] FRAMES = {
        new Frame(112, 16, 263, 291, 131), new Frame(489, 16, 318, 291, 134), new Frame(885, 16, 248, 291, 121),
        new Frame(96, 320, 288, 295, 140), new Frame(476, 324, 339, 291, 141), new Frame(902, 338, 276, 278, 142),
        new Frame(115, 621, 265, 348, 132), new Frame(504, 621, 316, 348, 129), new Frame(893, 621, 265, 348, 132),
        new Frame(112, 969, 284, 277, 139), new Frame(511, 970, 310, 276, 134), new Frame(909, 980, 275, 269, 141)
    };
    public static Frame frame(int index) { return FRAMES[index]; }
    public static int index(FighterKind kind, Action action) {
        return (kind == FighterKind.NAIWA ? 0 : 6) + switch (action) {
            case IDLE, WALK, RUN, SKID, DASH, JUMP_UP, JUMP_APEX, JUMP_FALL, DOUBLE_JUMP, LAND, WIN, GETUP, LAUGH -> 0;
            case PUNCH, PUNCH_2, PUNCH_3, AIR_PUNCH, UPPERCUT -> 1;
            case GUARD, GUARD_HIT -> 2; case RANGED,TELEPORT -> 3; case ULTIMATE, ULT_RELEASE -> 4;
            case HURT, HURT_HEAVY, KNOCKDOWN, LOSE,WEB_BOUND -> 5;
        };
    }

    /** V3 数据使用现有 libGDX JSON 读取；每个区域显式验证，失败时由绘制层回退旧资源。 */
    public record Clip(String sheet, FighterKind kind, String group, int pose, int x, int y, int width, int height, float pivotX) { }
    public static java.util.List<Clip> read(String json) {
        return read(json, null);
    }
    /** 拳击补充图集独立验证，保留原 V3 数据格式和异常回退边界。 */
    public static java.util.List<Clip> readBoxing(String json) {
        return read(json, "boxing");
    }
    public static java.util.List<Clip> readGreetings(String json) { return read(json, "greetings"); }
    public static java.util.List<Clip> readVictory(String json) { return read(json, "victory"); }
    public static java.util.List<Clip> readLaugh(String json) { return read(json, "laugh"); }
    private static java.util.List<Clip> read(String json, String extra) {
        var root = new com.badlogic.gdx.utils.JsonReader().parse(json);
        var result = new java.util.ArrayList<Clip>();
        for (var node : root.get("frames")) {
            Clip c = new Clip(node.getString("sheet"), FighterKind.valueOf(node.getString("kind")), node.getString("group"),
                    node.getInt("pose"), node.getInt("x"), node.getInt("y"), node.getInt("width"), node.getInt("height"), node.getFloat("pivotX"));
            boolean validSheet = extra != null ? c.sheet.equals("fighters/fighters-" + extra + "-v1.png") : c.sheet.startsWith("fighters/fighters-v3-");
            boolean validGroup = extra != null ? c.group.equals(extra) : c.group.equals("locomotion") || c.group.equals("combat") || c.group.equals("air-states");
            if (!validSheet || "laugh".equals(extra)&&c.kind!=FighterKind.NAIWA || c.sheet.contains("..") || c.pose < 0 || c.pose > 7 || !Float.isFinite(c.pivotX)
                    || !validGroup || c.x < 0 || c.y < 0 || c.width < 1 || c.height < 1
                    || c.x + c.width > node.getInt("sheetWidth") || c.y + c.height > node.getInt("sheetHeight") || c.pivotX < 0 || c.pivotX > c.width) {
                throw new IllegalArgumentException("Invalid fighter frame");
            }
            if (result.stream().anyMatch(v -> v.kind == c.kind && v.group.equals(c.group) && v.pose == c.pose)) { throw new IllegalArgumentException("Duplicate fighter pose"); }
            result.add(c);
        }
        if (result.size() != ("laugh".equals(extra)?8:extra != null ? 16 : 48)) { throw new IllegalArgumentException("Unexpected fighter pose count"); }
        return java.util.List.copyOf(result);
    }
}
