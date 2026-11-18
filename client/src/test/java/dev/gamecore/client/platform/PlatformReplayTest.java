package dev.gamecore.client.platform;

import dev.gamecore.api.event.GameEvent;
import dev.gamecore.api.replay.RecordingSession;
import dev.gamecore.api.replay.ReplayOperation;
import dev.gamecore.api.replay.ReplayRunner;
import dev.gamecore.api.replay.ReplayTape;
import dev.gamecore.api.replay.SessionFactory;
import dev.gamecore.api.session.PlayerInput;
import dev.gamecore.api.session.SessionLoop;
import dev.gamecore.api.session.SessionState;
import dev.gamecore.client.effects.EffectWorld;
import dev.gamecore.snake.Direction;
import dev.gamecore.snake.Cell;
import dev.gamecore.snake.SnakeMode;
import dev.gamecore.snake.SnakeSessionFactory;
import dev.gamecore.snake.SnakeSnapshot;
import dev.gamecore.tank.TankBot;
import dev.gamecore.tank.TankCommand;
import dev.gamecore.tank.TankMap;
import dev.gamecore.tank.TankMode;
import dev.gamecore.tank.TankSessionFactory;
import dev.gamecore.tank.TankSnapshot;
import dev.gamecore.tank.TmxArenaLoader;
import dev.gamecore.tank.Vec2;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

