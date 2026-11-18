package dev.gamecore.client.fight;

import dev.gamecore.fight.FightMap;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** 实际多地图背景/主题绑定及胜者动作透明区域，避免 UI 选图只改名字而没有对应素材。 */
class FightMapVictoryAssetsTest {
    @Test void allMapsHaveDifferentRealBackgroundsAndThemeColors() throws Exception {
        var paths=new java.util.HashSet<String>();var palettes=new java.util.HashSet<Integer>();
        for(var map:FightMap.values()){var style=FightMapStyle.of(map);paths.add(style.background);palettes.add(style.primary.toIntBits());
            try(var in=getClass().getResourceAsStream("/"+style.background)){assertNotNull(in);var image=javax.imageio.ImageIO.read(in);assertTrue(image.getWidth()>=1280&&image.getHeight()>600);}}
        assertEquals(FightMap.values().length,paths.size());assertEquals(FightMap.values().length,palettes.size());
    }
    @Test void allSixteenVictoryPosesHaveValidAlphaRegionsAndStrictMetadata() throws Exception {
        String json;try(var in=getClass().getResourceAsStream("/fighters/fighters-victory-v1.json")){assertNotNull(in);json=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
        var clips=FightSpriteLayout.readVictory(json);assertEquals(16,clips.size());
        try(var in=getClass().getResourceAsStream("/fighters/fighters-victory-v1.png")){assertNotNull(in);var im=javax.imageio.ImageIO.read(in);assertTrue(im.getColorModel().hasAlpha());for(var c:clips){assertTrue(c.x()+c.width()<=im.getWidth()&&c.y()+c.height()<=im.getHeight());assertTrue(c.width()>180&&c.height()>180);}}
        assertThrows(RuntimeException.class,()->FightSpriteLayout.readGreetings(json));
        assertThrows(RuntimeException.class,()->FightSpriteLayout.readVictory(json.replaceFirst("\"pose\": 0","\"pose\": 8")));
    }
}
