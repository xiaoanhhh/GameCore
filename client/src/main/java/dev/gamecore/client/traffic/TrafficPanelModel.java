package dev.gamecore.client.traffic;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 面板的状态、布局和命中判定，不依赖 GL，单独测试。坐标是视口逻辑坐标，原点在左下角。
 * 对象随窗口存在而不随页面重建，所以切换页面时位置、过滤和收起状态都保留。
 * 暂停或向上翻看历史时冻结显示内容，记录仍在后台写入。
 */
public final class TrafficPanelModel {
    public static final float WIDTH = 440, HEIGHT = 300, TITLE = 28, CHIPS = 24, ROW = 18, PAD = 8, MARGIN = 12;
    public static final int ROWS = (int) ((HEIGHT - TITLE - CHIPS - PAD) / ROW);
    private static final float BUTTON = 42;
    private static final int WHEEL_ROWS = 3;

    public enum Control { NONE, BODY, TITLE, PAUSE, CLEAR, COLLAPSE, FILTER }
    public record Hit(Control control, TrafficKind kind) { static final Hit NONE = new Hit(Control.NONE, null); }
    public record Box(float x, float y, float width, float height) {
        boolean contains(float px, float py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }

    private final Set<TrafficKind> hidden = EnumSet.noneOf(TrafficKind.class);
    private List<TrafficEntry> latest = List.of(), filtered = List.of();
    private float x, y, worldWidth, worldHeight, grabX, grabY;
    private boolean placed, visible = true, collapsed, paused, dragging;
    private int offset;

    /** 窗口尺寸变化：首次放到右下角，之后保持位置并收回窗口内。 */
    public void resize(float width, float height) {
        worldWidth = width; worldHeight = height;
        if (!placed) { placed = true; x = width - WIDTH - MARGIN; y = MARGIN; }
        clamp();
    }
    private void clamp() {
        x = Math.max(0, Math.min(x, worldWidth - WIDTH));
        y = Math.max(0, Math.min(y, worldHeight - height()));
    }

    public float x() { return x; }
    public float y() { return y; }
    public float width() { return WIDTH; }
    public float height() { return collapsed ? TITLE : HEIGHT; }
    public boolean visible() { return visible; }
    public boolean collapsed() { return collapsed; }
    public boolean paused() { return paused; }
    public boolean dragging() { return dragging; }
    public boolean filtered(TrafficKind kind) { return hidden.contains(kind); }
    public int offset() { return offset; }
    public int matching() { return filtered.size(); }
    /** 冻结时不接收新内容，避免正在看的行被滚走。 */
    public boolean frozen() { return paused || offset > 0; }

    public void toggleVisible() { visible = !visible; dragging = false; }
    public void togglePaused() { paused = !paused; }
    /** 收起时标题栏留在原处，展开后再收回窗口内。 */
    public void toggleCollapsed() {
        float top = y + height();
        collapsed = !collapsed;
        y = top - height();
        clamp();
    }
    public void toggleFilter(TrafficKind kind) {
        if (!hidden.remove(kind)) { hidden.add(kind); }
        refilter();
    }
    public void cleared() { latest = filtered = List.of(); offset = 0; }

    /** 接收最新记录；冻结期间忽略。 */
    public void entries(List<TrafficEntry> value) {
        if (frozen()) { return; }
        latest = value; refilter();
    }
    private void refilter() {
        var next = new ArrayList<TrafficEntry>(latest.size());
        for (var entry : latest) { if (!hidden.contains(entry.kind())) { next.add(entry); } }
        filtered = next;
        offset = Math.min(offset, maxOffset());
    }
    private int maxOffset() { return Math.max(0, filtered.size() - ROWS); }
    /** amount 与滚轮方向一致：正数向新记录方向。回到最底部后恢复实时。 */
    public void scroll(float amount) {
        offset = Math.max(0, Math.min(maxOffset(), offset - Math.round(amount * WHEEL_ROWS)));
    }
    /** 当前可见行，旧的在上，最新的在最下面。 */
    public List<TrafficEntry> rows() {
        int end = filtered.size() - offset;
        return filtered.subList(Math.max(0, end - ROWS), end);
    }

    public void beginDrag(float px, float py) { dragging = true; grabX = px - x; grabY = py - y; }
    public void dragTo(float px, float py) {
        if (!dragging) { return; }
        x = px - grabX; y = py - grabY; clamp();
    }
    public void endDrag() { dragging = false; }

    public Box title() { return new Box(x, y + height() - TITLE, WIDTH, TITLE); }
    public Box button(Control control) {
        int index = switch (control) { case COLLAPSE -> 0; case CLEAR -> 1; case PAUSE -> 2; default -> throw new IllegalArgumentException("Button"); };
        return new Box(x + WIDTH - (index + 1) * BUTTON - 4, y + height() - TITLE + 3, BUTTON - 4, TITLE - 6);
    }
    public Box chip(TrafficKind kind) {
        float width = (WIDTH - 2 * PAD) / TrafficKind.values().length;
        return new Box(x + PAD + kind.ordinal() * width, y + HEIGHT - TITLE - CHIPS + 3, width - 3, CHIPS - 6);
    }
    /** 第 index 行（从上往下）的矩形。 */
    public Box row(int index) { return new Box(x + PAD, y + HEIGHT - TITLE - CHIPS - (index + 1) * ROW, WIDTH - 2 * PAD, ROW); }

    /** 面板范围内都算命中，空白区域也要吃掉点击，不能穿透到下层页面。 */
    public Hit hit(float px, float py) {
        if (!visible || !new Box(x, y, WIDTH, height()).contains(px, py)) { return Hit.NONE; }
        for (Control control : List.of(Control.PAUSE, Control.CLEAR, Control.COLLAPSE)) {
            if (button(control).contains(px, py)) { return new Hit(control, null); }
        }
        if (title().contains(px, py)) { return new Hit(Control.TITLE, null); }
        if (!collapsed) {
            for (TrafficKind kind : TrafficKind.values()) {
                if (chip(kind).contains(px, py)) { return new Hit(Control.FILTER, kind); }
            }
        }
        return new Hit(Control.BODY, null);
    }
}
