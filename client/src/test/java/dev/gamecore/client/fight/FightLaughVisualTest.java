package dev.gamecore.client.fight;

import com.badlogic.gdx.Input;
import dev.gamecore.fight.*;
import dev.gamecore.api.session.PlayerInput;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

/** 专用按键路由、真实动作驱动的镜头/帧序列和透明图集，不创建 GL 上下文。 */
class FightLaughVisualTest {
    private static FightSession game(FighterKind one,FighterKind two){var s=new FightSession(new FightSessionFactory.Config(one,two,FightSessionFactory.Mode.DUEL),73);s.start();return s;}
    @Test void allThreeAliasesTargetTheOnlyHumanNaiwaOnEitherSide(){
        for(int key:new int[]{Input.Keys.Y,Input.Keys.NUM_9,Input.Keys.NUMPAD_9}){for(int player=0;player<2;player++){
            var input=new FightLaughInput();var view=game(player==0?FighterKind.NAIWA:FighterKind.FEIDUDU,player==1?FighterKind.NAIWA:FighterKind.FEIDUDU).snapshot();
            input.down(key,view,FightSessionFactory.Mode.DUEL);assertTrue(input.consume(player));assertFalse(input.consume(1-player));assertFalse(input.consume(player));}}
    }
    @Test void twoNaiwasUseSeparateKeysAndAnEnemyBotCannotBeForcedToLaugh(){
        var input=new FightLaughInput();var both=game(FighterKind.NAIWA,FighterKind.NAIWA).snapshot();input.down(Input.Keys.Y,both,FightSessionFactory.Mode.DUEL);assertTrue(input.consume(0));assertFalse(input.consume(1));input.down(Input.Keys.NUMPAD_9,both,FightSessionFactory.Mode.DUEL);assertTrue(input.consume(1));input.clear();
        var enemy=game(FighterKind.FEIDUDU,FighterKind.NAIWA).snapshot();input.down(Input.Keys.Y,enemy,FightSessionFactory.Mode.BOT);assertFalse(input.consume(0));assertFalse(input.consume(1));}
    @Test void shortTapSurvivesOneTickButHoldRepeatAndClearDoNotQueueLaughter(){
        var input=new FightLaughInput();var view=game(FighterKind.NAIWA,FighterKind.FEIDUDU).snapshot();input.down(Input.Keys.Y,view,FightSessionFactory.Mode.DUEL);input.up(Input.Keys.Y);assertTrue(input.consume(0));assertFalse(input.consume(0));
        input.down(Input.Keys.Y,view,FightSessionFactory.Mode.DUEL);assertTrue(input.consume(0));input.down(Input.Keys.Y,view,FightSessionFactory.Mode.DUEL);assertFalse(input.consume(0));input.clear();assertFalse(input.consume(0));assertFalse(FightLaughInput.accepts(Input.Keys.I));assertThrows(IllegalArgumentException.class,()->input.consume(2));}
    @Test void laughFramesCoverEightDistinctPosesAndAreBounded(){
        var seen=new HashSet<Integer>();for(int i=0;i<FightSession.LAUGH_TICKS;i++){seen.add(FightLaughMotion.pose(i));assertTrue(Math.abs(FightLaughMotion.angle(i))<=2.5);assertTrue(FightLaughMotion.squash(i)>=.98&&FightLaughMotion.squash(i)<=1.02);}
        assertEquals(8,seen.size());assertEquals(0,FightLaughMotion.pose(-1));assertEquals(7,FightLaughMotion.pose(Integer.MAX_VALUE));}
    @Test void focusedCameraFollowsRealLaughAndReturnsAfterMovementCancelsIt(){
        var s=game(FighterKind.NAIWA,FighterKind.FEIDUDU);s.submitInput(new PlayerInput<>(1,0,0,FightCommand.NONE.withLaugh(true)));s.tick();assertEquals(0,FightLaughMotion.focusedPlayer(s.snapshot()));
        var camera=new FightCamera();camera.viewport(0,0,1280,480);for(int i=0;i<90;i++){camera.update(s.snapshot(),1f/60,0);}
        assertTrue(camera.zoom()>3);float cx=camera.x(s.snapshot().fighters().get(0).x());assertTrue(cx>150&&cx<850);
        s.submitInput(new PlayerInput<>(2,0,0,new FightCommand(1,false,false,false,false,false,false)));s.tick();assertEquals(-1,FightLaughMotion.focusedPlayer(s.snapshot()));for(int i=0;i<150;i++){camera.update(s.snapshot(),1f/60,-1);}assertTrue(camera.zoom()<1.01);}
    @Test void actualLaughPngAndEightNaiwaMetadataRegionsAreValidAndStrict() throws Exception {
        String json;try(var in=getClass().getResourceAsStream("/fighters/fighters-laugh-v1.json")){assertNotNull(in);json=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
        var clips=FightSpriteLayout.readLaugh(json);assertEquals(8,clips.size());
        try(var in=getClass().getResourceAsStream("/fighters/fighters-laugh-v1.png")){assertNotNull(in);var im=javax.imageio.ImageIO.read(in);assertTrue(im.getColorModel().hasAlpha());assertTrue(im.getWidth()>1500&&im.getHeight()>800);
            for(var c:clips){assertEquals(FighterKind.NAIWA,c.kind());assertTrue(c.x()+c.width()<=im.getWidth()&&c.y()+c.height()<=im.getHeight());assertTrue(c.width()>250&&c.height()>250);}}
        assertThrows(RuntimeException.class,()->FightSpriteLayout.readLaugh(json.replaceFirst("NAIWA","FEIDUDU")));assertThrows(RuntimeException.class,()->FightSpriteLayout.readVictory(json));assertThrows(RuntimeException.class,()->FightSpriteLayout.readLaugh(json.replaceFirst("\"pose\": 0","\"pose\": 8")));
    }
}
