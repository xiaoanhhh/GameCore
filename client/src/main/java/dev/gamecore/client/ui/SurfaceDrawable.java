package dev.gamecore.client.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

/** 两层共享圆角构成细边框面板；只借用 Drawable，且完整恢复 Batch 的颜色状态。 */
final class SurfaceDrawable extends BaseDrawable {
    private final Drawable border;
    private final Drawable fill;

    SurfaceDrawable(Drawable border, Drawable fill) {
        this.border = border;
        this.fill = fill;
        setLeftWidth(14); setRightWidth(14); setTopHeight(10); setBottomHeight(10);
    }

    @Override public void draw(Batch batch, float x, float y, float width, float height) {
        border.draw(batch, x, y, width, height);
        if (width > 2 && height > 2) { fill.draw(batch, x + 1, y + 1, width - 2, height - 2); }
    }
}
