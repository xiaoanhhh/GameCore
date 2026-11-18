package dev.gamecore.client.traffic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import dev.gamecore.client.assets.ClientAssetManager;
import dev.gamecore.client.ui.UiFontScale;
import dev.gamecore.platform.api.PlatformMessages.Profile;
import java.util.function.Supplier;

/**
 * 每个窗口一份的悬浮面板：跟随 GameCoreClient 而不是页面，切换页面时不重建。
 * 不使用 Stage，页面的键盘焦点和触摸焦点完全不受影响，对局中可以直接拖动。
 * 字体和像素纹理每帧从资源管理器读取，字体密度重建后自动使用新对象。
 * 未开启时不创建 SpriteBatch，也不处理任何输入。
 */
public final class TrafficOverlay implements Disposable {
    public static final int TOGGLE_KEY = Input.Keys.F9;
    private static final Color PANEL = new Color(.05f, .07f, .10f, .88f), BAR = new Color(.11f, .14f, .19f, .96f),
            LINE = new Color(1, 1, 1, .16f), BUTTON = new Color(1, 1, 1, .10f), ACTIVE = new Color(.84f, .94f, .37f, .28f),
            TEXT = new Color(.92f, .94f, .97f, 1), MUTED = new Color(.66f, .71f, .79f, 1);

    private final TrafficMonitor monitor;
    private final Supplier<Profile> profile;
    private final ClientAssetManager assets;
    private final ExtendViewport viewport = new ExtendViewport(UiFontScale.DESIGN_WIDTH, UiFontScale.DESIGN_HEIGHT);
    private final TrafficPanelModel panel = new TrafficPanelModel();
    private final Vector2 point = new Vector2();
    private final InputProcessor input = new PanelInput();
    private SpriteBatch batch;
    private long seen = -1;
    private int capturedPointer = -1, pointerX, pointerY;

    public TrafficOverlay(TrafficMonitor monitor, Supplier<Profile> profile, ClientAssetManager assets) {
        this.monitor = monitor; this.profile = profile; this.assets = assets;
    }

    public InputProcessor input() { return input; }
    public TrafficPanelModel panel() { return panel; }
    public boolean active() { return monitor.active(); }
    /** 绘制资源是否已分配；其他账户下应始终为 false。 */
    public boolean allocated() { return batch != null; }
    /** 逻辑坐标转窗口坐标，供图形验收发送真实输入。 */
    public Vector2 screen(float x, float y) {
        Vector2 projected = viewport.project(new Vector2(x, y));
        return projected.set(projected.x, Gdx.graphics.getHeight() - projected.y);
    }

    public void resize(int width, int height) {
        viewport.update(width, height, true);
        panel.resize(viewport.getWorldWidth(), viewport.getWorldHeight());
    }

    /** 同步账户并绘制；返回是否改动了视口。 */
    public boolean render() {
        boolean active = monitor.sync(profile.get());
        TrafficLog log = monitor.log();
        if (!active) {
            capturedPointer = -1; panel.endDrag();
            if (seen != -1) { seen = -1; panel.cleared(); }
            return false;
        }
        log.flush();
        long version = log.version();
        if (version != seen && !panel.frozen()) { seen = version; panel.entries(log.entries()); }
        if (!panel.visible()) { return false; }
        if (batch == null) { batch = new SpriteBatch(); }
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        draw(assets.small);
        batch.setColor(Color.WHITE);
        batch.end();
        return true;
    }

