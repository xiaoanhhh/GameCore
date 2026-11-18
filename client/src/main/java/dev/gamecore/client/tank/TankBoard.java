package dev.gamecore.client.tank;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Align;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.tank.TankGame;
import dev.gamecore.client.effects.EffectWorld;
import java.util.List;
import dev.gamecore.tank.TankSnapshot;

/**
 * 只读快照渲染：libGDX 图层绘制地板/墙面/装饰，原创几何绘制坦克（履带、车体、炮塔、炮管）、
 * 子弹光晕、修理区和头顶生命条，不反写世界。可选的状态浮层（准备/暂停/结算）只由页面传入文字，
 * 规则状态仍以快照为准；在线页面不设置浮层。
 */
public final class TankBoard extends Widget {
    private static final Color TRACK = Color.valueOf("07131d");
    private static final Color TREAD = Color.valueOf("1d2a36");
    private static final Color ZONE = Color.valueOf("214a46");
    private static final Color FRAME = Color.valueOf("0a1520");
    private static final Color WRECK = Color.valueOf("3a4552");
    private final ThemeManager theme;
    private final GlyphLayout layout = new GlyphLayout();
    private final Color scratch = new Color();
    private TankMapAsset asset;
    private TankSnapshot view;
    private float cell;
    private float clock;
    private String overlayTitle, overlayDetail;
    private Color overlayColor = ThemeManager.TEXT;
    private List<EffectWorld.Particle> effects = List.of();
    public void updateEffects(List<EffectWorld.Particle> value) { effects = List.copyOf(value); }
    public TankBoard(ThemeManager theme) { this.theme = theme; }
    public void update(TankMapAsset asset, TankSnapshot view) { this.asset = asset; this.view = view; }
    /** 当前展示的快照（只读），供侧栏记分板等同屏 HUD 读取，避免各自缓存不同步的副本。 */
    public TankSnapshot view() { return view; }
    /** 设置居中状态浮层；title 为 null 时隐藏。只影响外观，不参与输入。 */
    public void overlay(String title, String detail, Color color) { overlayTitle = title; overlayDetail = detail; overlayColor = color; }
    public String overlayTitle() { return overlayTitle; }
    public int columns() { return view == null ? 24 : view.map().width(); }
    public int rows() { return view == null ? 18 : view.map().height(); }
    @Override public float getMinWidth() { return 0; }
    @Override public float getMinHeight() { return 0; }
    @Override public void act(float delta) { super.act(delta); clock += Math.min(Math.max(0, delta), .1f); }
    @Override public void draw(Batch batch, float parentAlpha) {
        if (view == null || asset == null) { return; }
        float oldColor = batch.getPackedColor(); cell = getWidth() / columns();
        float alpha = parentAlpha * getColor().a;
        // 外框：深色描边把地图从背景网格中“托”出来。
        rect(batch, FRAME, alpha, getX() - 5, getY() - 5, getWidth() + 10, getHeight() + 10);
        for (String name : new String[]{"floor", "decorations", "walls"}) {
            var layer = (TiledMapTileLayer) asset.visual().getLayers().get(name);
            batch.setColor(1, 1, 1, alpha);
            for (int y = 0; y < rows(); y++) {
                for (int x = 0; x < columns(); x++) {
                    var tile = layer.getCell(x, y);
                    if (tile != null) { batch.draw(tile.getTile().getTextureRegion(), getX() + x * cell, getY() + y * cell, cell, cell); }
                }
            }
        }
        float pulse = .5f + .5f * (float) Math.sin(clock * 3);
        for (var zone : view.map().repairZones()) {
            float zx = getX() + (float) zone.x() * cell, zy = getY() + (float) zone.y() * cell, zw = (float) zone.width() * cell, zh = (float) zone.height() * cell;
            rect(batch, ZONE, alpha * (.7f + .2f * pulse), zx, zy, zw, zh);
            glow(batch, ThemeManager.MINT, alpha * (.10f + .10f * pulse), zx + zw / 2, zy + zh / 2, Math.max(zw, zh) * .9f);
            corners(batch, ThemeManager.MINT, alpha * (.55f + .45f * pulse), zx + 2, zy + 2, zw - 4, zh - 4, Math.min(zw, zh) * .22f);
            float x = zx + zw / 2, y = zy + zh / 2;
            rect(batch, ThemeManager.MINT, alpha, x - cell * .3f, y - cell * .08f, cell * .6f, cell * .16f);
            rect(batch, ThemeManager.MINT, alpha, x - cell * .08f, y - cell * .3f, cell * .16f, cell * .6f);
        }
        for (var tank : view.tanks()) { tank(batch, tank, alpha); }
        for (var bullet : view.bullets()) {
            Color color = bullet.owner() == 0 ? ThemeManager.MINT : ThemeManager.GOLD;
            float x = getX() + (float) bullet.position().x() * cell, y = getY() + (float) bullet.position().y() * cell;
            glow(batch, color, alpha * .55f, x, y, cell * .42f);
            circle(batch, color, alpha, x, y, cell * .22f);
            circle(batch, ThemeManager.TEXT, alpha, x, y, cell * .11f);
        }
        for (var particle : effects) {
            Color color = particle.kind() == dev.gamecore.api.event.GameEvent.Effect.Kind.REPAIRED ? ThemeManager.MINT : ThemeManager.GOLD;
            batch.setColor(color.r, color.g, color.b, (float) particle.alpha() * alpha);
            float size = (float) particle.size() * cell;
            batch.draw(theme.assets.circle, getX() + (float) particle.x() * cell - size / 2,
                    getY() + (float) particle.y() * cell - size / 2, size, size);
        }
        if (overlayTitle != null) { overlay(batch, alpha); }
        theme.assets.small.setColor(Color.WHITE); batch.setPackedColor(oldColor);
    }

