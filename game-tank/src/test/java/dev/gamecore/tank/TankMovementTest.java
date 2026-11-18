package dev.gamecore.tank;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 抽取移动核后同时验证规则调用、地图阻挡与双车接触，避免只测能编译。 */
class TankMovementTest {
    @Test void sharedKernelMatchesActualGameAcrossDirectionChangesWallsAndContacts() {
        boolean[] walls = new boolean[96]; walls[4 * 12 + 6] = true;
        var map = new TankMap("movement", 12, 8, walls, List.of(new TankMap.Spawn(new Vec2(2.5, 4.5), 0), new TankMap.Spawn(new Vec2(9.5, 4.5), 180)), List.of());
        var game = new TankGame(map); game.start();
        for (int tick = 0; tick < 600; tick++) {
            var a = new TankCommand(tick % 100 < 70 ? 1 : -1, tick % 120 < 60 ? 0 : 1, false);
            var b = new TankCommand(tick % 80 < 50 ? 1 : 0, tick % 160 < 80 ? 0 : -1, false);
            var before = game.snapshot();
            var expected = TankMovement.step(map, before.tanks().stream().map(TankMovement.Motion::of).toList(), List.of(a, b));
            game.command(0, a); game.command(1, b); game.tick();
            assertEquals(expected, game.snapshot().tanks().stream().map(TankMovement.Motion::of).toList());
            assertTrue(expected.stream().noneMatch(motion -> map.blocked(motion.position(), TankGame.TANK_RADIUS)));
            assertTrue(expected.get(0).position().distanceSquared(expected.get(1).position()) >= 4 * TankGame.TANK_RADIUS * TankGame.TANK_RADIUS);
        }
    }
    @Test void sharedAimRespectsTurnSpeedAndWrapsShortestDirection() {
        assertEquals(1, TankMovement.command(1, 0, false, 5.0, 355).turn());
        assertEquals(-1, TankMovement.command(0, 0, false, 355.0, 5).turn());
        assertEquals(0, TankMovement.command(0, 0, false, 5.0, 5).turn());
    }
}
