package dev.gamecore.client.registry;

import dev.gamecore.api.GameDescriptor;
import dev.gamecore.api.GameMode;
import dev.gamecore.api.GameModule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 手动注册、稳定排序的游戏目录；UI 根据列表生成卡片，不扫描或加载外部 JAR。 */
public final class GameRegistry {
    private final Map<String, GameModule> modules = new LinkedHashMap<>();

    public void register(GameModule module) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(module.descriptor(), "descriptor");
        if (modules.putIfAbsent(module.id(), module) != null) {
            throw new IllegalArgumentException("Duplicate game id: " + module.id());
        }
    }

    public GameDescriptor require(String id) {
        GameModule module = modules.get(id);
        if (module == null) {
            throw new IllegalArgumentException("Unknown game: " + id);
        }
        return module.descriptor();
    }

    public List<GameDescriptor> all() {
        return modules.values().stream().map(GameModule::descriptor).toList();
    }

    public List<GameDescriptor> search(String query) {
        String term = Objects.requireNonNull(query, "query").strip().toLowerCase(Locale.ROOT);
        return all().stream().filter(game ->
                (game.id() + " " + game.name() + " " + game.subtitle() + " " + game.summary())
                        .toLowerCase(Locale.ROOT).contains(term)).toList();
    }

    public static GameRegistry builtIn() {
        GameRegistry registry = new GameRegistry();
        registry.register(() -> new GameDescriptor("snake", "Snake Arena", "贪吃蛇竞技场",
                "吃食物、躲障碍，也能双人同场竞技。", "在 20 × 20 的网格中寻找路线，吃食物成长。\n挑战经典、障碍，或与身边的朋友同场竞技。",
                List.of(new GameMode("classic", "经典模式", "纯粹的网格与成长，重温街机乐趣。"),
                        new GameMode("obstacles", "障碍挑战", "在墙壁之间寻找路线，挑战空间判断。"),
                        new GameMode("duo", "本地双人", "与身边的朋友共享一片竞技场。"),
                        new GameMode("multiplayer", "在线双人", "填写服务器地址，与朋友在线竞技。"))));
        registry.register(() -> new GameDescriptor("tank", "Tank Arena", "坦克竞技场",
                "与朋友本地或在线对战，也可挑战电脑。", "驾驶坦克寻找掩体，瞄准并击毁对手。\n本地双人、人机与在线双人，地图由当前模式决定。",
                List.of(new GameMode("duel", "本地对战", "双人同屏，面对面展开较量。"),
                        new GameMode("bot", "人机竞技", "从轻松练习到进阶挑战，三种难度可选。"),
                        new GameMode("online", "直连对战", "填写地址连接坦克专用服务器。"),
                        new GameMode("multiplayer", "直连房间", "填写地址，加入多游戏服务器的房间。"))));
        registry.register(() -> new GameDescriptor("fight", "终极格斗", "ULTIMATE FIGHT · 奶蛙 VS 肥嘟嘟",
                "出拳、闪电波与蜘蛛丝，开启热血对决。", "选择奶蛙或肥嘟嘟，挑战电脑或与朋友同屏格斗。\n移动跳跃、出拳格挡，用满能量大招终结对决。",
                List.of(new GameMode("bot", "人机竞技", "选择双方角色，与电脑切磋格斗。"),
                        new GameMode("duel", "本地双人", "共用键盘，双人同屏一决胜负。"),
                        new GameMode("multiplayer", "在线双人", "填写服务器地址，与朋友在线切磋。"))));
        return registry;
    }
}
