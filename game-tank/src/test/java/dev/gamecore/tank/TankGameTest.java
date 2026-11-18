package dev.gamecore.tank;

import org.junit.jupiter.api.Test;
import java.util.List;
import static dev.gamecore.tank.TankFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class TankGameTest {
    private TankGame game() { return new TankGame(open(32, 10, new Vec2(2.5, 4.5), new Vec2(28.5, 4.5))); }
    @Test void readyWaitsAndSnapshotsAreImmutable() {
        TankGame game = game(); var before = game.snapshot(); game.tick();
        assertEquals(before, game.snapshot());
        assertThrows(UnsupportedOperationException.class, () -> before.tanks().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.bullets().clear());
        game.start(); game.command(0, new TankCommand(1, 0, false)); game.tick();
        assertEquals(new Vec2(2.5, 4.5), before.tanks().getFirst().position());
    }
    @Test void movementHasWorldUnitsAndRotationUsesLocalForward() {
        var game = game(); game.start(); game.command(0, new TankCommand(1, 0, false)); tick(game, 60);
        assertEquals(5.7, game.snapshot().tanks().getFirst().position().x(), 1e-10);
        assertEquals(TankGame.MOVE_SPEED, game.snapshot().tanks().getFirst().velocity().x(), 1e-10);
        game.command(0, new TankCommand(0, 1, false)); tick(game, 36);
        assertEquals(90, game.snapshot().tanks().getFirst().heading());
        var before = game.snapshot().tanks().getFirst().position();
        game.command(0, new TankCommand(1, 0, false)); tick(game, 60);
        assertEquals(before.y() + 3.2, game.snapshot().tanks().getFirst().position().y(), 1e-10);
        game.command(0, new TankCommand(-1, 0, false)); tick(game, 60);
        assertEquals(before.y(), game.snapshot().tanks().getFirst().position().y(), 1e-10);
    }
    @Test void tanksCannotCrossWallsOrMapBoundary() {
        boolean[] walls = new boolean[12 * 8]; for (int y = 0; y < 8; y++) { walls[y * 12 + 5] = true; }
        var game = new TankGame(map(12, 8, new Vec2(2.5, 3.5), new Vec2(9.5, 3.5), walls, List.of()));
        game.start(); game.command(0, new TankCommand(1, 0, false)); game.command(1, new TankCommand(-1, 0, false)); tick(game, 300);
        assertTrue(game.snapshot().tanks().get(0).position().x() <= 5 - TankGame.TANK_RADIUS);
        assertTrue(game.snapshot().tanks().get(1).position().x() <= 12 - TankGame.TANK_RADIUS);
        game.snapshot().tanks().forEach(t -> assertFalse(game.snapshot().map().blocked(t.position(), TankGame.TANK_RADIUS)));
    }
    @Test void simultaneousTankMovementDoesNotOverlapOrSwap() {
        var game = new TankGame(open(12, 8, new Vec2(2.5, 4.5), new Vec2(9.5, 4.5)));
        game.start(); game.command(0, new TankCommand(1, 0, false)); game.command(1, new TankCommand(1, 0, false)); tick(game, 200);
        var a = game.snapshot().tanks().get(0).position(); var b = game.snapshot().tanks().get(1).position();
        assertTrue(a.x() < b.x()); assertTrue(a.distanceSquared(b) >= 4 * TankGame.TANK_RADIUS * TankGame.TANK_RADIUS);
        assertEquals(12, a.x() + b.x(), 1e-10);
    }
    @Test void sustainedFireHonorsCooldownAndUniqueIds() {
        var game = game(); game.start(); game.command(0, new TankCommand(0, 0, true)); tick(game, 24);
        assertEquals(1, game.snapshot().bullets().size()); game.tick();
        assertEquals(List.of(0L, 1L), game.snapshot().bullets().stream().map(TankSnapshot.Bullet::id).toList());
        assertEquals(TankGame.FIRE_COOLDOWN, game.snapshot().tanks().getFirst().cooldown());
    }
    @Test void bulletDamagesOnceAndIgnoresItsOwner() {
        var game = new TankGame(open(12, 8, new Vec2(2.5, 4.5), new Vec2(5.5, 4.5)));
        game.start(); game.command(0, new TankCommand(0, 0, true)); game.tick(); game.command(0, TankCommand.IDLE); tick(game, 100);
        assertEquals(5, game.snapshot().tanks().get(0).health()); assertEquals(4, game.snapshot().tanks().get(1).health());
        assertTrue(game.snapshot().bullets().isEmpty());
    }
    @Test void wallBeforeTargetAbsorbsBulletIncludingCloseMuzzle() {
        boolean[] walls = new boolean[12 * 8]; walls[4 * 12 + 3] = true;
        var game = new TankGame(map(12, 8, new Vec2(2.65, 4.5), new Vec2(5.5, 4.5), walls, List.of()));
        game.start(); game.command(0, new TankCommand(0, 0, true)); game.tick(); game.command(0, TankCommand.IDLE); tick(game, 60);
        assertEquals(5, game.snapshot().tanks().get(1).health()); assertTrue(game.snapshot().bullets().isEmpty());
    }
    @Test void bulletsExpireAndDoNotAccumulate() {
        var game = new TankGame(open(100, 8, new Vec2(2.5, 2.5), new Vec2(90.5, 5.5)));
        game.start(); game.command(0, new TankCommand(0, 0, true)); game.tick(); game.command(0, TankCommand.IDLE);
        tick(game, TankGame.BULLET_LIFETIME - 2); assertEquals(1, game.snapshot().bullets().size());
        game.tick(); assertTrue(game.snapshot().bullets().isEmpty());
    }
    @Test void mutualDestructionIsDrawAndFinishedWorldIsFrozen() {
        var game = new TankGame(open(12, 8, new Vec2(2.5, 4.5), new Vec2(9.5, 4.5)));
        game.start(); game.command(0, new TankCommand(0, 0, true)); game.command(1, new TankCommand(0, 0, true)); tick(game, 300);
        assertEquals(TankGame.Result.DRAW, game.snapshot().result()); assertEquals(TankGame.State.FINISHED, game.snapshot().state());
        assertTrue(game.snapshot().tanks().stream().noneMatch(TankSnapshot.Tank::alive));
        var before = game.snapshot(); game.start(); tick(game, 100); assertEquals(before, game.snapshot());
    }
    @Test void survivingTankWins() {
        var game = new TankGame(open(12, 8, new Vec2(2.5, 4.5), new Vec2(9.5, 4.5)));
        game.start(); game.command(0, new TankCommand(0, 0, true)); tick(game, 300);
        assertEquals(TankGame.Result.PLAYER_ONE, game.snapshot().result()); assertEquals(0, game.snapshot().tanks().get(1).health());
        assertTrue(game.snapshot().events().stream().anyMatch(e -> e.type() == TankSnapshot.EventType.FINISHED));
    }
    @Test void timeoutComparesHealthAndEqualHealthDraws() {
        var draw = game(); draw.start(); tick(draw, TankGame.MATCH_TICKS);
        assertEquals(TankGame.Result.DRAW, draw.snapshot().result()); assertEquals(TankGame.MATCH_TICKS, draw.snapshot().tick());
        var game = new TankGame(open(12, 8, new Vec2(2.5, 4.5), new Vec2(9.5, 4.5)));
        game.start(); game.command(1, new TankCommand(0, 0, true)); game.tick(); game.command(1, TankCommand.IDLE); tick(game, TankGame.MATCH_TICKS);
        assertEquals(TankGame.Result.PLAYER_TWO, game.snapshot().result());
    }
    @Test void pauseClearsPendingCommandsAndDoesNotAdvance() {
        var game = game(); game.start(); game.command(0, new TankCommand(1, 1, true)); game.pause();
        var before = game.snapshot(); tick(game, 60); assertEquals(before, game.snapshot());
        game.start(); game.tick(); assertEquals(before.tanks().getFirst().position(), game.snapshot().tanks().getFirst().position());
        assertTrue(game.snapshot().bullets().isEmpty());
    }
    @Test void repairZoneRestoresOnlyAfterTwoSeconds() {
        var game = new TankGame(map(12, 8, new Vec2(2.5, 4.5), new Vec2(5.5, 4.5), new boolean[96],
                List.of(new TankMap.RepairZone(5, 4, 1, 1))));
        game.start(); game.command(0, new TankCommand(0, 0, true)); game.tick(); game.command(0, TankCommand.IDLE);
        while (game.snapshot().tanks().get(1).health() == 5) { game.tick(); }
        assertEquals(4, game.snapshot().tanks().get(1).health()); tick(game, 118);
        assertEquals(4, game.snapshot().tanks().get(1).health()); game.tick();
        assertEquals(5, game.snapshot().tanks().get(1).health());
        assertTrue(game.snapshot().events().stream().anyMatch(e -> e.type() == TankSnapshot.EventType.REPAIRED));
    }
    @Test void invalidStateAndInputsFailEarly() {
        assertThrows(IllegalArgumentException.class, () -> new TankCommand(2, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new TankCommand(Integer.MIN_VALUE, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new Vec2(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> TankMode.fromId("network"));
        assertThrows(IllegalArgumentException.class, () -> game().command(2, TankCommand.IDLE));
    }
}
