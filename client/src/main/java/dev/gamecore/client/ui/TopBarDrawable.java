package dev.gamecore.client.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;

/** 大厅顶栏底：深墨半透明 + 底部 1px 发丝线；借用白像素，不拥有纹理，绘制后恢复 Batch 颜色。 */
public final class TopBarDrawable extends BaseDrawable {
    private final UiInk ink;
    public TopBarDrawable(ThemeManager theme) { ink = new UiInk(theme); }
    @Override public void draw(Batch b, float x, float y, float w, float h) {
        float packed = b.getPackedColor(), a = b.getColor().a;
        ink.rect(b, LobbyPalette.TOPBAR, a, x, y, w, h);
        ink.rect(b, LobbyPalette.LINE, a, x, y, w, 1);
        b.setPackedColor(packed);
    }
}
