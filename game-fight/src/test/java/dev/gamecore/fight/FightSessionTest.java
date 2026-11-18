package dev.gamecore.fight;

import dev.gamecore.api.event.GameEvent;
import dev.gamecore.api.replay.RecordingSession;
import dev.gamecore.api.replay.ReplayRunner;
import dev.gamecore.api.session.PlayerInput;
import dev.gamecore.api.session.SessionState;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

/** 通过公开输入驱动真实世界，覆盖战斗、生命周期、数值边界和既有回放框架；不注入内部状态。 */
class FightSessionTest {
    private static final FightCommand NONE = FightCommand.NONE;
    private static final FightCommand PUNCH = new FightCommand(0, false, true, false, false, false, false);
    private static final FightCommand RANGE = new FightCommand(0, false, false, true, false, false, false);
    private static final FightCommand ULT = new FightCommand(0, false, false, false, true, false, false);
    private static final FightCommand GUARD = new FightCommand(0, false, false, false, false, true, false);
    private static FightCommand move(int direction) { return new FightCommand(direction, false, false, false, false, false, false); }
    private static FightSession fresh() { return fresh(FighterKind.NAIWA, FighterKind.FEIDUDU); }
    private static FightSession fresh(FighterKind a, FighterKind b) {
        var session = new FightSession(new FightSessionFactory.Config(a, b, FightSessionFactory.Mode.DUEL), 73); session.start(); return session;
    }
    private static void step(FightSession s, FightCommand one, FightCommand two) {
        if (s.isFinished()) { return; }
        assertTrue(s.submitInput(new PlayerInput<>(s.tickNumber() + 1, 0, 0, one)));
        assertTrue(s.submitInput(new PlayerInput<>(s.tickNumber() + 1, 1, 1, two))); s.tick();
    }
    private static void steps(FightSession s, int n, FightCommand a, FightCommand b) { for (int i = 0; i < n; i++) { step(s, a, b); } }
    private static void contact(FightSession s) {
        for (int i = 0; i < 160 && fighter(s,1).x() - fighter(s,0).x() > FightSession.BODY_SPACING + .01; i++) { step(s, move(1), move(-1)); }
        steps(s, 5, NONE, NONE);
    }
    private static FightSnapshot.Fighter fighter(FightSession s, int p) { return s.snapshot().fighters().get(p); }
    @Test void readyPauseResumeAndTerminalFreeze() {
        var s = new FightSession(new FightSessionFactory.Config(FighterKind.NAIWA, FighterKind.FEIDUDU, FightSessionFactory.Mode.DUEL), 1);
        var initial = s.snapshot(); s.tick(); s.resume(); assertEquals(initial, s.snapshot());
        assertFalse(s.submitInput(new PlayerInput<>(1, 0, 0, PUNCH))); s.start();
        s.submitInput(new PlayerInput<>(1, 0, 0, move(1))); s.pause(); var paused = s.snapshot(); s.tick(); s.start();
        assertEquals(paused, s.snapshot()); s.resume(); s.tick(); assertEquals(560, fighter(s, 0).x());
        steps(s, FightSession.MATCH_TICKS, NONE, NONE); assertEquals(FightSnapshot.Result.DRAW, s.snapshot().result());
        var end = s.snapshot(); var events = s.events(); s.tick(); s.resume(); s.start(); s.pause(); assertEquals(end, s.snapshot());
        assertEquals(events, s.events()); assertEquals(FightSession.MATCH_TICKS, s.tickNumber());
    }
    @Test void validatesInputTickSequenceAndPlayer() {
        var s = fresh(); assertFalse(s.submitInput(new PlayerInput<>(2, 0, 0, PUNCH)));
        assertTrue(s.submitInput(new PlayerInput<>(1, 0, 3, move(-1))));
        assertTrue(s.submitInput(new PlayerInput<>(1, 0, 4, move(1))));
        assertFalse(s.submitInput(new PlayerInput<>(1, 1, 4, PUNCH)));
        assertThrows(IllegalArgumentException.class, () -> s.submitInput(new PlayerInput<>(1, 2, 5, NONE)));
        s.tick(); assertTrue(fighter(s, 0).x() > 560); s.tick(); assertEquals(560.62, fighter(s, 0).x(), 1e-6);
        assertThrows(IllegalArgumentException.class, () -> new FightCommand(2, false, false, false, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> FightSessionFactory.Mode.fromId("unknown"));
    }
    @Test void bodySeparationAndArenaBounds() {
        var s = fresh(); steps(s, 200, move(1), move(-1));
        assertTrue(fighter(s, 1).x() - fighter(s, 0).x() >= FightSession.BODY_SPACING - .01);
        steps(s, 300, move(-1), move(1)); assertEquals(60, fighter(s, 0).x()); assertEquals(2180, fighter(s, 1).x());
    }
    @Test void punchHasStartupRangeAndOnlyOneDamagePerPress() {
        var far = fresh(); steps(far, 30, PUNCH, NONE); assertEquals(100, fighter(far, 1).health());
        var s = fresh(); contact(s); step(s, PUNCH, NONE);
        steps(s, 4, NONE, NONE); assertEquals(100, fighter(s, 1).health()); step(s, NONE, NONE);
        assertEquals(95, fighter(s, 1).health()); steps(s, 25, NONE, NONE); assertEquals(95, fighter(s, 1).health());
    }
    @Test void simultaneousPunchesBothLand() {
        var s = fresh(); contact(s); step(s, PUNCH, PUNCH); steps(s, 5, NONE, NONE);
        assertEquals(95, fighter(s, 0).health()); assertEquals(95, fighter(s, 1).health());
        assertEquals(2, s.events().stream().filter(GameEvent.HealthChanged.class::isInstance).count());
    }
    @Test void jumpingAvoidsMeleeAndReturnsToGround() {
        var s = fresh(); contact(s);
        step(s, NONE, new FightCommand(0, true, false, false, false, false, false)); steps(s, 14, NONE, NONE);
        step(s, PUNCH, NONE); steps(s, 6, NONE, NONE); assertEquals(100, fighter(s, 1).health());
        steps(s, 60, NONE, NONE); assertEquals(0, fighter(s, 1).y());
    }
    @Test void thirdAirJumpDoesNotResetFlight() {
        var s = fresh(); var jump = new FightCommand(0, true, false, false, false, false, false);
        step(s, jump, NONE); steps(s, 12, NONE, NONE); step(s, jump, NONE); steps(s, 9, NONE, NONE);
        assertEquals(2, fighter(s, 0).jumpCount()); double vy = fighter(s, 0).vy(); step(s, jump, NONE);
        assertTrue(fighter(s, 0).vy() < vy); assertEquals(2, fighter(s, 0).jumpCount());
    }
    @Test void lightningTravelsAndDealsDamage() {
        var s = fresh(); step(s, RANGE, NONE); assertEquals(12.1, fighter(s, 0).energy(), 1e-6);
        steps(s, 11, NONE, NONE); assertTrue(s.snapshot().projectiles().isEmpty()); step(s, NONE, NONE);
        assertEquals(FighterKind.NAIWA, s.snapshot().projectiles().getFirst().kind());
        steps(s, 60, NONE, NONE); assertEquals(93, fighter(s, 1).health()); assertEquals(0, fighter(s, 1).slowTicks());
        assertTrue(s.snapshot().projectiles().isEmpty());
    }
    @Test void webInflictsTemporarySlow() {
        var s = fresh(FighterKind.FEIDUDU, FighterKind.NAIWA); step(s, RANGE, NONE);
        steps(s, 90, NONE, NONE); assertEquals(94, fighter(s, 1).health()); assertTrue(fighter(s, 1).slowTicks() > 0);
        double before = fighter(s, 1).x(); steps(s, 20, NONE, move(1)); assertTrue(fighter(s, 1).x() - before < 20 * 5.3);
        steps(s, 120, NONE, NONE); assertEquals(0, fighter(s, 1).slowTicks());
    }
    @Test void guardReducesDamageAndConsumesEnergy() {
        var s = fresh(); step(s, RANGE, GUARD); steps(s, 85, NONE, GUARD);
        assertEquals(99, fighter(s, 1).health()); assertTrue(fighter(s, 1).energy() < 20);
    }
    @Test void emptyGuardBreaksAndCannotBeHeldForever() {
        var s = fresh(); steps(s, 250, NONE, GUARD); step(s, RANGE, GUARD); steps(s, 85, NONE, GUARD);
        assertEquals(93, fighter(s, 1).health());
    }
    @Test void insufficientEnergyRejectsSkills() {
        var s = fresh(); step(s, ULT, NONE); assertEquals(FightSnapshot.Action.IDLE, fighter(s, 0).action());
        step(s, RANGE, NONE); steps(s, 30, NONE, NONE); step(s, RANGE, NONE);
        assertEquals(FightSnapshot.Action.IDLE, fighter(s, 0).action());
    }
    @Test void fullEnergyUltimateCosts100AndDeals20() {
        var s = fresh(); steps(s, 710, NONE, NONE); assertEquals(100, fighter(s, 0).energy());
        step(s, ULT, NONE); assertEquals(0, fighter(s, 0).energy());
        steps(s, 20, NONE, NONE); assertTrue(s.snapshot().projectiles().isEmpty());assertEquals(FightSpecial.Phase.CHARGE,FightSpecial.phase(s.snapshot().ultimates().getFirst()));
        steps(s, 140, NONE, NONE); assertEquals(80, fighter(s, 1).health());
    }
    @Test void dashSpendsEnergyAndMovesFaster() {
        var s = fresh(); double x = fighter(s, 0).x();
        step(s, new FightCommand(1, false, false, false, false, false, true), NONE);
        steps(s, 9, NONE, NONE); assertEquals(135, fighter(s, 0).x() - x, 1e-6); assertTrue(fighter(s, 0).energy() < 21);
    }
    @Test void knockoutSettlesWithOneMatchEvent() {
        // KO 场景先走到接触范围；出生点反复打空现会轮换较长的摆拳，不能兼作固定追击速度假设。
        var s = fresh(); contact(s); int ends = 0;
        for (int i = 0; i < 3000 && !s.isFinished(); i++) {
            step(s, new FightCommand(fighter(s, 1).x() > fighter(s, 0).x() ? 1 : -1, false, i % 24 == 0, false, false, false, false), NONE);
            ends += (int) s.events().stream().filter(GameEvent.MatchEnded.class::isInstance).count();
        }
        assertTrue(s.isFinished()); assertEquals(FightSnapshot.Result.PLAYER_ONE, s.snapshot().result());
        assertEquals(0, fighter(s, 1).health()); assertEquals(1, ends);
    }
    @Test void timeoutComparesRemainingHealth() {
        var s = fresh(); step(s, RANGE, NONE); steps(s, FightSession.MATCH_TICKS, NONE, NONE);
        assertEquals(FightSnapshot.Result.PLAYER_ONE, s.snapshot().result());
        assertEquals(FightSnapshot.Action.WIN, fighter(s, 0).action());
        assertEquals(FightSnapshot.Action.LOSE, fighter(s, 1).action());
    }
    @Test void snapshotsAndEventsRemainImmutable() {
        var s = fresh(); var before = s.snapshot(); step(s, RANGE, NONE); steps(s, 20, NONE, NONE);
        assertEquals(0, before.tick()); assertTrue(before.projectiles().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> s.snapshot().fighters().clear());
        assertThrows(UnsupportedOperationException.class, () -> s.snapshot().projectiles().clear());
        assertThrows(UnsupportedOperationException.class, () -> s.snapshot().impacts().clear());
        assertThrows(UnsupportedOperationException.class, () -> s.events().clear());
    }
    @Test void botCannotBeOverriddenAndReallyFights() {
        var s = new FightSession(new FightSessionFactory.Config(FighterKind.NAIWA, FighterKind.FEIDUDU, FightSessionFactory.Mode.BOT), 73);
        s.start(); assertFalse(s.submitInput(new PlayerInput<>(1, 1, 0, NONE)));
        for (int i = 0; i < 900 && !s.isFinished(); i++) { s.tick(); }
        assertTrue(fighter(s, 0).health() < 100); assertTrue(fighter(s, 1).x() != 1680);
    }
    @TestFactory Stream<DynamicTest> allRolePairsAndModesReplayDeterministically() {
        var cases = new java.util.ArrayList<DynamicTest>();
        for (var a : FighterKind.values()) { for (var b : FighterKind.values()) { for (var mode : FightSessionFactory.Mode.values()) {
            cases.add(DynamicTest.dynamicTest(a + "/" + b + "/" + mode, () -> {
                var factory = new FightSessionFactory();
                var recording = new RecordingSession<>(factory, new FightSessionFactory.Config(a, b, mode), 928);
                recording.start();
                for (int t = 0; t < 1200 && !recording.isFinished(); t++) {
                    var v = recording.snapshot(); var self = v.fighters().get(0); var target = v.fighters().get(1);
                    var command = new FightCommand(target.x() > self.x() ? 1 : -1, t % 131 == 0, t % 25 == 0, t % 90 == 0, true, t % 120 > 105, false);
                    recording.submitInput(new PlayerInput<>(recording.tickNumber() + 1, 0, 0, command));
                    if (t == 7) { recording.pause(); recording.tick(); recording.resume(); }
                    recording.tick();
                }
                assertEquals(recording.tape().expected(), ReplayRunner.verify(factory, recording.tape()));
            }));
        } } }
        return cases.stream();
    }
}
