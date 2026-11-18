package dev.gamecore.client.tank;

import dev.gamecore.client.timing.FixedStepClock;
import dev.gamecore.tank.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class TankTimingLayoutTest {
    @Test void mapLayoutPreservesAspectRatioAndControlBoundsAcrossResizes() {
        for (float[] size : new float[][]{{1280, 720}, {1920, 1080}, {960, 1280}, {2560, 1440}}) {
            var plan = TankArenaLayout.calculate(size[0], size[1], 24, 18);
            assertEquals(24.0 / 18, plan.board().width() / plan.board().height(), 1e-6);
            assertTrue(plan.board().x() >= 0 && plan.board().y() >= 0);
            assertTrue(plan.board().x() + plan.board().width() < plan.controls().x());
            assertTrue(plan.controls().x() + plan.controls().width() <= size[0]);
        }
    }
    @Test void identicalInputProducesSameWorldAt30_60_144FramesPerSecond() throws IOException {
        TankMap map;
        try (var stream = getClass().getResourceAsStream("/maps/tank/crossfire.tmx")) {
            // client 测试不需要图形；资源由 Gradle 显式加入。
            assertNotNull(stream); map = TmxArenaLoader.load(stream);
        }
        TankSnapshot expected = null;
        for (int fps : new int[]{30, 60, 144}) {
            var session = new TankSession(map, TankMode.BOT, TankBot.Level.HARD, 13); var clock = new FixedStepClock(60); session.start();
            for (int frame = 0; frame < fps * 10; frame++) {
                int steps = clock.advance(1.0 / fps);
                for (int i = 0; i < steps; i++) { session.command(0, new TankCommand(1, 1, true)); session.tick(); }
            }
            assertEquals(600, session.snapshot().tick());
            if (expected == null) { expected = session.snapshot(); } else { assertEquals(expected, session.snapshot()); }
            assertEquals(0, clock.slowFrames());
        }
    }
}
