package dev.gamecore.snake;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 用精确网格场景验证规则边界，不依赖图形、睡眠或随机碰运气。 */
class SnakeGameTest {
    private static SnakeSnapshot.Player player(Direction direction, Cell... cells) {
        return new SnakeSnapshot.Player(List.of(cells), direction, 0, SnakeGame.Collision.NONE);
    }
    private static SnakeGame scene(SnakeMode mode, Cell food, Set<Cell> walls, SnakeSnapshot.Player... players) {
        return new SnakeGame(mode, 42, List.of(players), walls, food);
    }
    private static SnakeSnapshot.Player first(SnakeGame game) { return game.snapshot().players().getFirst(); }
    private static SnakeGame solo(Cell food) {
        return scene(SnakeMode.CLASSIC, food, Set.of(), player(Direction.RIGHT, new Cell(5, 5), new Cell(4, 5), new Cell(3, 5)));
    }

    @Test void readyDoesNotMoveUntilStarted() {
        SnakeGame game = new SnakeGame(SnakeMode.CLASSIC, 1);
        var ready = game.snapshot(); game.tick();
        assertEquals(ready, game.snapshot());
        assertFalse(game.turn(0, Direction.UP));
        game.start(); game.start(); game.tick();
        assertEquals(new Cell(6, 10), first(game).head());
        assertEquals(1, game.snapshot().tick());
        assertEquals(3, first(game).body().size());
    }
    @Test void foodGrowsAndScoresOnceThenRespawnsOnFreeCell() {
        SnakeGame game = solo(new Cell(6, 5)); game.start(); game.tick();
        assertEquals(4, first(game).body().size());
        assertEquals(10, first(game).score());
        assertFalse(first(game).body().contains(game.snapshot().food()));
        game.tick(); assertEquals(10, first(game).score());
    }
    @Test void reversalAndRepeatedDirectionAreRejected() {
        SnakeGame game = solo(new Cell(0, 0)); game.start();
        assertFalse(game.turn(0, Direction.LEFT));
        assertFalse(game.turn(0, Direction.RIGHT));
        game.tick(); assertEquals(new Cell(6, 5), first(game).head());
    }
    @Test void twoBufferedTurnsApplyAcrossTwoTicksAndQueueIsBounded() {
        SnakeGame game = solo(new Cell(0, 0)); game.start();
        assertTrue(game.turn(0, Direction.UP));
        assertTrue(game.turn(0, Direction.LEFT));
        assertFalse(game.turn(0, Direction.DOWN));
        game.tick(); assertEquals(new Cell(5, 6), first(game).head());
        game.tick(); assertEquals(new Cell(4, 6), first(game).head());
    }
    @Test void reverseRelativeToQueuedTurnIsRejected() {
        SnakeGame game = solo(new Cell(0, 0)); game.start();
        assertTrue(game.turn(0, Direction.UP)); assertFalse(game.turn(0, Direction.DOWN));
    }
    @Test void pauseFreezesStateClearsBufferedTurnsAndResumeDoesNotRestart() {
        SnakeGame game = solo(new Cell(0, 0)); game.start(); game.tick();
        game.turn(0, Direction.UP); game.pause(); var paused = game.snapshot();
        game.tick(); assertFalse(game.turn(0, Direction.LEFT)); assertEquals(paused, game.snapshot());
        game.start(); assertEquals(SnakeGame.State.PAUSED, game.snapshot().state());
        game.resume(); game.tick(); assertEquals(new Cell(7, 5), first(game).head());
    }
    @Test void eachBoundaryEndsSoloGame() {
        for (Direction direction : Direction.values()) {
            Cell head = switch (direction) {
                case UP -> new Cell(10, 19); case DOWN -> new Cell(10, 0);
                case LEFT -> new Cell(0, 10); case RIGHT -> new Cell(19, 10);
            };
            SnakeGame game = scene(SnakeMode.CLASSIC, new Cell(0, 0), Set.of(), player(direction, head));
            game.start(); game.tick();
            assertEquals(SnakeGame.Collision.WALL, first(game).collision());
            assertEquals(SnakeGame.Result.SOLO_LOSS, game.snapshot().result());
            var finished = game.snapshot(); game.tick(); game.start(); game.pause(); game.resume();
            assertEquals(finished, game.snapshot());
        }
    }
    @Test void obstacleCollisionEndsGame() {
        SnakeGame game = scene(SnakeMode.OBSTACLES, new Cell(0, 0), Set.of(new Cell(6, 5)),
                player(Direction.RIGHT, new Cell(5, 5), new Cell(4, 5)));
        game.start(); game.tick(); assertEquals(SnakeGame.Collision.OBSTACLE, first(game).collision());
    }
    @Test void selfCollisionUsesBodyAfterTailRelease() {
        SnakeGame game = scene(SnakeMode.CLASSIC, new Cell(0, 0), Set.of(), player(Direction.RIGHT,
                new Cell(5, 5), new Cell(5, 6), new Cell(6, 6), new Cell(6, 5), new Cell(7, 5)));
        game.start(); game.tick(); assertEquals(SnakeGame.Collision.SELF, first(game).collision());
    }
    @Test void enteringOwnVacatingTailIsLegal() {
        SnakeGame game = scene(SnakeMode.CLASSIC, new Cell(0, 0), Set.of(), player(Direction.RIGHT,
                new Cell(5, 5), new Cell(5, 6), new Cell(6, 6), new Cell(6, 5)));
        game.start(); game.tick(); assertTrue(first(game).alive());
        assertEquals(SnakeGame.State.RUNNING, game.snapshot().state());
    }
    @Test void headOnCollisionIsDrawRegardlessOfIterationOrder() {
        var a = player(Direction.RIGHT, new Cell(5, 5), new Cell(4, 5));
        var b = player(Direction.LEFT, new Cell(7, 5), new Cell(8, 5));
        for (var players : List.of(List.of(a, b), List.of(b, a))) {
            SnakeGame game = new SnakeGame(SnakeMode.DUO, 3, players, Set.of(), new Cell(6, 5));
            game.start(); game.tick();
            assertEquals(SnakeGame.Result.DRAW, game.snapshot().result());
            assertTrue(game.snapshot().players().stream().allMatch(p -> p.collision() == SnakeGame.Collision.HEAD_ON && p.score() == 0));
        }
    }
    @Test void swappingHeadsIsDrawEvenForLengthOne() {
        SnakeGame game = scene(SnakeMode.DUO, new Cell(0, 0), Set.of(),
                player(Direction.RIGHT, new Cell(5, 5)), player(Direction.LEFT, new Cell(6, 5)));
        game.start(); game.tick();
        assertEquals(SnakeGame.Result.DRAW, game.snapshot().result());
        assertTrue(game.snapshot().players().stream().allMatch(p -> p.collision() == SnakeGame.Collision.HEAD_SWAP));
    }
    @Test void singleDeathAwardsSurvivorWithNoFoodRequirement() {
        for (boolean firstDies : new boolean[]{true, false}) {
            var doomed = player(Direction.LEFT, new Cell(0, 5), new Cell(1, 5));
            var safe = player(Direction.UP, new Cell(10, 5), new Cell(10, 4));
            SnakeGame game = scene(SnakeMode.DUO, new Cell(0, 0), Set.of(), firstDies ? doomed : safe, firstDies ? safe : doomed);
            game.start(); game.tick();
            assertEquals(firstDies ? SnakeGame.Result.PLAYER_TWO : SnakeGame.Result.PLAYER_ONE, game.snapshot().result());
        }
    }
    @Test void independentSimultaneousDeathsAreDraw() {
        SnakeGame game = scene(SnakeMode.DUO, new Cell(0, 0), Set.of(),
                player(Direction.LEFT, new Cell(0, 5)), player(Direction.RIGHT, new Cell(19, 5)));
        game.start(); game.tick(); assertEquals(SnakeGame.Result.DRAW, game.snapshot().result());
    }
    @Test void opponentBodyKillsButVacatingOpponentTailIsLegal() {
        var opponent = player(Direction.UP, new Cell(6, 6), new Cell(6, 5), new Cell(6, 4));
        SnakeGame hit = scene(SnakeMode.DUO, new Cell(0, 0), Set.of(), player(Direction.RIGHT, new Cell(5, 5)), opponent);
        hit.start(); hit.tick(); assertEquals(SnakeGame.Collision.OTHER, first(hit).collision());
        SnakeGame tail = scene(SnakeMode.DUO, new Cell(0, 0), Set.of(), player(Direction.RIGHT, new Cell(5, 4)), opponent);
        tail.start(); tail.tick(); assertEquals(SnakeGame.State.RUNNING, tail.snapshot().state());
    }
    @Test void growingOpponentDoesNotVacateTail() {
        SnakeGame game = scene(SnakeMode.DUO, new Cell(6, 7), Set.of(), player(Direction.RIGHT, new Cell(5, 4)),
                player(Direction.UP, new Cell(6, 6), new Cell(6, 5), new Cell(6, 4)));
        game.start(); game.tick(); assertEquals(SnakeGame.Collision.OTHER, first(game).collision());
        assertEquals(10, game.snapshot().players().get(1).score());
    }
    @Test void fillingLastFreeCellWinsWithoutRandomRetryLoop() {
        List<Cell> path = new ArrayList<>();
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) { path.add(new Cell(y % 2 == 0 ? x : 19 - x, y)); }
        }
        Cell food = path.removeLast(); java.util.Collections.reverse(path);
        SnakeGame game = scene(SnakeMode.CLASSIC, food, Set.of(),
                new SnakeSnapshot.Player(path, Direction.LEFT, 3960, SnakeGame.Collision.NONE));
        game.start(); game.tick();
        assertEquals(SnakeGame.Result.SOLO_WIN, game.snapshot().result());
        assertEquals(400, first(game).body().size()); assertEquals(3970, first(game).score());
        assertNull(game.snapshot().food());
    }
    @Test void fullDuoBoardUsesScoresAndCanTie() {
        for (int score : new int[]{0, 10, 20}) {
            var a = player(Direction.RIGHT, new Cell(1, 1));
            var b = new SnakeSnapshot.Player(List.of(new Cell(5, 5), new Cell(5, 6), new Cell(6, 6), new Cell(6, 5)),
                    Direction.RIGHT, score, SnakeGame.Collision.NONE);
            Set<Cell> free = new HashSet<>(a.body()); free.addAll(b.body()); free.add(new Cell(2, 1));
            Set<Cell> walls = new HashSet<>();
            for (int x = 0; x < 20; x++) { for (int y = 0; y < 20; y++) { Cell c = new Cell(x, y); if (!free.contains(c)) { walls.add(c); } } }
            SnakeGame game = scene(SnakeMode.DUO, new Cell(2, 1), walls, a, b);
            game.start(); game.tick();
            assertEquals(score == 0 ? SnakeGame.Result.PLAYER_ONE : score == 10 ? SnakeGame.Result.DRAW : SnakeGame.Result.PLAYER_TWO,
                    game.snapshot().result());
        }
    }
    @Test void seedAndCommandsReproduceEverySnapshot() {
        for (SnakeMode mode : SnakeMode.values()) {
            SnakeGame a = new SnakeGame(mode, 987); SnakeGame b = new SnakeGame(mode, 987);
            a.start(); b.start();
            for (int i = 0; i < 120; i++) {
                Direction direction = Direction.values()[(i / 4) % 4];
                a.turn(0, direction); b.turn(0, direction);
                a.tick(); b.tick(); assertEquals(a.snapshot(), b.snapshot());
            }
        }
        SnakeGame a = solo(new Cell(6, 5)); SnakeGame b = solo(new Cell(6, 5));
        a.start(); b.start(); a.tick(); b.tick();
        assertEquals(10, first(a).score());
        Direction[] square = {Direction.UP, Direction.LEFT, Direction.DOWN, Direction.RIGHT};
        for (int i = 0; i < 120; i++) {
            a.turn(0, square[(i / 3) % 4]); b.turn(0, square[(i / 3) % 4]);
            a.tick(); b.tick(); assertEquals(a.snapshot(), b.snapshot());
        }
        assertEquals(121, a.snapshot().tick());
    }
    @Test void foodNeverSpawnsOnBodiesOrObstaclesAcrossSeedsAndModes() {
        for (SnakeMode mode : SnakeMode.values()) {
            for (int seed = 0; seed < 200; seed++) {
                var view = new SnakeGame(mode, seed).snapshot();
                assertFalse(view.obstacles().contains(view.food()));
                assertTrue(view.players().stream().noneMatch(p -> p.body().contains(view.food())));
                assertTrue(view.food().x() >= 0 && view.food().x() < 20 && view.food().y() >= 0 && view.food().y() < 20);
            }
        }
    }
    @Test void snapshotsCannotMutateGameAndOldSnapshotsStayStable() {
        SnakeGame game = new SnakeGame(SnakeMode.OBSTACLES, 1); var old = game.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> old.players().clear());
        assertThrows(UnsupportedOperationException.class, () -> old.players().getFirst().body().clear());
        assertThrows(UnsupportedOperationException.class, () -> old.obstacles().clear());
        game.start(); game.tick(); assertEquals(new Cell(5, 10), old.players().getFirst().head());
    }
    @Test void modeIdsValidateAndMapsHaveSafeInitialSpawns() {
        assertThrows(IllegalArgumentException.class, () -> SnakeMode.fromId("missing"));
        for (SnakeMode mode : SnakeMode.values()) {
            assertEquals(mode, SnakeMode.fromId(mode.id()));
            SnakeGame game = new SnakeGame(mode, 1);
            assertEquals(mode == SnakeMode.OBSTACLES ? 16 : 0, game.snapshot().obstacles().size());
            game.start(); game.tick(); assertEquals(SnakeGame.State.RUNNING, game.snapshot().state());
        }
    }
    @Test void invalidPlayerAndNullDirectionAreRejected() {
        SnakeGame game = solo(new Cell(0, 0)); game.start();
        assertThrows(IllegalArgumentException.class, () -> game.turn(1, Direction.UP));
        assertThrows(NullPointerException.class, () -> game.turn(0, null));
    }
}
