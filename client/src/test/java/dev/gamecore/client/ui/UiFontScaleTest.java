package dev.gamecore.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 无图形环境验证物理像素、留边、密度分档与拖动窗口时的稳定策略。 */
class UiFontScaleTest {
    @ParameterizedTest
    @CsvSource({"0,0,1", "-1,720,1", "960,540,1", "1280,720,1", "1281,721,1.25",
            "1600,900,1.25", "1920,1080,1.5", "2560,1440,2", "3840,2160,3",
            "800,900,1", "2560,720,1", "10240,5760,4"})
    void choosesDensityFromDrawablePixelsAndShortSide(int width, int height, float density) {
        assertEquals(density, UiFontScale.densityFor(width, height));
    }

    @ParameterizedTest
    @CsvSource({"14,1.25,18", "18,1.25,23", "18,1.5,27", "18,2,36", "40,3,120", "32,4,128"})
    void rasterFontHasEnoughPixelsWithoutChangingLogicalSize(int logical, float density, int expected) {
        int raster = UiFontScale.rasterSize(logical, density);
        assertEquals(expected, raster);
        assertTrue(raster >= logical * density);
        assertEquals(logical, raster * (logical / (float) raster), .0001f);
    }

    @Test void waitsForStableDensityRatherThanRebuildingEachResizeEvent() {
        UiFontScale scale = new UiFontScale(1);
        assertEquals(1, scale.advance(2560, 1440, .05f));
        assertEquals(1, scale.advance(1920, 1080, .1f));
        assertEquals(1, scale.advance(1920, 1080, .049f));
        assertEquals(1.5f, scale.advance(1920, 1080, .002f));
        assertEquals(1.5f, scale.advance(1800, 1013, .1f));
    }

    @Test void canReturnToNativeDensityWithoutRebuildingForMinimizedWindows() {
        UiFontScale scale = new UiFontScale(2);
        assertEquals(2, scale.advance(0, 0, 1));
        assertEquals(2, scale.advance(1280, 720, .1f));
        assertEquals(1, scale.advance(1280, 720, .1f));
        assertEquals(1, scale.advance(1280, 720, 1));
    }

    @Test void rejectsInvalidRasterRequests() {
        assertThrows(IllegalArgumentException.class, () -> UiFontScale.rasterSize(0, 1));
        assertThrows(IllegalArgumentException.class, () -> UiFontScale.rasterSize(18, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> UiFontScale.rasterSize(18, Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> UiFontScale.rasterSize(18, .5f));
    }
}
