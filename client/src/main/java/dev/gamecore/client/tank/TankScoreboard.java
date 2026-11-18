package dev.gamecore.client.tank;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Align;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.tank.TankGame;

/**
 * 侧栏对阵记分板：左 P1、右 P2，各自名称与五格生命条，中间 VS。
 * 直接读取 {@link TankBoard#view()} 的同一只读快照，不缓存、不推进规则；文字版生命值仍由
 * tank-health 标签提供，本控件只做图形化读数，不接收输入。
 */
public final class TankScoreboard extends Widget {
    private static final Color EMPTY = Color.valueOf("1d2a36");
    private final ThemeManager theme;
    private final TankBoard board;
    private final String[] names;
    private final GlyphLayout layout = new GlyphLayout();

    public TankScoreboard(ThemeManager theme, TankBoard board, String playerOne, String playerTwo) {
        this.theme = theme; this.board = board; names = new String[]{playerOne, playerTwo};
        setTouchable(Touchable.disabled);
    }
    @Override public float getPrefHeight() { return 44; }
    @Override public float getPrefWidth() { return 240; }
    @Override public float getMinWidth() { return 0; }

    @Override public void draw(Batch batch, float parentAlpha) {
        float packed = batch.getPackedColor(), alpha = parentAlpha * getColor().a;
        var view = board.view();
        float half = (getWidth() - 36) / 2;
        for (int side = 0; side < 2; side++) {
            Color color = side == 0 ? ThemeManager.MINT : ThemeManager.GOLD;
            int health = view == null || view.tanks().size() <= side ? -1 : view.tanks().get(side).health();
            float x = side == 0 ? getX() : getX() + getWidth() - half;
            BitmapFont font = theme.assets.body;
            font.setColor(color.r, color.g, color.b, alpha);
            layout.setText(font, names[side], font.getColor(), half, side == 0 ? Align.left : Align.right, false);
            font.draw(batch, layout, x, getY() + getHeight() - 4);
            // 五格生命条：P1 从左向右填充，P2 从右向左填充，两侧向中间对称收缩。
            float gap = 4, pip = (half - gap * (TankGame.MAX_HEALTH - 1)) / TankGame.MAX_HEALTH, barY = getY() + 6;
            for (int i = 0; i < TankGame.MAX_HEALTH; i++) {
                boolean filled = health > (side == 0 ? i : TankGame.MAX_HEALTH - 1 - i);
                Color c = filled ? color : EMPTY;
                batch.setColor(c.r, c.g, c.b, alpha);
                batch.draw(theme.assets.pixel, x + i * (pip + gap), barY, pip, 12);
            }
        }
        BitmapFont small = theme.assets.small;
        small.setColor(ThemeManager.MUTED.r, ThemeManager.MUTED.g, ThemeManager.MUTED.b, alpha);
        layout.setText(small, "VS", small.getColor(), 36, Align.center, false);
        small.draw(batch, layout, getX() + half, getY() + 6 + 12 / 2f + layout.height / 2);
        small.setColor(Color.WHITE);
        theme.assets.body.setColor(Color.WHITE);
        batch.setPackedColor(packed);
    }
}
