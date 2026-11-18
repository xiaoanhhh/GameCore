package dev.gamecore.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;

/** 大厅原创几何图标（品牌标、游戏库、在线、设置、退出），沿用共享白像素；装饰让出命中，不改变按钮键盘与焦点语义。 */
public final class LobbyGlyph extends Widget {
    public enum Kind { LOGO, LIBRARY, ONLINE, SETTINGS, EXIT }
    private final UiInk ink;
    private final Kind kind;
    public LobbyGlyph(ThemeManager theme, Kind kind) {
        ink = new UiInk(theme); this.kind = kind;
        setColor(LobbyPalette.SIGNAL); setTouchable(Touchable.disabled);
    }
    @Override public void draw(Batch b, float alpha) {
        float p = b.getPackedColor(), s = Math.min(getWidth(), getHeight()), x = getX() + (getWidth() - s) / 2, y = getY() + (getHeight() - s) / 2;
        Color c = getColor();
        switch (kind) {
            case LOGO -> {
                // 信号绿切角方块 + 墨色“开始”三角：品牌标只用几何，不依赖外部美术。
                float cut = s * .24f;
                ink.quad(b, c, alpha, x, y + cut, x + cut, y, x + s, y, x + s, y + s - cut);
                ink.quad(b, c, alpha, x, y + cut, x + s, y + s - cut, x + s - cut, y + s, x, y + s);
                ink.quad(b, LobbyPalette.INK, alpha, x + s * .36f, y + s * .27f, x + s * .74f, y + s * .5f, x + s * .74f, y + s * .5f, x + s * .36f, y + s * .73f);
            }
            case LIBRARY -> {
                // 2×2 实心方格：游戏库。
                float e = s * .38f;
                for (int i = 0; i < 4; i++) { ink.rect(b, c, alpha * (i == 1 ? 1 : .72f), x + s * (i % 2 == 0 ? .08f : .54f), y + s * (i < 2 ? .54f : .08f), e, e); }
            }
            case ONLINE -> {
                // 两个相交的圆：与朋友同场。
                ink.ring(b, c, alpha, x + s * .36f, y + s * .5f, s * .26f, 0, 360, 1.5f);
                ink.ring(b, c, alpha, x + s * .64f, y + s * .5f, s * .26f, 0, 360, 1.5f);
            }
            case EXIT -> {
                ink.line(b, c, alpha, x + s * .15f, y + s * .15f, x + s * .15f, y + s * .85f, 1.5f);
                ink.line(b, c, alpha, x + s * .15f, y + s * .85f, x + s * .55f, y + s * .85f, 1.5f);
                ink.line(b, c, alpha, x + s * .15f, y + s * .15f, x + s * .55f, y + s * .15f, 1.5f);
                ink.line(b, c, alpha, x + s * .4f, y + s * .5f, x + s * .92f, y + s * .5f, 1.5f);
                ink.line(b, c, alpha, x + s * .92f, y + s * .5f, x + s * .76f, y + s * .66f, 1.5f);
                ink.line(b, c, alpha, x + s * .92f, y + s * .5f, x + s * .76f, y + s * .34f, 1.5f);
            }
            case SETTINGS -> {
                for (int i = 0; i < 3; i++) {
                    float yy = y + s * (.2f + i * .3f), xx = x + s * (i == 1 ? .66f : .34f);
                    ink.line(b, c, alpha, x + s * .08f, yy, x + s * .92f, yy, 1.4f);
                    ink.rect(b, c, alpha, xx - s * .09f, yy - s * .09f, s * .18f, s * .18f);
                }
            }
        }
        b.setPackedColor(p);
    }
    /** 图标颜色随导航状态传入：常态用中性灰，选中用信号绿，与文字同一层级。 */
    public static void decorate(ThemeManager theme, TextButton button, Kind kind, Color color) {
        var glyph = new LobbyGlyph(theme, kind); glyph.setColor(color);
        button.clearChildren(); button.add(glyph).size(15).padRight(10); button.add(button.getLabel()).left();
    }
}
