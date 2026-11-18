package dev.gamecore.lwjgl3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** 无需显示器与本地库，验证窗口参数边界和默认启动约定。 */
class DesktopSettingsTest {
    @Test
    void providesExpectedBootstrapDefaults() {
        assertEquals(new DesktopSettings("GameCore", 1280, 720, 60), DesktopSettings.defaults());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingTitles(String title) {
        assertThrows(IllegalArgumentException.class, () -> new DesktopSettings(title, 1280, 720, 60));
    }

    @ParameterizedTest
    @CsvSource({"0,720", "-1,720", "1280,0", "1280,-1"})
    void rejectsInvalidDimensions(int width, int height) {
        assertThrows(IllegalArgumentException.class, () -> new DesktopSettings("GameCore", width, height, 60));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsInvalidForegroundFps(int fps) {
        assertThrows(IllegalArgumentException.class, () -> new DesktopSettings("GameCore", 1280, 720, fps));
    }

    @Test
    void acceptsCustomWindowSettings() {
        DesktopSettings settings = new DesktopSettings("GameCore Dev", 960, 540, 144);
        assertEquals("GameCore Dev", settings.title());
        assertEquals(960, settings.width());
        assertEquals(540, settings.height());
        assertEquals(144, settings.foregroundFps());
    }
}
