package dev.gamecore.client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Matrix4;

/**
 * 借用当前 Stage 的 Batch 和所属窗口的纹理；先覆盖整个客户区，再由页面恢复 UI 视口。
 * 大厅主题为纯程序背景（墨底 + 缓慢漂移的游戏识别色柔光 + 点阵），不加载插画；
 * 竞技主题仍使用原背景图并压暗，外观不变。
 */
public final class BackdropRenderer {
    private final ThemeManager theme;
    private final Matrix4 projection = new Matrix4();
    private final UiInk ink;
    private final long origin = System.nanoTime();
    private static final Color TACTICAL = Color.valueOf("07111a");

    public BackdropRenderer(ThemeManager theme) { this.theme = theme; ink = new UiInk(theme); }
    public boolean lobby() { return theme.isLobby(); }

    public void draw(Batch batch) { draw(batch, false); }

    /**
     * tactical=true 为 Tank 战场背景：深钢蓝底、两侧玩家色（P1 青绿/P2 琥珀）柔光与等距网格线，
     * 不使用插画；只影响背景，不改竞技主题色常量。
     */
    public void draw(Batch batch, boolean tactical) {
        int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
        if (width <= 0 || height <= 0) { return; }
        Gdx.gl.glViewport(0, 0, width, height);
        batch.setProjectionMatrix(projection.setToOrtho2D(0, 0, width, height));
        batch.begin();
        if (tactical) {
            tactical(batch, width, height);
        } else if (theme.isLobby()) {
            lobby(batch, width, height);
        } else if (theme.assets.background != null) {
            var rect = BackgroundLayout.cover(theme.assets.background.getWidth(), theme.assets.background.getHeight(), width, height);
            batch.setColor(1, 1, 1, 1);
            batch.draw(theme.assets.background, rect.x(), rect.y(), rect.width(), rect.height());
            batch.setColor(.025f, .045f, .09f, .52f);
            batch.draw(theme.assets.pixel, 0, 0, width, height);
            // 原创渐变仅用于压暗底部和左侧文字区域，不修改或重复上传原图。
            batch.setColor(.025f, .045f, .09f, .65f);
            batch.draw(theme.assets.shade, 0, 0, width, height);
        }
        batch.setColor(1, 1, 1, 1);
        batch.end();
    }

    /** 光晕周期约 40–60 秒，幅度只有画面的几个百分点：营造“在运转”的氛围，又不抢正文注意力。 */
    private void lobby(Batch batch, int width, int height) {
        float t = (System.nanoTime() - origin) / 1e9f;
        float scale = Math.min(width / (float) UiFontScale.DESIGN_WIDTH, height / (float) UiFontScale.DESIGN_HEIGHT);
        ink.rect(batch, LobbyPalette.INK, 1, 0, 0, width, height);
        glow(batch, LobbyPalette.SIGNAL, .085f, width * (.10f + .03f * (float) Math.sin(t * .11)), height * 1.02f, height * 1.05f);
        glow(batch, LobbyPalette.CORAL, .075f, width * .95f, height * (.70f + .05f * (float) Math.sin(t * .13 + 1)), height * .95f);
        glow(batch, LobbyPalette.TEAL, .06f, width * (.62f + .04f * (float) Math.cos(t * .09)), -height * .15f, height * 1.1f);
        glow(batch, LobbyPalette.AMBER, .035f, width * .28f, height * .08f, height * .7f);
        // 点阵按逻辑像素等距，任何分辨率下点数相近（约 1000 个），不随 4K 线性增长。
        float spacing = 32 * scale, size = Math.max(1, 1.4f * scale);
        for (float y = spacing / 2; y < height; y += spacing) {
            for (float x = spacing / 2; x < width; x += spacing) { ink.rect(batch, LobbyPalette.TEXT, .045f, x, y, size, size); }
        }
        // 底部压暗，让页脚和卡片下沿落在稳定的暗部。
        batch.setColor(LobbyPalette.INK.r, LobbyPalette.INK.g, LobbyPalette.INK.b, .55f);
        batch.draw(theme.assets.shade, 0, 0, width, height * .6f);
    }

    private void tactical(Batch batch, int width, int height) {
        float t = (System.nanoTime() - origin) / 1e9f;
        float scale = Math.min(width / (float) UiFontScale.DESIGN_WIDTH, height / (float) UiFontScale.DESIGN_HEIGHT);
        ink.rect(batch, TACTICAL, 1, 0, 0, width, height);
        glow(batch, ThemeManager.MINT, .07f, width * .05f, height * (.15f + .03f * (float) Math.sin(t * .1)), height * .95f);
        glow(batch, ThemeManager.GOLD, .06f, width * .78f, height * (.95f + .03f * (float) Math.cos(t * .12)), height * .9f);
        // 网格线按逻辑像素等距，与地图格子同属“战术地图”语言；条数与分辨率无关。
        float spacing = 48 * scale, thickness = Math.max(1, scale);
        for (float x = spacing; x < width; x += spacing) { ink.rect(batch, ThemeManager.TEXT, .028f, x, 0, thickness, height); }
        for (float y = spacing; y < height; y += spacing) { ink.rect(batch, ThemeManager.TEXT, .028f, 0, y, width, thickness); }
        batch.setColor(TACTICAL.r, TACTICAL.g, TACTICAL.b, .6f);
        batch.draw(theme.assets.shade, 0, 0, width, height * .5f);
    }

    private void glow(Batch batch, Color color, float alpha, float cx, float cy, float radius) {
        batch.setColor(color.r, color.g, color.b, alpha);
        batch.draw(theme.assets.glow, cx - radius, cy - radius, radius * 2, radius * 2);
    }
}
