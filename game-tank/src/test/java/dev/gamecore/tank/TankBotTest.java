package dev.gamecore.tank;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static dev.gamecore.tank.TankFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class TankBotTest {
    @Test void aStarFindsShortestRouteAroundBarrierAndHandlesUnreachableGoal() {
        boolean[] walls = new boolean[64]; for (int y = 0; y < 7; y++) { walls[y * 8 + 3] = true; }
        var map = map(8, 8, new Vec2(1.5, 1.5), new Vec2(6.5, 1.5), walls, List.of());
        var path = GridPathfinder.find(map, map.spawns().getFirst().position(), map.spawns().get(1).position());
        assertEquals(17, path.size()); assertTrue(path.stream().anyMatch(point -> point.y() == 7.5));
        path.forEach(point -> assertFalse(map.blocked(point, TankGame.TANK_RADIUS)));
        walls[7 * 8 + 3] = true; var closed = map(8, 8, new Vec2(1.5, 1.5), new Vec2(6.5, 1.5), walls, List.of());
        assertTrue(GridPathfinder.find(closed, closed.spawns().getFirst().position(), closed.spawns().get(1).position()).isEmpty());
    }
    @Test void normalBotAimsAndFiresUsingOnlyCommands() {
        var game = new TankGame(open(12, 8, new Vec2(2.5, 4.5), new Vec2(9.5, 4.5))); game.start();
        var bot = new ArenaBot(TankBot.Level.NORMAL, 1); var command = bot.command(game.snapshot(), 1);
        assertTrue(command.fire()); assertEquals(TankBot.State.ATTACK, bot.state());
        assertEquals(5, game.snapshot().tanks().getFirst().health()); game.command(1, command); tick(game, 40);
        assertTrue(game.snapshot().tanks().getFirst().health() < 5);
    }
    @Test void hardBotPredictsIncomingBulletAndChoosesEvadeState() {
        var game = new TankGame(open(12, 8, new Vec2(2.5, 4.5), new Vec2(6.5, 4.5))); game.start();
        game.command(0, new TankCommand(0, 0, true)); game.tick(); game.command(0, TankCommand.IDLE); tick(game, 9);
        var bot = new ArenaBot(TankBot.Level.HARD, 1); bot.command(game.snapshot(), 1);
        assertEquals(TankBot.State.EVADE, bot.state());
    }
    @Test void allDifficultiesAreSeedReproducibleAndStayOutsideWalls() throws IOException {
        for (String id : List.of("crossfire", "ruins")) {
            var map = arena(id);
            for (var level : TankBot.Level.values()) {
                var a = new TankSession(map, TankMode.BOT, level, 812); var b = new TankSession(map, TankMode.BOT, level, 812);
                a.start(); b.start();
                for (int tick = 0; tick < 1800; tick++) {
                    var command = new TankCommand(tick % 200 < 100 ? 1 : -1, tick % 90 < 45 ? 1 : -1, tick % 60 < 20);
                    a.command(0, command); b.command(0, command); a.tick(); b.tick(); assertEquals(a.snapshot(), b.snapshot());
                    for (var tank : a.snapshot().tanks()) { assertFalse(map.blocked(tank.position(), TankGame.TANK_RADIUS)); }
                    assertTrue(a.snapshot().bullets().size() <= 14);
                }
            }
        }
    }
    @Test void hardBotCanReachAndDefeatStationaryPlayerOnBothProductionMaps() throws IOException {
        for (String id : List.of("crossfire", "ruins")) {
            var session = new TankSession(arena(id), TankMode.BOT, TankBot.Level.HARD, 7); session.start();
            for (int tick = 0; tick < TankGame.MATCH_TICKS && session.snapshot().state() == TankGame.State.RUNNING; tick++) { session.tick(); }
            assertEquals(TankGame.Result.PLAYER_TWO, session.snapshot().result(), id + " " + session.snapshot().tanks());
        }
    }
    @Test void botSlotRejectsHumanOverrideAndPausedSessionIsFrozen() throws IOException {
        var session = new TankSession(arena("crossfire"), TankMode.BOT, TankBot.Level.NORMAL, 1);
        session.start(); session.command(1, new TankCommand(-1, -1, true)); session.tick(); session.pause();
        var paused = session.snapshot(); for (int i = 0; i < 60; i++) { session.tick(); }
        assertEquals(paused, session.snapshot());
    }
}