    private void draw(BitmapFont font) {
        rect(PANEL, panel.x(), panel.y(), panel.width(), panel.height());
        outline(LINE, panel.x(), panel.y(), panel.width(), panel.height());
        var title = panel.title();
        rect(panel.dragging() ? ACTIVE : BAR, title.x(), title.y(), title.width(), title.height());
        text(font, TEXT, "服务器交互 · " + panel.matching() + " 条 · F9 隐藏", title.x() + TrafficPanelModel.PAD, title.y() + 20, title.width() - 150);
        button(font, TrafficPanelModel.Control.PAUSE, panel.paused() ? "继续" : "暂停", panel.paused());
        button(font, TrafficPanelModel.Control.CLEAR, "清空", false);
        button(font, TrafficPanelModel.Control.COLLAPSE, panel.collapsed() ? "展开" : "收起", false);
        if (panel.collapsed()) { return; }
        for (TrafficKind kind : TrafficKind.values()) {
            var chip = panel.chip(kind);
            boolean off = panel.filtered(kind);
            rect(BUTTON, chip.x(), chip.y(), chip.width(), chip.height());
            rect(off ? MUTED : kind.color, chip.x(), chip.y(), 3, chip.height());
            text(font, off ? MUTED : kind.color, kind.label, chip.x() + 8, chip.y() + 15, chip.width() - 9);
        }
        var rows = panel.rows();
        for (int i = 0; i < rows.size(); i++) {
            var entry = rows.get(i);
            var row = panel.row(i);
            float base = row.y() + 15;
            rect(entry.kind().color, row.x(), row.y() + 3, 3, row.height() - 5);
            text(font, MUTED, entry.time(), row.x() + 8, base, 88);
            text(font, MUTED, entry.direction().label, row.x() + 98, base, 30);
            text(font, entry.kind().color, entry.kind().label, row.x() + 128, base, 30);
            text(font, TEXT, entry.text(), row.x() + 160, base, row.width() - 160);
        }
    }

    private void button(BitmapFont font, TrafficPanelModel.Control control, String label, boolean on) {
        var box = panel.button(control);
        rect(on ? ACTIVE : BUTTON, box.x(), box.y(), box.width(), box.height());
        text(font, TEXT, label, box.x() + 5, box.y() + 17, box.width() - 6);
    }
    private void text(BitmapFont font, Color color, String value, float x, float y, float width) {
        font.setColor(color);
        font.draw(batch, value, x, y, 0, value.length(), width, Align.left, false, "..");
    }
    private void rect(Color color, float x, float y, float width, float height) {
        batch.setColor(color);
        batch.draw(assets.pixel, x, y, width, height);
    }
    private void outline(Color color, float x, float y, float width, float height) {
        rect(color, x, y, width, 1); rect(color, x, y + height - 1, width, 1);
        rect(color, x, y, 1, height); rect(color, x + width - 1, y, 1, height);
    }

    private void unproject(int screenX, int screenY) { viewport.unproject(point.set(screenX, screenY)); }

    /** 输入先经过这里再进入页面。只消费落在面板上的指针和切换键，其余一律返回 false。 */
    private final class PanelInput extends InputAdapter {
        private boolean live() { return monitor.sync(profile.get()); }
        @Override public boolean keyDown(int key) {
            if (key != TOGGLE_KEY || !live()) { return false; }
            panel.toggleVisible();
            return true;
        }
        @Override public boolean keyUp(int key) { return key == TOGGLE_KEY && live(); }
        @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (!live() || !panel.visible()) { return false; }
            unproject(screenX, screenY);
            var hit = panel.hit(point.x, point.y);
            if (hit.control() == TrafficPanelModel.Control.NONE) { return false; }
            capturedPointer = pointer;
            switch (hit.control()) {
                case TITLE -> panel.beginDrag(point.x, point.y);
                case PAUSE -> panel.togglePaused();
                case CLEAR -> { monitor.log().clear(); panel.cleared(); }
                case COLLAPSE -> panel.toggleCollapsed();
                case FILTER -> panel.toggleFilter(hit.kind());
                default -> { }
            }
            return true;
        }
        @Override public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (pointer != capturedPointer) { return false; }
            unproject(screenX, screenY);
            panel.dragTo(point.x, point.y);
            return true;
        }
        @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (pointer != capturedPointer) { return false; }
            capturedPointer = -1;
            panel.endDrag();
            return true;
        }
        /** 只记录指针位置供滚轮判定，不消费事件，下层页面的悬停状态照常更新。 */
        @Override public boolean mouseMoved(int screenX, int screenY) { pointerX = screenX; pointerY = screenY; return false; }
        /** libGDX 中滚轮向下 amountY 为正，与面板“正数向新记录”的约定一致。 */
        @Override public boolean scrolled(float amountX, float amountY) {
            if (!live() || !panel.visible()) { return false; }
            unproject(pointerX, pointerY);
            if (panel.hit(point.x, point.y).control() == TrafficPanelModel.Control.NONE) { return false; }
            panel.scroll(amountY);
            return true;
        }
    }

    @Override public void dispose() {
        if (batch != null) { batch.dispose(); batch = null; }
    }
}
