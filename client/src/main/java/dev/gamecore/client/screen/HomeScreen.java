package dev.gamecore.client.screen;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import dev.gamecore.api.GameDescriptor;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.ui.ArenaArt;
import dev.gamecore.client.ui.GameCard;
import dev.gamecore.client.ui.LobbyPalette;
import dev.gamecore.client.ui.ThemeManager;
import java.util.List;

/** 游戏目录首页：卡片、封面、搜索和空结果状态都由注册目录驱动。 */
public final class HomeScreen extends UiScreen {
    private final Table cards = new Table();
    private final TextField search;
    private final Label count;

    public HomeScreen(GameCoreClient app) {
        super(app, "探索游戏");
        // 首屏层级：带信号绿脊线的横幅（题眼 + 两个入口 + 目录统计）→ 游戏库标题与搜索 → 三张游戏卡片。
        content.add(hero()).growX().height(204).row();
        Table section = new Table();
        section.add(new Image(theme.solid(ThemeManager.BLUE))).size(4, 20).padRight(12).bottom().padBottom(3);
        section.add(theme.label("游戏库", theme.assets.heading, ThemeManager.TEXT)).left().bottom();
        count = theme.caption("");
        section.add(count).padLeft(12).padBottom(3).expandX().left().bottom();
        search = focus(theme.searchField(), "search");
        search.setFocusTraversal(false);
        search.setMaxLength(60);
        search.setProgrammaticChangeEvents(true);
        section.add(search).width(280).height(40);
        content.add(section).growX().padTop(20).row();
        cards.top();
        ScrollPane library = new ScrollPane(cards, theme.scrollStyle());
        library.setScrollingDisabled(true, false);
        library.setFadeScrollBars(false);
        stage.setScrollFocus(library);
        content.add(library).growX().height(284).padTop(14).top().row();
        if (!app.startupNotice().isBlank()) {
            Label notice = theme.caption(app.startupNotice());
            notice.setWrap(true);
            content.add(notice).growX().padTop(8).row();
        }
        content.add().expandY();
        search.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { refreshCards(); }
        });
        refreshCards();
    }

    /** 横幅：左侧题眼与入口（主按钮进入推荐游戏，次按钮直达在线平台），右侧是由注册目录计算的统计数字。 */
    private Table hero() {
        Table copy = new Table();
        copy.top().left();
        Table eyebrow = new Table();
        eyebrow.add(new Image(theme.solid(ThemeManager.BLUE))).size(8, 8).padRight(10);
        eyebrow.add(theme.label("GAMECORE ARCADE   ·   原创双人对战", theme.assets.small, ThemeManager.BLUE));
        copy.add(eyebrow).left().row();
        copy.add(theme.label("挑一局，即刻开打。", theme.assets.hero, ThemeManager.TEXT)).left().padTop(12).row();
        copy.add(theme.label("同屏双人、人机对练或在线联机，随时开局，随时重来。", theme.assets.body, ThemeManager.MUTED)).left().padTop(8).row();
        Table actions = new Table();
        actions.add(bind(theme.button("快速开始  ·  Snake Arena   →", true), "featured-snake", () -> app.detail("snake"))).width(296).height(44).padRight(12);
        actions.add(bind(theme.button("在线平台", false), "open-platform", app::openPlatform)).width(128).height(44);
        copy.add(actions).left().padTop(20);
        List<GameDescriptor> all = app.registry().all();
        Table stats = new Table();
        stat(stats, Integer.toString(all.size()), "款原创游戏", ThemeManager.TEXT, false);
        stat(stats, Integer.toString(all.stream().mapToInt(game -> game.modes().size()).sum()), "种玩法模式", ThemeManager.BLUE, true);
        stat(stats, "2P", "本地 / 在线", ThemeManager.TEXT, true);
        Table hero = new Table();
        hero.setName("home-hero");
        hero.setBackground(theme.surface(LobbyPalette.PANEL, LobbyPalette.LINE, 1, LobbyPalette.SIGNAL));
        hero.pad(26, 34, 26, 30);
        hero.add(copy).expand().fill().left();
        hero.add(stats).right().bottom().padBottom(4);
        return hero;
    }

    /** 统计格：大号数字 + 说明；除首格外左侧有 1px 竖分隔线。 */
    private void stat(Table stats, String value, String caption, com.badlogic.gdx.graphics.Color color, boolean divider) {
        if (divider) { stats.add(new Image(theme.solid(ThemeManager.LINE))).width(1).height(56).padLeft(22).padRight(22); }
        Table cell = new Table();
        cell.add(theme.label(value, theme.assets.hero, color)).left().row();
        cell.add(theme.caption(caption)).left().padTop(2);
        stats.add(cell).left();
    }

    private void refreshCards() {
        forgetFocus(cards);
        cards.clearChildren();
        List<GameDescriptor> games = app.registry().search(search.getText());
        count.setText(games.size() + " 款游戏");
        if (games.isEmpty()) {
            Table empty = new Table();
            empty.setBackground(theme.panel(ThemeManager.PANEL));
            empty.setName("empty-search");
            empty.add(theme.label("没有找到匹配的游戏", theme.assets.heading, ThemeManager.TEXT)).row();
            empty.add(theme.caption("试试 Snake、Tank、终极格斗或角色名称。")).padTop(12);
            cards.add(empty).grow().height(270).colspan(3);
            return;
        }
        int index = 0;
        for (GameDescriptor game : games) {
            GameCard card = new GameCard(theme, game);
            bind(card, "game-" + game.id(), () -> app.detail(game.id()));
            cards.add(card).growX().uniformX().minWidth(0).height(272).spaceRight(index % 3 < 2 ? 18 : 0).spaceBottom(12);
            if (++index % 3 == 0) { cards.row(); }
        }
        while(index%3!=0){cards.add().growX().uniformX().minWidth(0);index++;}
    }

    @Override protected boolean handleKey(int keycode) {
        if (keycode == Input.Keys.ENTER && stage.getKeyboardFocus() == search) {
            List<GameDescriptor> matches = app.registry().search(search.getText());
            if (!matches.isEmpty()) { app.detail(matches.getFirst().id()); }
            return true;
        }
        return false;
    }
}