    /** 坦克由阴影、两条履带、车体、炮塔与炮管组成；全部按车身朝向旋转，阵亡时显示为灰色残骸。 */
    private void tank(Batch batch, TankSnapshot.Tank tank, float alpha) {
        float x = getX() + (float) tank.position().x() * cell, y = getY() + (float) tank.position().y() * cell;
        float heading = (float) tank.heading();
        Color color = !tank.alive() ? WRECK : tank.id() == 0 ? ThemeManager.MINT : ThemeManager.GOLD;
        part(batch, Color.BLACK, alpha * .35f, x + cell * .06f, y - cell * .07f, heading, 0, 0, .86f, .78f);
        part(batch, TRACK, alpha, x, y, heading, 0, .27f, .86f, .24f);
        part(batch, TRACK, alpha, x, y, heading, 0, -.27f, .86f, .24f);
        // 履带齿：沿长度方向的浅色短条，随朝向旋转，读出“车头/车尾”与运动方向。
        for (int i = 0; i < 5; i++) {
            float along = -.34f + i * .17f;
            part(batch, TREAD, alpha, x, y, heading, along, .27f, .06f, .2f);
            part(batch, TREAD, alpha, x, y, heading, along, -.27f, .06f, .2f);
        }
        part(batch, color, alpha, x, y, heading, -.02f, 0, .66f, .44f);
        part(batch, Color.WHITE, alpha * .18f, x, y, heading, -.02f, .13f, .62f, .08f);
        if (tank.alive()) {
            part(batch, color, alpha, x, y, heading, .36f, 0, .5f, .12f);
            part(batch, TRACK, alpha, x, y, heading, .6f, 0, .08f, .17f);
            circle(batch, TRACK, alpha, x, y, cell * .38f);
            circle(batch, color, alpha, x, y, cell * .32f);
            circle(batch, ThemeManager.TEXT, alpha * .9f, x, y, cell * .14f);
        } else {
            float s = cell * .22f;
            line(batch, TRACK, alpha, x - s, y - s, x + s, y + s, cell * .08f);
            line(batch, TRACK, alpha, x - s, y + s, x + s, y - s, cell * .08f);
        }
        Color tag = tank.alive() ? color : ThemeManager.MUTED;
        // 头顶标签：玩家编号 + 五格生命条，深色底保证在任何地砖上都可读。
        float pip = cell * .13f, gap = cell * .04f, barWidth = TankGame.MAX_HEALTH * pip + (TankGame.MAX_HEALTH - 1) * gap;
        float top = y + cell * .58f;
        rect(batch, TRACK, alpha * .82f, x - barWidth / 2 - cell * .06f, top - cell * .05f, barWidth + cell * .12f, cell * .18f);
        for (int hp = 0; hp < TankGame.MAX_HEALTH; hp++) {
            rect(batch, hp < tank.health() ? tag : TREAD, alpha, x - barWidth / 2 + hp * (pip + gap), top, pip, cell * .08f);
        }
        BitmapFont font = theme.assets.small;
        font.setColor(tag.r, tag.g, tag.b, alpha);
        layout.setText(font, tank.id() == 0 ? "P1" : "P2");
        font.draw(batch, layout, x - layout.width / 2, top + cell * .16f + layout.height);
    }

