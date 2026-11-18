package dev.gamecore.client.traffic;

import static org.junit.jupiter.api.Assertions.*;

import dev.gamecore.client.traffic.TrafficPanelModel.Control;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 拖动边界、命中区域和过滤滚动都是纯状态，不打开窗口验证。 */
class TrafficPanelModelTest {
    private static final float W = 1280, H = 720;
    private final TrafficPanelModel panel = placed();

    private static TrafficPanelModel placed() {
        var model = new TrafficPanelModel();
        model.resize(W, H);
        return model;
    }
    private static float centerX(TrafficPanelModel.Box box) { return box.x() + box.width() / 2; }
    private static float centerY(TrafficPanelModel.Box box) { return box.y() + box.height() / 2; }
    private static List<TrafficEntry> rows(int count, TrafficKind kind) {
        var rows = new ArrayList<TrafficEntry>();
        for (int i = 0; i < count; i++) { rows.add(new TrafficEntry("00:00:00.000", kind, TrafficDirection.RECEIVED, kind + " " + i)); }
        return rows;
    }

    @Test void startsInTheBottomRightCornerFullyInsideTheWindow() {
        assertEquals(W - TrafficPanelModel.WIDTH - TrafficPanelModel.MARGIN, panel.x());
        assertEquals(TrafficPanelModel.MARGIN, panel.y());
        assertTrue(panel.visible());
        assertTrue(TrafficPanelModel.ROWS >= 12);
    }

    @Test void draggingKeepsTheGrabPointUnderTheCursor() {
        float px = panel.x() + 100, py = panel.y() + TrafficPanelModel.HEIGHT - 10;
        panel.beginDrag(px, py);
        assertTrue(panel.dragging());
        float x = panel.x(), y = panel.y();
        panel.dragTo(px - 300, py + 150);
        assertEquals(x - 300, panel.x());
        assertEquals(y + 150, panel.y());
        panel.endDrag();
        panel.dragTo(0, 0);
        assertEquals(x - 300, panel.x(), "Movement stops when the mouse is released");
    }

    @Test void draggingCannotLeaveTheWindowInAnyDirection() {
        panel.beginDrag(panel.x(), panel.y());
        for (float[] target : new float[][]{{-5000, -5000}, {5000, 5000}, {-5000, 5000}, {5000, -5000}}) {
            panel.dragTo(target[0], target[1]);
            assertTrue(panel.x() >= 0 && panel.x() + panel.width() <= W, "x " + panel.x());
            assertTrue(panel.y() >= 0 && panel.y() + panel.height() <= H, "y " + panel.y());
        }
    }

    @Test void shrinkingTheWindowPullsThePanelBackAndKeepsThePlaceOtherwise() {
        panel.beginDrag(panel.x(), panel.y());
        panel.dragTo(800, 400);
        panel.endDrag();
        panel.resize(2000, 1200);
        assertEquals(800, panel.x(), "Growing the window does not move the panel");
        assertEquals(400, panel.y());
        panel.resize(1000, 600);
        assertEquals(1000 - TrafficPanelModel.WIDTH, panel.x());
        assertEquals(300, panel.y());
    }

    @Test void everyPartOfThePanelConsumesTheClickAndOutsideDoesNot() {
        assertEquals(Control.NONE, panel.hit(panel.x() - 1, panel.y() + 5).control());
        assertEquals(Control.NONE, panel.hit(panel.x() + 5, panel.y() - 1).control());
        assertEquals(Control.NONE, panel.hit(panel.x() + panel.width(), panel.y() + 5).control());
        assertEquals(Control.BODY, panel.hit(panel.x() + 5, panel.y() + 5).control());
        var title = panel.title();
        assertEquals(Control.TITLE, panel.hit(title.x() + 20, centerY(title)).control());
        for (Control control : List.of(Control.PAUSE, Control.CLEAR, Control.COLLAPSE)) {
            var box = panel.button(control);
            assertEquals(control, panel.hit(centerX(box), centerY(box)).control());
        }
        for (var kind : TrafficKind.values()) {
            var chip = panel.chip(kind);
            var hit = panel.hit(centerX(chip), centerY(chip));
            assertEquals(Control.FILTER, hit.control());
            assertEquals(kind, hit.kind());
        }
    }

    @Test void hiddenPanelDoesNotInterceptClicks() {
        float x = panel.x() + 5, y = panel.y() + 5;
        panel.toggleVisible();
        assertEquals(Control.NONE, panel.hit(x, y).control());
        panel.toggleVisible();
        assertEquals(Control.BODY, panel.hit(x, y).control());
    }

