package dev.gamecore.client.fight;

import dev.gamecore.fight.FighterKind;
import dev.gamecore.fight.FightSnapshot.Action;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 直接读取打包 PNG 验证透明通道、十二个真实动作区域及人物面积，避免资源路径/空帧回归。 */
class FightSpriteTest {
    @Test void referenceSpritesHaveRealAlphaAndCompleteFrames() throws IOException {
        try (var stream = getClass().getResourceAsStream("/" + FightSpriteLayout.ASSET)) {
            assertNotNull(stream); var image = ImageIO.read(stream); assertNotNull(image);
            assertEquals(FightSpriteLayout.WIDTH, image.getWidth()); assertEquals(FightSpriteLayout.HEIGHT, image.getHeight());
            assertTrue(image.getColorModel().hasAlpha()); assertEquals(0, image.getRGB(0, 0) >>> 24);
            for (int index = 0; index < 12; index++) {
                var f = FightSpriteLayout.frame(index);
                assertTrue(f.x() >= 0 && f.y() >= 0 && f.x() + f.width() <= image.getWidth() && f.y() + f.height() <= image.getHeight());
                assertTrue(f.pivotX() > 0 && f.pivotX() < f.width());
                int opaque = 0;
                for (int y = f.y(); y < f.y() + f.height(); y++) { for (int x = f.x(); x < f.x() + f.width(); x++) {
                    if ((image.getRGB(x, y) >>> 24) > 128) { opaque++; }
                } }
                assertTrue(opaque > 30000, "Missing or clipped character in frame " + index);
            }
        }
    }
    @Test void bothCharactersHaveDistinctCombatPoses() {
        for (var kind : FighterKind.values()) {
            var indices = java.util.stream.Stream.of(Action.IDLE, Action.PUNCH, Action.GUARD, Action.RANGED, Action.ULTIMATE, Action.HURT)
                    .map(a -> FightSpriteLayout.index(kind, a)).distinct().toList();
            assertEquals(6, indices.size());
        }
    }
}
