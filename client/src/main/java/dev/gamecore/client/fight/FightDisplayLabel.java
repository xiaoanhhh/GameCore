package dev.gamecore.client.fight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import dev.gamecore.client.ui.ThemeManager;

/** 保持 Label 的文本/布局/可访问接口，绘制时采用独立展示字体，缩放不会悬挂旧字体引用。 */
public final class FightDisplayLabel extends Label {
    private final FightTypography type;
    private final float height;
    public FightDisplayLabel(ThemeManager theme, String text, float height, Color accent) {
        super(text, new LabelStyle(theme.assets.heading, Color.WHITE));
        type = new FightTypography(theme); this.height = height; setColor(accent);
    }
    // 展示字自行按可用宽度适配，不能让正文 Label 的固有宽度把整条 HUD 挤出窗口。
    @Override public float getMinWidth(){return 0;}
    @Override public void draw(Batch batch, float parentAlpha) {
        type.draw(batch, getText(), getX() + getWidth() / 2, getY() + (getHeight() + height) / 2,
                height, getWidth() - 4, getColor(), parentAlpha * getColor().a);
    }
}