/** 同一个泛型验收方法运行两款真实游戏，不能以两份相似但互不相干的回放测试冒充公共框架。 */
class PlatformReplayTest {
    @TestFactory Stream<DynamicTest> bothGamesUseOneLifecycleRecordingAndReplayHarness() {
        List<DynamicTest> cases = new ArrayList<>();
        for (var mode : SnakeMode.values()) {
            for (long seed : new long[]{1, 42}) {
                cases.add(DynamicTest.dynamicTest("snake/" + mode + "/" + seed, () ->
                        exercise(new SnakeSessionFactory(), new SnakeSessionFactory.Config(mode), seed, 260, (tick, view) -> {
                            Direction direction = Direction.values()[(int) (tick / 6) % Direction.values().length];
                            return List.of(new PlayerInput<>(tick, 0, 1, direction));
                        })));
            }
        }
        for (String map : List.of("crossfire", "ruins")) {
            for (var mode : TankMode.values()) {
                for (var level : TankBot.Level.values()) {
                    cases.add(DynamicTest.dynamicTest("tank/" + map + "/" + mode + "/" + level, () ->
                            exercise(new TankSessionFactory(), new TankSessionFactory.Config(arena(map), mode, level), 812, 1200,
                                    (tick, view) -> {
                                        var first = new PlayerInput<>(tick, 0, 4, new TankCommand(tick % 160 < 80 ? 1 : -1,
                                                tick % 100 < 50 ? 1 : -1, tick % 60 < 20));
                                        return mode == TankMode.BOT ? List.of(first) : List.of(first,
                                                new PlayerInput<>(tick, 1, 2, new TankCommand(0, 1, true)));
                                    })));
                }
            }
        }
        return cases.stream();
    }
    private static <C, I, S> void exercise(SessionFactory<C, I, S> factory, C config, long seed, int steps,
                                          BiFunction<Long, S, List<PlayerInput<I>>> inputs) {
        var recording = new RecordingSession<>(factory, config, seed);
        var loop = new SessionLoop<>(recording); List<GameEvent> observed = new ArrayList<>(); loop.events().subscribe(observed::add);
        loop.tick(); assertEquals(0, recording.tickNumber()); recording.resume(); assertEquals(SessionState.READY, recording.state());
        recording.start();
        for (int step = 0; step < steps && !recording.isFinished(); step++) {
            long target = recording.tickNumber() + 1;
            for (var input : inputs.apply(target, recording.snapshot())) { assertTrue(recording.submitInput(input)); }
            if (step == 7) {
                recording.pause(); var frozen = recording.snapshot(); int eventCount = observed.size();
                assertTrue(loop.tick().isEmpty()); assertEquals(frozen, recording.snapshot()); assertEquals(eventCount, observed.size());
                recording.start(); assertEquals(SessionState.PAUSED, recording.state()); recording.resume();
            }
            assertTrue(loop.tick().isEmpty());
        }
        var tape = recording.tape();
        assertEquals(observed, tape.expected().events());
        assertEquals(tape.expected(), ReplayRunner.verify(factory, tape));
        assertThrows(UnsupportedOperationException.class, () -> tape.operations().clear());
        assertThrows(UnsupportedOperationException.class, () -> tape.expected().events().clear());
        assertTrue(tape.operations().stream().anyMatch(op -> op instanceof ReplayOperation.Control<I> control
                && control.action() == ReplayOperation.Action.PAUSE));
    }
    private static TankMap arena(String id) throws IOException {
        try (var stream = PlatformReplayTest.class.getResourceAsStream("/maps/tank/" + id + ".tmx")) {
            if (stream == null) { throw new IOException("Missing arena " + id); }
            return TmxArenaLoader.load(stream);
        }
    }
    private static ReplayTape<SnakeSessionFactory.Config, Direction, SnakeSnapshot> finishedSnake() {
        var recording = new RecordingSession<>(new SnakeSessionFactory(), new SnakeSessionFactory.Config(SnakeMode.DUO), 19);
        var loop = new SessionLoop<>(recording); recording.start();
        for (int i = 0; i < 40; i++) { loop.tick(); }
        assertTrue(recording.isFinished()); return recording.tape();
    }
    @Test void finishedReplayReproducesDeathAndMatchEvents() {
        var tape = finishedSnake(); assertEquals(10, tape.expected().tick());
        assertEquals(2, tape.expected().events().stream().filter(GameEvent.PlayerDied.class::isInstance).count());
        assertEquals(1, tape.expected().events().stream().filter(GameEvent.MatchEnded.class::isInstance).count());
        assertEquals(tape.expected(), ReplayRunner.verify(new SnakeSessionFactory(), tape));
    }
    @Test void wrongRulesVersionIsRejectedBeforeSimulation() {
        var tape = finishedSnake(); var wrong = new ReplayTape<>(new SessionFactory.Rules("snake", "other", 20), tape.configuration(),
                tape.seed(), tape.initial(), tape.operations(), tape.expected());
        assertThrows(IllegalArgumentException.class, () -> ReplayRunner.verify(new SnakeSessionFactory(), wrong));
    }
    @Test void changedInitialSeedAndConfigurationAreRejected() {
        var tape = finishedSnake(); long changedSeed = tape.seed() + 1;
        while (new SnakeSessionFactory().create(tape.configuration(), changedSeed).snapshot().equals(tape.initial())) { changedSeed++; }
        var wrong = new ReplayTape<>(tape.rules(), tape.configuration(), changedSeed, tape.initial(), tape.operations(), tape.expected());
        assertThrows(IllegalArgumentException.class, () -> ReplayRunner.verify(new SnakeSessionFactory(), wrong));
        var wrongMode = new ReplayTape<>(tape.rules(), new SnakeSessionFactory.Config(SnakeMode.CLASSIC), tape.seed(), tape.initial(), tape.operations(), tape.expected());
        assertThrows(IllegalArgumentException.class, () -> ReplayRunner.verify(new SnakeSessionFactory(), wrongMode));
    }
    @Test void wrongTickAndRejectedInputAreRejected() {
        var tape = finishedSnake(); var wrongTick = new ArrayList<>(tape.operations()); wrongTick.addFirst(new ReplayOperation.Advance<>(99));
        var a = new ReplayTape<>(tape.rules(), tape.configuration(), tape.seed(), tape.initial(), wrongTick, tape.expected());
        assertThrows(IllegalArgumentException.class, () -> ReplayRunner.verify(new SnakeSessionFactory(), a));
        var rejected = new ArrayList<>(tape.operations());
        rejected.add(1, new ReplayOperation.Input<>(0, new PlayerInput<>(2, 0, 0, Direction.UP)));
        var b = new ReplayTape<>(tape.rules(), tape.configuration(), tape.seed(), tape.initial(), rejected, tape.expected());
        assertThrows(IllegalArgumentException.class, () -> ReplayRunner.verify(new SnakeSessionFactory(), b));
    }
    @Test void changedFinalTickOrEventHistoryCannotPassVerification() {
        var tape = finishedSnake(); var state = tape.expected();
        var wrongFinal = new ReplayTape.Result<>(state.tick() + 1, state.state(), state.snapshot(), state.events());
        var a = new ReplayTape<>(tape.rules(), tape.configuration(), tape.seed(), tape.initial(), tape.operations(), wrongFinal);
        assertThrows(IllegalStateException.class, () -> ReplayRunner.verify(new SnakeSessionFactory(), a));
        var wrongEvents = new ReplayTape.Result<>(state.tick(), state.state(), state.snapshot(), List.<GameEvent>of());
        var b = new ReplayTape<>(tape.rules(), tape.configuration(), tape.seed(), tape.initial(), tape.operations(), wrongEvents);
        assertThrows(IllegalStateException.class, () -> ReplayRunner.verify(new SnakeSessionFactory(), b));
    }
    @Test void snakeMovementPhaseParticipatesInFinalVerification() {
        var factory = new SnakeSessionFactory(); var recording = new RecordingSession<>(factory, new SnakeSessionFactory.Config(SnakeMode.CLASSIC), 1);
        recording.start(); recording.tick(); var tape = recording.tape(); var view = tape.expected().snapshot();
        assertEquals(10, view.movementCredit()); assertEquals(1, view.simulationTick());
        var wrongView = new SnakeSnapshot(view.mode(), view.state(), view.result(), view.tick(), view.players(), view.obstacles(), view.food(), view.simulationTick(), 0);
        var wrong = new ReplayTape<>(tape.rules(), tape.configuration(), tape.seed(), tape.initial(), tape.operations(),
                new ReplayTape.Result<>(1, SessionState.RUNNING, wrongView, tape.expected().events()));
        assertThrows(IllegalStateException.class, () -> ReplayRunner.verify(factory, wrong));
    }
    @Test void tankMapContentDriftIsDetectedEvenWhenIdIsUnchanged() {
        var first = map(false); var changed = map(true); var factory = new TankSessionFactory();
        var recording = new RecordingSession<>(factory, new TankSessionFactory.Config(first, TankMode.DUEL, TankBot.Level.EASY), 1);
        var tape = recording.tape();
        var wrong = new ReplayTape<>(tape.rules(), new TankSessionFactory.Config(changed, TankMode.DUEL, TankBot.Level.EASY), tape.seed(), tape.initial(), tape.operations(), tape.expected());
        assertThrows(IllegalArgumentException.class, () -> ReplayRunner.verify(factory, wrong));
    }
    private static TankMap map(boolean wall) {
        boolean[] walls = new boolean[96]; walls[0] = wall;
        return new TankMap("same-id", 12, 8, walls, List.of(new TankMap.Spawn(new Vec2(2.5, 4.5), 0), new TankMap.Spawn(new Vec2(9.5, 4.5), 180)), List.of());
    }
    @Test void snakeReplayReproducesFoodRespawnAndAccelerationWithRealSeededStart() {
        var factory = new SnakeSessionFactory(); var recording = new RecordingSession<>(factory, new SnakeSessionFactory.Config(SnakeMode.CLASSIC), 42);
        var loop = new SessionLoop<>(recording); recording.start();
        for (int i = 0; i < 1500 && recording.snapshot().players().getFirst().score() < 30 && !recording.isFinished(); i++) {
            var view = recording.snapshot(); var player = view.players().getFirst();
            var queue = new java.util.ArrayDeque<Cell>(); var seen = new java.util.HashSet<Cell>();
            var first = new java.util.HashMap<Cell, Direction>(); var head = player.head(); queue.add(head); seen.add(head);
            while (!queue.isEmpty() && !seen.contains(view.food())) {
                var current = queue.removeFirst();
                for (var direction : Direction.values()) {
                    if (current.equals(head) && player.direction().opposite(direction)) { continue; }
                    var next = current.move(direction);
                    if (next.x() < 0 || next.y() < 0 || next.x() >= 20 || next.y() >= 20
                            || player.body().contains(next) || !seen.add(next)) { continue; }
                    first.put(next, current.equals(head) ? direction : first.get(current)); queue.addLast(next);
                }
            }
            var direction = first.get(view.food()); assertNotNull(direction, "No path to food");
            recording.submitInput(new PlayerInput<>(recording.tickNumber() + 1, 0, 0, direction)); loop.tick();
        }
        assertFalse(recording.isFinished()); assertEquals(30, recording.snapshot().players().getFirst().score());
        assertEquals(3, recording.tape().expected().events().stream().filter(GameEvent.ScoreChanged.class::isInstance).count());
        assertEquals(recording.tape().expected(), ReplayRunner.verify(factory, recording.tape()));
    }
    @Test void tankTimeoutReplayReproducesMatchEndAndFinishedFreeze() {
        var factory = new TankSessionFactory(); var recording = new RecordingSession<>(factory,
                new TankSessionFactory.Config(map(false), TankMode.DUEL, TankBot.Level.EASY), 3);
        var loop = new SessionLoop<>(recording); recording.start();
        for (int i = 0; i < dev.gamecore.tank.TankGame.MATCH_TICKS + 3; i++) { loop.tick(); }
        assertTrue(recording.isFinished()); assertEquals(dev.gamecore.tank.TankGame.MATCH_TICKS, recording.tickNumber());
        assertEquals(List.of(new GameEvent.MatchEnded(recording.tickNumber(), "DRAW")), recording.tape().expected().events());
        assertEquals(recording.tape().expected(), ReplayRunner.verify(factory, recording.tape()));
    }
    @Test void changingAppliedInputPayloadFailsFinalStateValidation() {
        var factory = new SnakeSessionFactory(); var recording = new RecordingSession<>(factory, new SnakeSessionFactory.Config(SnakeMode.CLASSIC), 7);
        recording.start(); recording.submitInput(new PlayerInput<>(1, 0, 0, Direction.UP)); recording.tick(); recording.tick();
        var tape = recording.tape(); var changed = new ArrayList<>(tape.operations());
        changed.set(1, new ReplayOperation.Input<>(0, new PlayerInput<>(1, 0, 0, Direction.DOWN)));
        var wrong = new ReplayTape<>(tape.rules(), tape.configuration(), tape.seed(), tape.initial(), changed, tape.expected());
        assertThrows(IllegalStateException.class, () -> ReplayRunner.verify(factory, wrong));
    }