    /** 浮层：压暗地图后在中部画一条横带，标题用玩家/结果色，说明用正文色。 */
    private void overlay(Batch batch, float alpha) {
        rect(batch, Color.BLACK, alpha * .42f, getX(), getY(), getWidth(), getHeight());
        float band = Math.max(cell * 2.8f, 96), y = getY() + (getHeight() - band) / 2;
        rect(batch, FRAME, alpha * .92f, getX(), y, getWidth(), band);
        rect(batch, overlayColor, alpha, getX(), y + band - 2, getWidth(), 2);
        rect(batch, overlayColor, alpha * .5f, getX(), y, getWidth(), 1);
        BitmapFont title = theme.assets.title, body = theme.assets.body;
        title.setColor(overlayColor.r, overlayColor.g, overlayColor.b, alpha);
        layout.setText(title, overlayTitle, title.getColor(), getWidth(), Align.center, false);
        float titleHeight = layout.height;
        float detailHeight = 0;
        if (overlayDetail != null) { layout.setText(body, overlayDetail); detailHeight = layout.height + 12; }
        float baseline = y + band / 2 + (titleHeight + detailHeight) / 2;
        layout.setText(title, overlayTitle, title.getColor(), getWidth(), Align.center, false);
        title.draw(batch, layout, getX(), baseline);
        if (overlayDetail != null) {
            body.setColor(ThemeManager.MUTED.r, ThemeManager.MUTED.g, ThemeManager.MUTED.b, alpha);
            layout.setText(body, overlayDetail, body.getColor(), getWidth(), Align.center, false);
            body.draw(batch, layout, getX(), baseline - titleHeight - 12);
            body.setColor(Color.WHITE);
        }
        title.setColor(Color.WHITE);
    }

    /** 以车身中心为原点、朝向为 x 轴的局部矩形；along/side/length/width 以格为单位。 */
    private void part(Batch batch, Color color, float alpha, float x, float y, float heading, float along, float side, float length, float width) {
        double radians = Math.toRadians(heading);
        float cos = (float) Math.cos(radians), sin = (float) Math.sin(radians);
        float cx = x + (along * cos - side * sin) * cell, cy = y + (along * sin + side * cos) * cell;
        float w = length * cell, h = width * cell;
        batch.setColor(color.r, color.g, color.b, color.a * alpha);
        batch.draw(theme.assets.pixel, cx - w / 2, cy - h / 2, w / 2, h / 2, w, h, 1, 1, heading, 0, 0, 1, 1, false, false);
    }
    private void corners(Batch batch, Color color, float alpha, float x, float y, float w, float h, float arm) {
        float t = Math.max(2, cell * .06f);
        rect(batch, color, alpha, x, y, arm, t); rect(batch, color, alpha, x, y, t, arm);
        rect(batch, color, alpha, x + w - arm, y, arm, t); rect(batch, color, alpha, x + w - t, y, t, arm);
        rect(batch, color, alpha, x, y + h - t, arm, t); rect(batch, color, alpha, x, y + h - arm, t, arm);
        rect(batch, color, alpha, x + w - arm, y + h - t, arm, t); rect(batch, color, alpha, x + w - t, y + h - arm, t, arm);
    }
    private void line(Batch batch, Color color, float alpha, float x, float y, float xx, float yy, float thickness) {
        float dx = xx - x, dy = yy - y; batch.setColor(color.r, color.g, color.b, alpha);
        batch.draw(theme.assets.pixel, x, y - thickness / 2, 0, thickness / 2, (float) Math.hypot(dx, dy), thickness, 1, 1,
                (float) Math.toDegrees(Math.atan2(dy, dx)), 0, 0, 1, 1, false, false);
    }
    private void circle(Batch batch, Color color, float alpha, float x, float y, float size) {
        batch.setColor(color.r, color.g, color.b, alpha); batch.draw(theme.assets.circle, x - size / 2, y - size / 2, size, size);
    }
    private void glow(Batch batch, Color color, float alpha, float x, float y, float radius) {
        batch.setColor(scratch.set(color.r, color.g, color.b, alpha)); batch.draw(theme.assets.glow, x - radius, y - radius, radius * 2, radius * 2);
    }
    private void rect(Batch batch, Color color, float alpha, float x, float y, float w, float h) {
        batch.setColor(color.r, color.g, color.b, color.a * alpha); batch.draw(theme.assets.pixel, x, y, w, h);
    }
}
