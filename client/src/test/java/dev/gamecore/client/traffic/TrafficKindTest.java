package dev.gamecore.client.traffic;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.graphics.Color;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

/** 类型颜色互不相同，并且在半透明深色面板上可读。 */
class TrafficKindTest {
    private static final Color PANEL = new Color(.05f, .07f, .10f, 1);

    private static double channel(float value) {
        return value <= .03928 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }
    private static double luminance(Color color) {
        return .2126 * channel(color.r) + .7152 * channel(color.g) + .0722 * channel(color.b);
    }

    @Test void everyKindHasItsOwnColorAndLabel() {
        var colors = new HashSet<Color>();
        var labels = new HashSet<String>();
        for (var kind : TrafficKind.values()) {
            assertTrue(colors.add(kind.color), kind.name());
            assertTrue(labels.add(kind.label), kind.name());
        }
        assertEquals(8, colors.size());
    }

    @Test void colorsAreReadableOnThePanel() {
        double panel = luminance(PANEL);
        for (var kind : TrafficKind.values()) {
            double contrast = (luminance(kind.color) + .05) / (panel + .05);
            assertTrue(contrast >= 4.5, kind + " contrast " + contrast);
        }
    }
}
