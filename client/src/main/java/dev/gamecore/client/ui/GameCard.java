package dev.gamecore.client.ui;

import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import dev.gamecore.api.GameDescriptor;

/**
 * 数据驱动的可复用游戏卡片；整个封面和内容区域都可选择。
 * 布局：通栏封面（左上玩法数角标）→ 识别色副标题 → 大号名称与进入箭头 → 一行简介。
 * 悬停/焦点时描边换成该游戏识别色；玩法数量来自注册目录而非写死文案。
 */
public final class GameCard extends MotionTextButton {
    public GameCard(ThemeManager theme, GameDescriptor game) {
        super("", theme.cardStyle(theme.accent(game.id())));
        clearChildren();
        pad(0);
        Stack cover = new Stack();
        cover.setName("game-cover-" + game.id());
        cover.add(new ArenaArt(theme, game.id(), "classic"));
        Table badge = new Table();
        badge.top().left().pad(12);
        badge.add(theme.badge(game.modes().size() + " 种玩法", theme.accent(game.id())));
        cover.add(badge);
        add(cover).growX().height(146).pad(2, 2, 0, 2).row();
        Table body = new Table();
        body.setName("game-copy-" + game.id());
        body.top().left();
        body.pad(14, 18, 14, 18);
        body.add(theme.label(game.subtitle(), theme.assets.small, theme.accent(game.id()))).left().colspan(2).row();
        Label name = theme.label(game.name(), theme.assets.title, ThemeManager.TEXT);
        name.setEllipsis(true);
        body.add(name).left().growX().minWidth(0).padTop(2);
        body.add(theme.label("→", theme.assets.heading, theme.accent(game.id()))).right().bottom().padBottom(4).row();
        Label summary = theme.caption(game.summary());
        summary.setName("game-summary-" + game.id());
        summary.setWrap(true);
        body.add(summary).growX().left().padTop(8).colspan(2);
        add(body).grow();
        // 所有内部装饰均让出命中，使整张卡片具备一致的点击和键盘语义。
        getChildren().forEach(actor -> actor.setTouchable(Touchable.disabled));
    }
}
