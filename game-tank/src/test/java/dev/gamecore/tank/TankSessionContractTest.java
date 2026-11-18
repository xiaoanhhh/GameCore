package dev.gamecore.tank;

import dev.gamecore.api.event.GameEvent;
import dev.gamecore.api.session.PlayerInput;
import dev.gamecore.api.session.SessionLoop;
import dev.gamecore.api.session.SessionState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static dev.gamecore.tank.TankFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class TankSessionContractTest {
    private static TankSession duel() {
        return new TankSession(open(12, 8, new Vec2(2.5, 4.5), new Vec2(9.5, 4.5)), TankMode.DUEL, TankBot.Level.NORMAL, 1);
    }
    @Test void lastCommandBySequenceWinsAndHeldCommandPersistsUntilExplicitIdle() {
        var game = duel(); game.start();
        game.submitInput(new PlayerInput<>(1, 0, 4, new TankCommand(1, 0, false)));
        game.submitInput(new PlayerInput<>(1, 0, 2, new TankCommand(-1, 0, false)));
        assertFalse(game.submitInput(new PlayerInput<>(1, 1, 4, TankCommand.IDLE)));
        game.tick(); double x = game.snapshot().tanks().getFirst().position().x(); assertTrue(x > 2.5);
        game.tick(); assertTrue(game.snapshot().tanks().getFirst().position().x() > x);
        game.submitInput(new PlayerInput<>(3, 0, 0, TankCommand.IDLE)); game.tick();
        assertEquals(new Vec2(0, 0), game.snapshot().tanks().getFirst().velocity());
    }
    @Test void wrongTickPlayerAndBotSlotAreRejected() {
        var game = duel(); game.start();
        assertFalse(game.submitInput(new PlayerInput<>(2, 0, 0, TankCommand.IDLE)));
        assertThrows(IllegalArgumentException.class, () -> game.submitInput(new PlayerInput<>(1, 2, 0, TankCommand.IDLE)));
        var bot = new TankSession(game.snapshot().map(), TankMode.BOT, TankBot.Level.EASY, 5); bot.start();
        assertFalse(bot.submitInput(new PlayerInput<>(1, 1, 0, TankCommand.IDLE)));
    }
    @Test void pauseCancelsPendingAndHeldCommandsAndStartDoesNotResume() {
        var game = duel(); game.resume(); assertEquals(SessionState.READY, game.state()); game.start();
        game.submitInput(new PlayerInput<>(1, 0, 0, new TankCommand(1, 0, true))); game.pause();
        game.start(); assertEquals(SessionState.PAUSED, game.state()); var frozen = game.snapshot(); game.tick(); assertEquals(frozen, game.snapshot());
        game.resume(); game.tick(); assertTrue(game.snapshot().bullets().isEmpty());
        assertEquals(new Vec2(2.5, 4.5), game.snapshot().tanks().getFirst().position());
    }
    @Test void combatEventsAndTerminalStateArePublishedOnlyOnce() {
        var game = duel(); var loop = new SessionLoop<>(game); List<GameEvent> seen = new ArrayList<>(); loop.events().subscribe(seen::add);
        game.start(); game.command(0, new TankCommand(0, 0, true));
        for (int i = 0; i < 500 && !game.isFinished(); i++) { loop.tick(); }
        assertTrue(game.isFinished()); assertEquals(1, seen.stream().filter(GameEvent.MatchEnded.class::isInstance).count());
        assertEquals(1, seen.stream().filter(GameEvent.PlayerDied.class::isInstance).count());
        assertTrue(seen.stream().anyMatch(GameEvent.HealthChanged.class::isInstance));
        assertTrue(seen.stream().anyMatch(e -> e instanceof GameEvent.Effect effect && effect.kind() == GameEvent.Effect.Kind.SHOT));
        var frozen = game.snapshot(); int count = seen.size(); game.start(); game.resume(); loop.tick();
        assertEquals(frozen, game.snapshot()); assertEquals(count, seen.size());
    }
    @Test void independentlyConstructedMapsHaveContentEqualityIncludingWallsAndZones() {
        var a = open(12, 8, new Vec2(2.5, 4.5), new Vec2(9.5, 4.5));
        var b = open(12, 8, new Vec2(2.5, 4.5), new Vec2(9.5, 4.5));
        assertNotSame(a, b); assertEquals(a, b); assertEquals(a.hashCode(), b.hashCode());
        boolean[] walls = new boolean[96]; walls[0] = true;
        assertNotEquals(a, map(12, 8, new Vec2(2.5, 4.5), new Vec2(9.5, 4.5), walls, List.of()));
        assertNotEquals(a, map(12, 8, new Vec2(2.5, 4.5), new Vec2(9.5, 4.5), new boolean[96], List.of(new TankMap.RepairZone(1, 1, 1, 1))));
    }
}
