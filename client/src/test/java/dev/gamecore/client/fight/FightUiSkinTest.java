package dev.gamecore.client.fight;

import com.badlogic.gdx.graphics.Color;
import dev.gamecore.fight.FightMap;
import dev.gamecore.client.ui.ThemeManager;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 不依赖 GL 的主题覆盖与对比度检查；几何、动态数字及点击区域另由真实窗口验收。 */
class FightUiSkinTest {
    @Test void everyPlayableMapHasItsOwnMaterialAndTwoIdentifiableSides(){
        var materials=new HashSet<String>();var palettes=new HashSet<String>();
        for(var map:FightMap.values()){var skin=FightUiSkin.of(map);assertEquals(map.name(),skin.name());
            assertTrue(materials.add(skin.material));assertTrue(palettes.add(skin.primary+":"+skin.secondary));
            assertNotEquals(skin.side(0),skin.side(1));assertFalse(skin.subtitle.isBlank());}
    }
    @Test void allMapTextAccentsRemainReadableAgainstTheirOwnPanel(){
        for(var skin:FightUiSkin.values()){assertTrue(contrast(ThemeManager.TEXT,skin.panel)>=7,skin.name());
            assertTrue(contrast(ThemeManager.MUTED,skin.panel)>=4.5,skin.name());
            assertTrue(contrast(skin.primary,skin.panel)>=4.5,skin.name());assertTrue(contrast(skin.secondary,skin.panel)>=4.5,skin.name());}
    }
    private static double channel(float v){return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}
    private static double luminance(Color c){return .2126*channel(c.r)+.7152*channel(c.g)+.0722*channel(c.b);}
    private static double contrast(Color a,Color b){double aa=luminance(a),bb=luminance(b);return (Math.max(aa,bb)+.05)/(Math.min(aa,bb)+.05);}
}
