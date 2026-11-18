package dev.gamecore.client.snake;
import dev.gamecore.client.timing.FixedStepClock;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** 验证帧率变化、停顿及生命周期对固定步长的影响。 */
class FixedStepClockTest {
    @Test void equalTimeAtDifferentFrameRatesProducesSameTicks() {
        for (int fps : new int[]{30, 60, 144}) {
            FixedStepClock clock = new FixedStepClock(10); int ticks = 0;
            for (int i = 0; i < fps * 60; i++) { ticks += clock.advance(1.0 / fps); }
            assertEquals(600, ticks);
        }
    }
    @Test void longFrameIsClampedAndDoesNotBuildCatchupDebt() {
        FixedStepClock clock = new FixedStepClock(10);
        assertEquals(2, clock.advance(100)); assertEquals(1, clock.advance(.05)); assertEquals(0, clock.advance(0));
    }
    @Test void resetDiscardsRemainder() {
        FixedStepClock clock = new FixedStepClock(10); clock.advance(.09); clock.reset();
        assertEquals(0, clock.advance(.02)); assertEquals(1, clock.advance(.08));
    }
    @Test void invalidDeltaDoesNotPoisonClock() {
        FixedStepClock clock = new FixedStepClock(10);
        for (double delta : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1}) { assertEquals(0, clock.advance(delta)); }
        assertEquals(1, clock.advance(.1));
        assertThrows(IllegalArgumentException.class, () -> new FixedStepClock(0));
    }
    @Test void catchupLimitDropsWholeStepsKeepsRemainderAndMeasuresLostTime() {
        FixedStepClock clock = new FixedStepClock(60);
        assertEquals(5, clock.advance(.26));
        assertEquals(1, clock.slowFrames());
        assertEquals(.26 - 5.0 / 60, clock.droppedSeconds(), 1e-8);
        assertEquals(0, clock.advance(0));
        assertEquals(1, clock.advance(1.0 / 60));
        clock.reset(); assertEquals(1, clock.slowFrames());
        clock.restart(); assertEquals(0, clock.slowFrames()); assertEquals(0, clock.droppedSeconds());
    }
    @Test void twentyHertzAllowsFiveTicksAndDoesNotTreatNormalCatchupAsSlow() {
        FixedStepClock clock = new FixedStepClock(20);
        assertEquals(5, clock.advance(.25)); assertEquals(0, clock.slowFrames());
        assertEquals(5, clock.advance(2)); assertEquals(1, clock.slowFrames());
        assertEquals(1.75, clock.droppedSeconds(), 1e-8); assertEquals(0, clock.advance(0));
    }
}
