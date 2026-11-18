package dev.gamecore.client.screen;

import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import dev.gamecore.api.GameDescriptor;
import dev.gamecore.api.GameMode;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.ui.ArenaArt;
import dev.gamecore.client.ui.ThemeManager;

/** 游戏详情与模式选择；在线Tank走独立连接页，本地模式继续使用原路由。 */
public final class GameDetailScreen extends UiScreen {
    private String selectedMode;

    public GameDetailScreen(GameCoreClient app, GameDescriptor game) {
        super(app, "游戏详情");
        selectedMode = game.modes().getFirst().id();
        boolean snake = "snake".equals(game.id());
        boolean fight = "fight".equals(game.id());
        Table back = new Table();
        back.add(bind(theme.button("←  返回游戏库", false), "back", app::home)).height(40).left();
        back.add().expandX();
        back.add(theme.badge("本地与在线双人", theme.accent(game.id())));
        content.add(back).growX().padBottom(16).row();
        // 左：封面与特性；右：名称、简介、带序号的玩法列表和唯一的主操作。两栏顶端对齐，右栏更宽。
        Table layout = new Table();
        Table left = new Table();
        left.setBackground(theme.panel(ThemeManager.PANEL));
        left.pad(2, 2, 18, 2);
        ArenaArt art = new ArenaArt(theme, game.id(), selectedMode);
        art.setName("detail-art");
        left.add(art).growX().height(290).row();
        Table copy = new Table();
        copy.pad(0, 18, 0, 18);
        copy.add(theme.label(game.subtitle(), theme.assets.heading, theme.accent(game.id()))).left().padTop(18).row();
        Label features = theme.caption(snake ? "经典 / 障碍 / 本地双人\n保存本地成绩  ·  支持在线双人" : fight ? "两位可选角色  ·  人机与双人联机\n出拳 / 远程 / 大招 / 格挡" : "本地双人  ·  三级人机  ·  在线双人\n双地图  ·  走位与掩体策略");
        features.setWrap(true);
        copy.add(features).growX().left().padTop(10);
        left.add(copy).growX();
        layout.add(left).width(420).top().padRight(24);
        Table info = new Table();
        info.setBackground(theme.panel(ThemeManager.PANEL));
        info.pad(24, 28, 24, 28);
        info.add(theme.label(game.name(), theme.assets.hero, ThemeManager.TEXT)).left().row();
        Label description = theme.label(game.description(), theme.assets.body, ThemeManager.MUTED);
        description.setWrap(true);
        info.add(description).growX().left().padTop(10).row();
        Table modesHeading = new Table();
        modesHeading.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(theme.solid(ThemeManager.BLUE))).size(8, 8).padRight(10);
        modesHeading.add(theme.label("选择玩法  ·  " + game.modes().size(), theme.assets.small, ThemeManager.BLUE)).left();
        info.add(modesHeading).left().padTop(20).row();
        Table modes = new Table();
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        int index = 0;
        for (GameMode mode : game.modes()) {
            TextButton button = theme.toggle(mode.name());
            bind(button, "mode-" + mode.id(), () -> {
                if (!button.isChecked()) { return; }
                selectedMode = mode.id();
                art.setMode(selectedMode);
            });
            button.clearChildren();
            button.pad(7, 18, 7, 14);
            button.add(theme.label(String.format("%02d", ++index), theme.assets.small, ThemeManager.MUTED)).left().width(30);
            button.add(theme.text(mode.name())).left().width(104);
            Label modeDescription = theme.caption(mode.description());
            modeDescription.setWrap(true);
            button.add(modeDescription).growX().left();
            button.getChildren().forEach(child -> child.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled));
            group.add(button);
            modes.add(button).height(52).growX().spaceBottom(6).row();
        }
        info.add(modes).growX().padTop(10).row();
        info.add(bind(theme.button("开始游戏  →", true), "start-preview", () -> app.preview(game.id(), selectedMode)))
                .growX().height(48).padTop(14).row();
        Label note = theme.caption(snake ? "本地游戏在独立窗口开始，按键以局内提示为准。在线对战也可从在线平台创建房间。" : fight ? "本地对战可选择角色与地图；操作以局内提示为准。在线对战也可从在线平台进入。" : "直连模式需填写对应服务器地址。便捷联机请返回游戏库，进入在线平台。");
        note.setWrap(true);
        info.add(note).growX().padTop(12);
        layout.add(info).growX().top();
        content.add(layout).growX().top().row();
        content.add().expandY();
    }
}
