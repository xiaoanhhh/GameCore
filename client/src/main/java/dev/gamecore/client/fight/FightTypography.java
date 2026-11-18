package dev.gamecore.client.fight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import dev.gamecore.client.ui.ThemeManager;

/** 格斗展示字：同源重字重、挤出阴影和元素色描边；借用字体，每次恢复共享字体/Batch 状态。 */
public final class FightTypography {
    private static final Color INK = Color.valueOf("030918"), FACE = Color.valueOf("f4fcff");
    private final ThemeManager theme;
    private final GlyphLayout layout = new GlyphLayout();
    public FightTypography(ThemeManager theme) { this.theme = theme; }
    public void draw(Batch batch, CharSequence text, float centerX, float baseline, float height, float maxWidth, Color accent, float alpha) {
        if (maxWidth <= 0 || height <= 0 || text.length() == 0) { return; }
        var font = theme.assets.fightDisplay();
        for (int i = 0; i < text.length(); i++) { if (!font.getData().hasGlyph(text.charAt(i)) && !Character.isWhitespace(text.charAt(i))) { font = theme.assets.heading; break; } }
        float sx = font.getData().scaleX, sy = font.getData().scaleY, packed = batch.getPackedColor();
        float scale = height / Math.max(1, font.getCapHeight());
        try {
            font.getData().setScale(sx * scale, sy * scale); layout.setText(font, text);
            if (layout.width > maxWidth) { scale *= maxWidth / layout.width; font.getData().setScale(sx * scale, sy * scale); layout.setText(font, text); }
            float x = centerX - layout.width / 2, edge = Math.max(.6f, height / 40);
            font.setColor(INK.r, INK.g, INK.b, alpha); font.draw(batch, text, x + edge * 3, baseline - edge * 5);
            for (int i = 3; i >= 1; i--) { font.setColor(accent.r * .35f, accent.g * .35f, accent.b * .35f, alpha); font.draw(batch, text, x + i * edge, baseline - i * edge); }
            font.setColor(accent.r, accent.g, accent.b, alpha);
            font.draw(batch, text, x - edge, baseline); font.draw(batch, text, x + edge, baseline);
            font.draw(batch, text, x, baseline + edge); font.draw(batch, text, x, baseline - edge);
            font.setColor(FACE.r, FACE.g, FACE.b, alpha); font.draw(batch, text, x, baseline);
        } finally { font.getData().setScale(sx, sy); font.setColor(Color.WHITE); batch.setPackedColor(packed); }
    }
}
