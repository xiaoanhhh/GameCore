package dev.gamecore.client.fight;

import dev.gamecore.fight.FightPunch;
import dev.gamecore.fight.FightSnapshot;
import dev.gamecore.fight.FighterKind;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 无 GL 校验真实拳击素材和规则/动画时序，避免只改动作名字而仍贴同一张拳图。 */
class FightBoxingVisualTest {
    @Test void boxingAtlasHasEightCompleteAlphaPosesPerOriginalCharacter() throws Exception {
        String json;
        try (var input = getClass().getResourceAsStream("/fighters/fighters-boxing-v1.json")) {
            assertNotNull(input); json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var clips = FightSpriteLayout.readBoxing(json); assertEquals(16, clips.size());
        for (var kind : FighterKind.values()) {
            assertEquals(8, clips.stream().filter(c -> c.kind() == kind).count());
            assertEquals(3, clips.stream().filter(c -> c.kind() == kind && (c.pose() == 2 || c.pose() == 4 || c.pose() == 6))
                    .map(c -> c.x() + ":" + c.y()).distinct().count());
        }
        try (var input = getClass().getResourceAsStream("/fighters/fighters-boxing-v1.png")) {
            assertNotNull(input); var image = javax.imageio.ImageIO.read(input); assertTrue(image.getColorModel().hasAlpha());
            for (var c : clips) {
                assertTrue(c.x() + c.width() <= image.getWidth()); assertTrue(c.y() + c.height() <= image.getHeight());
                int visible = 0;
                for (int y = c.y(); y < c.y() + c.height(); y++) { for (int x = c.x(); x < c.x() + c.width(); x++) {
                    if ((image.getRGB(x, y) >>> 24) > 140) { visible++; }
                } }
                assertTrue(visible > 12000, "Missing/empty boxing character");
            }
        }
        assertThrows(RuntimeException.class, () -> FightSpriteLayout.read(json));
        assertThrows(RuntimeException.class, () -> FightSpriteLayout.readBoxing(json.replaceFirst("\"pose\": 0", "\"pose\": 8")));
    }
    @Test void eachPunchHasDifferentWindupImpactAndRecoveryAlignedWithRealHitTick() {
        for (var punch : FightPunch.values()) {
            assertEquals(1 + 2 * punch.ordinal(), FightBoxingMotion.pose(punch.action(), 0));
            assertEquals(2 + 2 * punch.ordinal(), FightBoxingMotion.pose(punch.action(), punch.strikeTick()));
            assertEquals(7, FightBoxingMotion.pose(punch.action(), punch.strikeTick() + 4));
            assertEquals(0, FightBoxingMotion.pose(punch.action(), punch.duration() - 2));
            assertTrue(FightBoxingMotion.thrust(punch.action(), punch.strikeTick() - 1) < 0);
            assertEquals(1, FightBoxingMotion.thrust(punch.action(), punch.strikeTick()));
            assertEquals(0, FightBoxingMotion.thrust(punch.action(), punch.duration()));
        }
        assertEquals(-1, FightBoxingMotion.pose(FightSnapshot.Action.AIR_PUNCH, 3));
        assertEquals(-1, FightBoxingMotion.pose(FightSnapshot.Action.UPPERCUT, 3));
    }
}
