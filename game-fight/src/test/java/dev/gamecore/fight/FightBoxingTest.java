package dev.gamecore.fight;

import dev.gamecore.api.session.PlayerInput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 仅通过公开输入验证拳击轮换、单拳排队和打断，不注入角色状态。 */
class FightBoxingTest {
    private static final FightCommand NONE = FightCommand.NONE;
    private static final FightCommand PUNCH = new FightCommand(0, false, true, false, false, false, false);
    private static FightSession game() {
        var s = new FightSession(new FightSessionFactory.Config(FighterKind.NAIWA, FighterKind.FEIDUDU, FightSessionFactory.Mode.DUEL), 5);
        s.start(); return s;
    }
    private static void step(FightSession s, FightCommand a, FightCommand b) {
        s.submitInput(new PlayerInput<>(s.tickNumber() + 1, 0, 0, a));
        s.submitInput(new PlayerInput<>(s.tickNumber() + 1, 1, 1, b)); s.tick();
    }
    private static void waitFor(FightSession s, int ticks) { for (int i = 0; i < ticks; i++) { step(s, NONE, NONE); } }
    @Test void bothCharactersCycleAllThreePunchesEvenWhenTheyMiss() {
        var s = game();
        for (var punch : new FightPunch[]{FightPunch.JAB, FightPunch.CROSS, FightPunch.HOOK, FightPunch.JAB}) {
            step(s, PUNCH, PUNCH);
            for (var f : s.snapshot().fighters()) { assertEquals(punch.action(), f.action()); assertEquals(0, f.poseTick()); }
            waitFor(s, punch.duration() + 2);
        }
        for (var f : s.snapshot().fighters()) { assertEquals(4, f.attacks()); assertEquals(0, f.hits()); assertEquals(100, f.health()); }
    }
    @Test void earlyRapidClicksQueueOnlyOneNextPunchUntilItActuallyStarts() {
        var s = game(); step(s, PUNCH, NONE);
        for (int i = 0; i < 8; i++) { step(s, PUNCH, NONE); }
        waitFor(s, 50);
        var f = s.snapshot().fighters().get(0);
        assertEquals(2, f.attacks()); assertEquals(FightSnapshot.Action.IDLE, f.action()); assertEquals(0, f.hits());
    }
    @Test void earlyClickDuringLongHookSurvivesFullRecoveryAndWrapsToJab() {
        var s = game();
        for (var punch : new FightPunch[]{FightPunch.JAB, FightPunch.CROSS}) { step(s, PUNCH, NONE); waitFor(s, punch.duration() + 2); }
        step(s, PUNCH, NONE); assertEquals(FightSnapshot.Action.PUNCH_3, s.snapshot().fighters().get(0).action());
        step(s, PUNCH, NONE); // 早于普通技能邮箱的 12 步覆盖范围，仍须完成摆拳再接刺拳。
        boolean jab = false;
        for (int i = 0; i < 45; i++) { step(s, NONE, NONE); jab |= s.snapshot().fighters().get(0).action() == FightSnapshot.Action.PUNCH; }
        assertTrue(jab); assertEquals(4, s.snapshot().fighters().get(0).attacks());
    }
    @Test void pauseDiscardsQueuedPunchAndInactivityRestartsAtJab() {
        var s = game(); step(s, PUNCH, NONE); step(s, PUNCH, NONE); s.pause(); s.resume(); waitFor(s, 70);
        assertEquals(1, s.snapshot().fighters().get(0).attacks());
        step(s, PUNCH, NONE); assertEquals(FightSnapshot.Action.PUNCH, s.snapshot().fighters().get(0).action());
    }
    @Test void beingHitCancelsTheQueuedBoxingFollowup() {
        var s = game(); var right = new FightCommand(1, false, false, false, false, false, false);
        var left = new FightCommand(-1, false, false, false, false, false, false);
        for (int i = 0; i < 160 && s.snapshot().fighters().get(1).x() - s.snapshot().fighters().get(0).x() > 100; i++) { step(s, right, left); }
        waitFor(s, 5); step(s, PUNCH, PUNCH); step(s, PUNCH, NONE); waitFor(s, 60);
        assertEquals(95, s.snapshot().fighters().get(0).health());
        assertEquals(1, s.snapshot().fighters().get(0).attacks());
    }
}
