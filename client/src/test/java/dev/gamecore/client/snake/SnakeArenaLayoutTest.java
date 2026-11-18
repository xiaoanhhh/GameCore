package dev.gamecore.client.snake;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** 用几何不变量验证大棋盘策略，图形验收另确认实际 framebuffer/控件与窗口生命周期。 */
class SnakeArenaLayoutTest {
    @Test void landscapeBoardUsesAllHeightExceptTwelvePixelMargins() {
        var layout = SnakeArenaLayout.calculate(1280, 720);
        assertEquals(696, layout.board().width()); assertEquals(696, layout.board().height());
        assertEquals(12, layout.board().y()); assertEquals(280, layout.controls().width());
        assertTrue(layout.board().width() > 440 * 1.5);
    }
    @Test void resizeAlwaysChoosesLargestFittingSquareWithoutOverlappingControls() {
        for (float[] extent : new float[][]{{1280, 720}, {1280, 800}, {2560, 720}, {1280, 1440}, {1920, 1080}}) {
            var layout = SnakeArenaLayout.calculate(extent[0], extent[1]);
            var board = layout.board(); var controls = layout.controls();
            assertEquals(board.width(), board.height());
            assertEquals(Math.min(extent[0] - 316, extent[1] - 24), board.width());
            assertEquals(12, controls.x() - board.x() - board.width(), .001f);
            assertTrue(board.x() >= 12 && board.y() >= 12);
            assertTrue(controls.x() + controls.width() <= extent[0] - 12 + .001f);
            assertTrue(board.y() + board.height() <= extent[1] - 12 + .001f);
        }
    }
    @Test void narrowAspectUsesWidthAsItsLimitInsteadOfStretchingGridCells() {
        var layout = SnakeArenaLayout.calculate(1280, 1440);
        assertEquals(964, layout.board().width());
        assertEquals(1416, layout.controls().height());
        assertTrue(layout.board().y() > 12);
    }
    @Test void impossibleOrNonFiniteExtentsAreRejected() {
        for (float[] extent : new float[][]{{0, 720}, {300, 720}, {1280, 0}, {Float.NaN, 720}, {1280, Float.POSITIVE_INFINITY}}) {
            assertThrows(IllegalArgumentException.class, () -> SnakeArenaLayout.calculate(extent[0], extent[1]));
        }
    }
}
