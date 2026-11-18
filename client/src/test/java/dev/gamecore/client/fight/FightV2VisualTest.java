package dev.gamecore.client.fight;

import dev.gamecore.fight.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 无 GL 验证图集元数据、粒子上限/去重、视觉演进与规则隔离。 */
class FightV2VisualTest {
    @Test void allFortyEightMeasuredFramesExistAndAreUnique() throws Exception {
        String json;
        try(var input=getClass().getResourceAsStream("/fighters/fighters-v3.json")){assertNotNull(input);json=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        var clips=FightSpriteLayout.read(json);assertEquals(48,clips.size());
        for(var kind:FighterKind.values()){assertEquals(24,clips.stream().filter(c->c.kind()==kind).count());}
        for(var c:clips){try(var input=getClass().getResourceAsStream("/"+c.sheet())){assertNotNull(input);var image=javax.imageio.ImageIO.read(input);assertTrue(image.getColorModel().hasAlpha());assertTrue(c.x()+c.width()<=image.getWidth());assertTrue(c.y()+c.height()<=image.getHeight());}}
        for(var action:FightSnapshot.Action.values()){for(var kind:FighterKind.values()){assertTrue(FightSpriteLayout.index(kind,action)>=0);}}
        assertThrows(RuntimeException.class,()->FightSpriteLayout.read("{}"));
        assertThrows(RuntimeException.class,()->FightSpriteLayout.read(json.replaceFirst("\"pose\": 0", "\"pose\": 8")));
    }
    @Test void hitBurstIsLayeredDeduplicatedBoundedAndExpires() {
        var s=new FightSession(new FightSessionFactory.Config(FighterKind.NAIWA,FighterKind.FEIDUDU,FightSessionFactory.Mode.DUEL),1);
        var v=s.snapshot();var hit=new FightSnapshot.Impact(1,100,100,false,FighterKind.NAIWA,24,5,1,false);
        var sample=new FightSnapshot(1,v.state(),v.result(),v.fighters(),List.of(),List.of(hit),3);
        var effects=new FightVfx();assertTrue(effects.ingest(sample));assertEquals(27,effects.count());assertFalse(effects.ingest(sample));assertEquals(27,effects.count());
        for(int i=2;i<80;i++){effects.ingest(new FightSnapshot(i,v.state(),v.result(),v.fighters(),List.of(),List.of(new FightSnapshot.Impact(i,100,100,false,FighterKind.FEIDUDU,24,6,1,false)),3));}
        assertEquals(FightVfx.CAPACITY,effects.count());for(int i=0;i<20;i++){effects.update(.1f);}assertEquals(0,effects.count());assertEquals(v,s.snapshot());effects.clear();assertEquals(0,effects.count());
    }
    @Test void cameraMapsAllObjectsThroughOneFiniteTransformAndCanFreeze() {
        var s=new FightSession(new FightSessionFactory.Config(FighterKind.NAIWA,FighterKind.FEIDUDU,FightSessionFactory.Mode.DUEL),1);
        var camera=new FightCamera();camera.viewport(0,0,1280,500);camera.impact(.8f);camera.update(s.snapshot(),.016f);
        float x=camera.x(560),y=camera.y(0);camera.update(s.snapshot(),0);assertEquals(x,camera.x(560));assertEquals(y,camera.y(0));
        assertTrue(Float.isFinite(x)&&Float.isFinite(y));assertTrue(camera.x(1680)>x);assertTrue(camera.y(120)>y);
    }
}
