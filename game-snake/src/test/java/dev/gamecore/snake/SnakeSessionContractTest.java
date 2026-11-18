package dev.gamecore.snake;

import dev.gamecore.api.event.GameEvent;
import dev.gamecore.api.session.PlayerInput;
import dev.gamecore.api.session.SessionLoop;
import dev.gamecore.api.session.SessionState;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnakeSessionContractTest {
    @Test void orderedTurnsUseFirstTwoLegalDirectionsRegardlessOfArrivalOrder() {
        var game = new SnakeGameSession(SnakeMode.CLASSIC, 4); game.start();
        assertTrue(game.submitInput(new PlayerInput<>(1, 0, 2, Direction.LEFT)));
        assertTrue(game.submitInput(new PlayerInput<>(1, 0, 1, Direction.UP)));
        assertTrue(game.submitInput(new PlayerInput<>(1, 0, 3, Direction.DOWN)));
        game.tick(); game.tick(); assertEquals(Direction.UP, game.snapshot().players().getFirst().direction());
        game.tick(); game.tick(); assertEquals(Direction.LEFT, game.snapshot().players().getFirst().direction());
        game.tick(); game.tick(); assertEquals(Direction.LEFT, game.snapshot().players().getFirst().direction());
    }
    @Test void scoreDeathAndMatchEventsUseSimulationTickAndEmitOnce() {
        var player = new SnakeSnapshot.Player(List.of(new Cell(18, 5), new Cell(17, 5)), Direction.RIGHT, 0, SnakeGame.Collision.NONE);
        var game = new SnakeGameSession(new SnakeGame(SnakeMode.CLASSIC, 2, List.of(player), Set.of(), new Cell(19, 5)));
        var loop = new SessionLoop<>(game); List<GameEvent> seen = new ArrayList<>(); loop.events().subscribe(seen::add);
        game.start(); loop.tick(); loop.tick();
        assertEquals(new GameEvent.ScoreChanged(2, 0, 0, 10), seen.getFirst());
        loop.tick(); loop.tick(); assertTrue(game.isFinished());
        assertEquals(List.of(new GameEvent.ScoreChanged(2, 0, 0, 10), new GameEvent.PlayerDied(4, 0),
                new GameEvent.MatchEnded(4, "SOLO_LOSS")), seen);
        var frozen = game.snapshot(); loop.tick(); game.start(); game.resume();
        assertEquals(frozen, game.snapshot()); assertEquals(3, seen.size());
    }
    @Test void pauseCancelsQueuedInputsAndPhaseAndLifecycleDoesNotAutoResume() {
        var game = new SnakeGameSession(SnakeMode.CLASSIC, 9);
        game.resume(); game.pause(); assertEquals(SessionState.READY, game.state());
        assertFalse(game.submitInput(new PlayerInput<>(1, 0, 0, Direction.UP)));
        game.start(); game.tick(); game.submitInput(new PlayerInput<>(2, 0, 0, Direction.UP)); game.pause();
        game.start(); assertEquals(SessionState.PAUSED, game.state()); game.resume(); game.tick(); game.tick();
        assertEquals(new Cell(6, 10), game.snapshot().players().getFirst().head());
        assertEquals(3, game.snapshot().simulationTick()); assertEquals(0, game.snapshot().movementCredit());
    }
}