    @Test void ecsObserversAndRenderRateDoNotChangeSimulationOrReplay() {
        var factory = new TankSessionFactory(); var config = new TankSessionFactory.Config(map(false), TankMode.DUEL, TankBot.Level.EASY);
        var plain = new RecordingSession<>(factory, config, 9); var decorated = new RecordingSession<>(factory, config, 9);
        var a = new SessionLoop<>(plain); var b = new SessionLoop<>(decorated); var effects = new EffectWorld();
        b.events().subscribe(effects::accept); b.events().subscribe(event -> { throw new IllegalStateException("unavailable sound"); });
        plain.start(); decorated.start(); boolean particlesSeen = false, failureSeen = false;
        for (int i = 0; i < 240 && !plain.isFinished(); i++) {
            var input = new PlayerInput<>(plain.tickNumber() + 1, 0, 0, new TankCommand(0, 0, true));
            plain.submitInput(input); decorated.submitInput(input); a.tick(); failureSeen |= !b.tick().isEmpty();
            particlesSeen |= effects.size() > 0; effects.update(i % 2 == 0 ? 1.0 / 30 : 1.0 / 144);
            assertEquals(plain.snapshot(), decorated.snapshot());
        }
        assertTrue(particlesSeen); assertTrue(failureSeen); assertEquals(plain.tape(), decorated.tape());
        assertEquals(decorated.tape().expected(), ReplayRunner.verify(factory, decorated.tape()));
    }
}
