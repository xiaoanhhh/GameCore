package dev.gamecore.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

/** 仅混合背景的短过渡；不缩放 Actor、不推迟事件，保留 TextButton 的键盘和样式语义。 */
public class MotionTextButton extends TextButton {
    private Drawable current;
    private Drawable previous;
    private float elapsed = .12f;

    public MotionTextButton(String text, TextButtonStyle style) { super(text, style); }

    @Override public void act(float delta) {
        super.act(delta);
        elapsed = Math.min(.12f, elapsed + Math.max(0, delta));
    }

    @Override protected void drawBackground(Batch batch, float parentAlpha, float x, float y) {
        Drawable next = getBackground();
        if (next != current) {
            previous = current; current = next; elapsed = previous == null ? .12f : 0;
        }
        float packed = batch.getPackedColor();
        Color color = getColor();
        float progress = elapsed / .12f;
        if (previous != null && progress < 1) {
            batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
            previous.draw(batch, x, y, getWidth(), getHeight());
        }
        if (current != null) {
            batch.setColor(color.r, color.g, color.b, color.a * parentAlpha * progress);
            current.draw(batch, x, y, getWidth(), getHeight());
        }
        batch.setPackedColor(packed);
    }
}
