package dev.gamecore.snake;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 在纯 Java 会话验证恒定模拟频率、渐进速度和完整时间相位可复现性。 */
class SnakeGameSessionTest {
    private static SnakeSnapshot.Player player(int score) {
        return new SnakeSnapshot.Player(List.of(new Cell(5, 5), new Cell(4, 5), new Cell(3, 5)),
                Direction.RIGHT, score, SnakeGame.Collision.NONE);
    }
    private static SnakeGameSession session(int score, Cell food) {
        return new SnakeGameSession(new SnakeGame(SnakeMode.CLASSIC, 42, List.of(player(score)), Set.of(), food));
    }
    @Test void simulationIsTwentyHertzAndInitialMovementIsTenPerSecond() {
        var game = session(0, new Cell(0, 0));
        game.tick(); assertEquals(0, game.timingSnapshot().simulationTick());
        game.start(); game.tick(); assertEquals(new Cell(5, 5), game.snapshot().players().getFirst().head());
        assertEquals(1, game.timingSnapshot().simulationTick());
        game.tick(); assertEquals(new Cell(6, 5), game.snapshot().players().getFirst().head());
        assertEquals(1, game.snapshot().tick());
        for (int i = 0; i < 18; i++) { game.tick(); }
        assertEquals(10, game.snapshot().tick()); assertEquals(20, game.timingSnapshot().simulationTick());
    }
    @Test void speedThresholdsAndCapAreExplicitAndDuoUsesSharedFood() {
        int[] scores = {0, 20, 30, 60, 90, 120, 150, 3970};
        int[] rates = {10, 10, 12, 14, 16, 18, 20, 20};
        for (int i = 0; i < scores.length; i++) {
            assertEquals(rates[i], ScoreSystem.movementRate(List.of(player(scores[i]))));
        }
        assertEquals(12, ScoreSystem.movementRate(List.of(player(10), player(20))));
    }
    @Test void eatingThirdFoodChangesSpeedOnNextSimulationStepWithoutChangingFrequency() {
        var game = session(20, new Cell(6, 5)); game.start(); game.tick(); game.tick();
        assertEquals(30, game.snapshot().players().getFirst().score()); assertEquals(12, game.movementRate());
        game.tick(); assertEquals(1, game.snapshot().tick());
        assertEquals(12, game.timingSnapshot().movementCredit());
        game.tick(); assertEquals(2, game.snapshot().tick()); assertEquals(4, game.timingSnapshot().movementCredit());
    }
    @Test void everySpeedMovesExactRateAcrossTwentyFixedSteps() {
        for (int score : new int[]{0, 30, 60, 90, 120, 150}) {
            var game = session(score, new Cell(0, 0)); game.start();
            // 在边长五格的环路上行进，避免边界干扰速度断言。
            Direction[] ring = {Direction.UP, Direction.LEFT, Direction.DOWN, Direction.RIGHT};
            long previousMove = -1;
            for (int i = 0; i < 20; i++) {
                long move = game.snapshot().tick();
                if (move != previousMove) { game.turn(0, ring[(int) (move / 5) % 4]); previousMove = move; }
                game.tick();
            }
            assertEquals(game.movementRate(), game.snapshot().tick());
            assertEquals(20, game.timingSnapshot().simulationTick());
        }
    }
    @Test void pauseDiscardsPartialMovementAndBufferedTurn() {
        var game = session(0, new Cell(0, 0)); game.start(); game.tick(); game.turn(0, Direction.UP);
        game.pause(); var paused = game.timingSnapshot(); game.tick(); assertEquals(paused, game.timingSnapshot());
        assertEquals(0, paused.movementCredit());
        game.resume(); game.tick(); assertEquals(0, game.snapshot().tick());
        game.tick(); assertEquals(new Cell(6, 5), game.snapshot().players().getFirst().head());
    }
    @Test void sameSeedAndTickIndexedInputsReproduceTimingAndWorldThroughAcceleration() {
        var a = session(20, new Cell(6, 5)); var b = session(20, new Cell(6, 5));
        a.start(); b.start(); a.tick(); b.tick(); a.tick(); b.tick();
        assertEquals(12, a.movementRate());
        Direction[] square = {Direction.UP, Direction.LEFT, Direction.DOWN, Direction.RIGHT};
        long previousMove = -1;
        for (int tick = 0; tick < 200; tick++) {
            long move = a.snapshot().tick();
            if (move != previousMove) {
                Direction direction = square[(int) (move / 3) % 4];
                a.turn(0, direction); b.turn(0, direction); previousMove = move;
            }
            if (tick == 61) { a.pause(); b.pause(); }
            if (tick == 65) { a.resume(); b.resume(); }
            a.tick(); b.tick(); assertEquals(a.timingSnapshot(), b.timingSnapshot());
        }
        assertEquals(SnakeGame.State.RUNNING, a.snapshot().state());
        assertTrue(a.timingSnapshot().simulationTick() > 190);
    }
    @Test void duoStillMovesTogetherAndStopsAfterSimultaneousDeath() {
        var game = new SnakeGameSession(SnakeMode.DUO, 1); game.start();
        for (int i = 0; i < 10; i++) { game.tick(); }
        assertEquals(SnakeGame.Result.DRAW, game.snapshot().result());
        var finished = game.timingSnapshot(); game.tick(); assertEquals(finished, game.timingSnapshot());
    }
}
