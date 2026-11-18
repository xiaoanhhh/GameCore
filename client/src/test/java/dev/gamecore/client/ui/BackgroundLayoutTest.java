package dev.gamecore.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** 不依赖 GL 的背景几何验收；真正的全窗口覆盖另由 framebuffer 截图和采样验证。 */
class BackgroundLayoutTest {
    @Test void matchingAspectHasNoCropOrStretch() {
        var rect = BackgroundLayout.cover(1600, 900, 1280, 720);
        assertEquals(new BackgroundLayout.Rect(0, 0, 1280, 720), rect);
    }
    @Test void wideWindowCropsTopAndBottomEqually() {
        var rect = BackgroundLayout.cover(1600, 900, 2560, 720);
        assertEquals(0, rect.x()); assertEquals(-360, rect.y());
        assertEquals(2560, rect.width()); assertEquals(1440, rect.height());
    }
    @Test void portraitWindowCropsBothSidesEqually() {
        var rect = BackgroundLayout.cover(1600, 900, 800, 900);
        assertEquals(-400, rect.x()); assertEquals(0, rect.y());
        assertEquals(1600, rect.width()); assertEquals(900, rect.height());
    }
    @Test void allSupportedSizesCoverViewportWithoutChangingImageAspect() {
        for (int[] extent : new int[][]{{960,540}, {1280,720}, {1920,1080}, {2560,1494}, {3840,2160}, {800,900}}) {
            var rect = BackgroundLayout.cover(1672, 941, extent[0], extent[1]);
            assertTrue(rect.x() <= .001f && rect.y() <= .001f);
            assertTrue(rect.width() + .001f >= extent[0] && rect.height() + .001f >= extent[1]);
            assertEquals(1672f / 941, rect.width() / rect.height(), .0001f);
            assertEquals(extent[0], 2 * rect.x() + rect.width(), .001f);
            assertEquals(extent[1], 2 * rect.y() + rect.height(), .001f);
        }
    }
    @Test void minimizedViewportProducesEmptyDrawing() {
        assertEquals(new BackgroundLayout.Rect(0, 0, 0, 0), BackgroundLayout.cover(1672, 941, 0, 720));
        assertEquals(new BackgroundLayout.Rect(0, 0, 0, 0), BackgroundLayout.cover(1672, 941, 1280, 0));
    }
    @Test void invalidImagesAreRejected() {
        for (float value : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> BackgroundLayout.cover(value, 941, 1280, 720));
            assertThrows(IllegalArgumentException.class, () -> BackgroundLayout.cover(1672, value, 1280, 720));
        }
    }
    @Test void invalidWindowsAreRejected() {
        for (float value : new float[]{-1, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> BackgroundLayout.cover(1672, 941, value, 720));
            assertThrows(IllegalArgumentException.class, () -> BackgroundLayout.cover(1672, 941, 1280, value));
        }
    }
}
