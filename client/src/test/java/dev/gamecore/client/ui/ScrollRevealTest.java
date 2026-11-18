package dev.gamecore.client.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 三卡片目录真实回归的几何验证：上下两种坐标原点不能混用。 */
class ScrollRevealTest {
    @Test void visibleTopRowDoesNotScrollToBottom() { assertEquals(0, ScrollReveal.offset(0, 218, 218, 218, 210)); }
    @Test void bottomRowScrollsIntoView() { assertEquals(218, ScrollReveal.offset(0, 218, 218, 0, 210)); }
    @Test void returningToTopRowRevealsItsFullBounds() { assertEquals(8, ScrollReveal.offset(218, 218, 218, 218, 210)); }
    @Test void visibleRowKeepsOffset() { assertEquals(40, ScrollReveal.offset(40, 400, 218, 370, 80)); }
    @Test void oversizedAndOutsideBoundsAreClamped() {
        assertEquals(118, ScrollReveal.offset(0, 400, 218, 200, 300));
        assertEquals(0, ScrollReveal.offset(0, 0, 218, 0, 210));
    }
}
