package dev.gamecore.client.fight;

import dev.gamecore.api.session.PlayerInput;
import dev.gamecore.client.timing.FixedStepClock;
import dev.gamecore.fight.FightCommand;
import dev.gamecore.fight.FightSession;
import dev.gamecore.fight.FightSessionFactory;
import dev.gamecore.fight.FightSnapshot;
import dev.gamecore.fight.FighterKind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 同样的 Tick 输入在不同显示刷新率下必须产生相同格斗结果；卡顿沿用平台最多五步的降级策略。 */
class FightTimingTest {
    private FightSnapshot run(int fps) {
        var clock = new FixedStepClock(60);
        var s = new FightSession(new FightSessionFactory.Config(FighterKind.NAIWA, FighterKind.FEIDUDU, FightSessionFactory.Mode.BOT), 42);
        s.start();
        for (int frame = 0; frame < fps * 10; frame++) {
            int count = clock.advance(1.0 / fps);
            for (int i = 0; i < count; i++) {
                long t = s.tickNumber() + 1;
                s.submitInput(new PlayerInput<>(t, 0, 0, new FightCommand(t % 150 < 80 ? 1 : -1,
                        t % 120 == 0, t % 29 == 0, t % 97 == 0, true, t % 100 > 85, false))); s.tick();
            }
        }
        assertTrue(s.tickNumber() == 600 || s.isFinished()); return s.snapshot();
    }
    @Test void renderingAt30_60_144FpsPreservesCombat() { assertEquals(run(30), run(60)); assertEquals(run(60), run(144)); }
}