    @Test void collapsingKeepsTheTitleBarInPlaceAndRestoresTheBody() {
        var before = panel.title();
        panel.toggleCollapsed();
        assertEquals(TrafficPanelModel.TITLE, panel.height());
        assertEquals(before, panel.title());
        assertEquals(Control.NONE, panel.hit(panel.x() + 5, panel.y() - 20).control(), "The former body no longer blocks clicks");
        panel.toggleCollapsed();
        assertEquals(TrafficPanelModel.HEIGHT, panel.height());
        assertEquals(before, panel.title());
    }

    @Test void expandingNearTheBottomStaysInsideTheWindow() {
        panel.toggleCollapsed();
        panel.beginDrag(panel.x(), panel.y());
        panel.dragTo(panel.x(), -1000);
        panel.endDrag();
        assertEquals(0, panel.y());
        panel.toggleCollapsed();
        assertEquals(0, panel.y());
        assertEquals(TrafficPanelModel.HEIGHT, panel.height());
    }

    @Test void showsTheNewestRowsAtTheBottom() {
        panel.entries(rows(40, TrafficKind.ROOM));
        var visible = panel.rows();
        assertEquals(TrafficPanelModel.ROWS, visible.size());
        assertEquals("ROOM 39", visible.getLast().text());
        assertEquals(40, panel.matching());
    }

    @Test void pausingFreezesTheViewUntilResumed() {
        panel.entries(rows(5, TrafficKind.ROOM));
        panel.togglePaused();
        assertTrue(panel.frozen());
        panel.entries(rows(9, TrafficKind.ROOM));
        assertEquals("ROOM 4", panel.rows().getLast().text());
        panel.togglePaused();
        panel.entries(rows(9, TrafficKind.ROOM));
        assertEquals("ROOM 8", panel.rows().getLast().text());
    }

    @Test void wheelBrowsesHistoryAndReturnsToLiveAtTheBottom() {
        panel.entries(rows(40, TrafficKind.ROOM));
        panel.scroll(-1);
        assertEquals(3, panel.offset());
        assertEquals("ROOM 36", panel.rows().getLast().text());
        assertTrue(panel.frozen(), "Reading history must not jump");
        panel.entries(rows(60, TrafficKind.ROOM));
        assertEquals("ROOM 36", panel.rows().getLast().text());
        panel.scroll(-1000);
        assertEquals(40 - TrafficPanelModel.ROWS, panel.offset());
        assertEquals("ROOM 0", panel.rows().getFirst().text());
        panel.scroll(1000);
        assertEquals(0, panel.offset());
        assertFalse(panel.frozen());
    }

    @Test void filterHidesAKindUntilToggledAgain() {
        var mixed = new ArrayList<>(rows(3, TrafficKind.HEARTBEAT));
        mixed.addAll(rows(2, TrafficKind.ERROR));
        panel.entries(mixed);
        assertEquals(5, panel.matching());
        panel.toggleFilter(TrafficKind.HEARTBEAT);
        assertTrue(panel.filtered(TrafficKind.HEARTBEAT));
        assertEquals(2, panel.matching());
        assertTrue(panel.rows().stream().allMatch(row -> row.kind() == TrafficKind.ERROR));
        panel.toggleFilter(TrafficKind.HEARTBEAT);
        assertEquals(5, panel.matching());
    }

    @Test void filteringDownWhileScrolledKeepsAValidOffset() {
        var mixed = new ArrayList<>(rows(40, TrafficKind.HEARTBEAT));
        mixed.addAll(rows(2, TrafficKind.ERROR));
        panel.entries(mixed);
        panel.scroll(-5);
        panel.toggleFilter(TrafficKind.HEARTBEAT);
        assertEquals(0, panel.offset());
        assertEquals(2, panel.rows().size());
    }

    @Test void clearingEmptiesTheViewAndReturnsToLive() {
        panel.entries(rows(40, TrafficKind.ROOM));
        panel.scroll(-1);
        panel.cleared();
        assertEquals(0, panel.offset());
        assertTrue(panel.rows().isEmpty());
    }

    @Test void fixedPanelTextIsInsideTheFontSubset() throws Exception {
        String glyphs;
        try (var input = getClass().getResourceAsStream("/fonts/characters.txt")) {
            assertNotNull(input);
            glyphs = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var text = new StringBuilder("服务器交互 条 F9 隐藏 暂停 继续 清空 展开 收起 .. : · × → # %");
        text.append("握手请求 握手完成 游戏 房间 加入房间 已加入 比赛 序号 比赛结果 未识别的消息 往返 ms 版本 准备 暂停 账户 已登录，开始记录 TCP 已连接 已断开 连接失败 tick B");
        for (var kind : TrafficKind.values()) { text.append(kind.label); }
        for (var direction : TrafficDirection.values()) { text.append(direction.label); }
        text.codePoints().filter(c -> !Character.isWhitespace(c)).forEach(c -> assertTrue(glyphs.indexOf(c) >= 0, "Missing glyph " + Character.toString(c)));
    }
}
