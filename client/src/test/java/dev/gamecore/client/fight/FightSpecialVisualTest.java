package dev.gamecore.client.fight;

import dev.gamecore.fight.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 无 GL 验证镜头、暗幕/茧阶段与真实命中元数据；不会把渲染成功当作伤害验证。 */
class FightSpecialVisualTest {
    private static FightSnapshot base(){return new FightSession(new FightSessionFactory.Config(FighterKind.NAIWA,FighterKind.FEIDUDU,FightSessionFactory.Mode.DUEL),2).snapshot();}
    private static FightSnapshot.Ultimate ult(int owner,FighterKind kind,int age,boolean bound){return new FightSnapshot.Ultimate(owner+1,owner,kind,age,owner==0?560:1680,0,owner==0?1680:560,0,bound,false,List.of());}
    private static FightSnapshot view(List<FightSnapshot.Ultimate> ults,List<FightSnapshot.Teleport> blinks){var b=base();return new FightSnapshot(1,b.state(),b.result(),b.fighters(),List.of(),List.of(),0,b.map(),ults,blinks);}
    @Test void bothCastersFocusDuringChargeAndReturnBeforeFirstBolt(){
        for(int player=0;player<2;player++){var sample=view(List.of(ult(player,player==0?FighterKind.NAIWA:FighterKind.FEIDUDU,18,false)),List.of());var focus=FightSpecialView.focus(sample);assertEquals(player,focus.player());assertEquals(2.35f,focus.zoom());
            var camera=new FightCamera();camera.viewport(0,0,1280,460);for(int i=0;i<36;i++){camera.special(sample,1f/60,player,0,focus.zoom());}assertTrue(camera.zoom()>2.2f);
            var released=view(List.of(ult(player,FighterKind.NAIWA,FightSpecial.CHARGE,false)),List.of());assertEquals(-1,FightSpecialView.focus(released).player());
            for(int i=0;i<FightSpecial.BOLT_FIRST-FightSpecial.CHARGE;i++){camera.special(released,1f/60,-1,0,1);}assertTrue(camera.zoom()<1.06f);assertTrue(camera.x(560)>=0&&camera.x(1680)<=1280);
        }
    }
    @Test void darknessRampsAndOnlyActualCaptureInflatesTheCocoon(){
        assertEquals(0,FightSpecialView.darkness(view(List.of(ult(0,FighterKind.NAIWA,0,false)),List.of())));assertEquals(1,FightSpecialView.darkness(view(List.of(ult(0,FighterKind.NAIWA,25,false)),List.of())));
        assertEquals(0,FightSpecialView.darkness(view(List.of(ult(0,FighterKind.FEIDUDU,25,false)),List.of())));
        assertEquals(0,FightSpecialView.cocoonScale(ult(1,FighterKind.FEIDUDU,85,false)));assertEquals(1,FightSpecialView.cocoonScale(ult(1,FighterKind.FEIDUDU,FightSpecial.WEB_CAPTURE,true)));
        assertTrue(FightSpecialView.cocoonScale(ult(1,FighterKind.FEIDUDU,FightSpecial.WEB_BURST-1,true))>1.8f);
    }
    @Test void blinkUsesMildFocusAndChargeHasPriority(){
        var blink=new FightSnapshot.Teleport(9,1,1680,0,468,0,1,0);var sample=view(List.of(),List.of(blink));assertEquals(1,FightSpecialView.focus(sample).player());assertEquals(1.2f,FightSpecialView.focus(sample).zoom());
        assertEquals(0,FightSpecialView.focus(view(List.of(ult(0,FighterKind.NAIWA,12,false)),List.of(blink))).player());
        assertEquals(-1,FightSpecialView.focus(view(List.of(),List.of(new FightSnapshot.Teleport(9,1,1680,0,468,0,1,20)))).player());
    }
    @Test void pauseDoesNotAdvanceTheSameSpecialCameraTransform(){
        var sample=view(List.of(ult(1,FighterKind.FEIDUDU,24,false)),List.of());var camera=new FightCamera();camera.viewport(0,0,960,430);camera.special(sample,.05f,1,0,2.35f);float x=camera.x(1680),y=camera.y(0),z=camera.zoom();camera.special(sample,0,1,0,2.35f);assertEquals(x,camera.x(1680));assertEquals(y,camera.y(0));assertEquals(z,camera.zoom());
    }
    @Test void actualImpactMetadataWinsOverAmbiguousOrChangedCharacterActions(){
        var b=base();var hit=new FightSnapshot.Impact(1,560,55,false,FighterKind.FEIDUDU,24,5,1,false,1,0,FightSnapshot.Attack.TELEPORT);
        var sample=new FightSnapshot(1,b.state(),b.result(),b.fighters(),List.of(),List.of(hit),5);var effects=new FightImpactFeedback();effects.ingest(sample);assertEquals(FightImpactFeedback.Style.TELEPORT,effects.hit(0).style());assertEquals(0,effects.hit(0).victim());assertTrue(effects.hurtFlash(0)>0);
    }
    @Test void allNewActionsHaveExistingOriginalCharacterArtAndFallbackFrames(){
        for(var kind:FighterKind.values()){for(var action:new FightSnapshot.Action[]{FightSnapshot.Action.TELEPORT,FightSnapshot.Action.WEB_BOUND,FightSnapshot.Action.ULTIMATE,FightSnapshot.Action.ULT_RELEASE}){assertTrue(FightSpriteLayout.index(kind,action)>=0);}}
    }
    @Test void teleportHudNamesActualPlayerComboAndShowsTheRuleCooldown(){
        var s=new FightSession(new FightSessionFactory.Config(FighterKind.NAIWA,FighterKind.FEIDUDU,FightSessionFactory.Mode.DUEL),2);assertEquals("",FightHudModel.blinkLabel(s.snapshot().fighters().get(0)));assertTrue(FightHudModel.blinkLabel(s.snapshot().fighters().get(1)).contains("↓+N2"));
        var f=s.snapshot().fighters().get(1);var cooldown=new FightSnapshot.Fighter(f.player(),f.kind(),f.x(),f.y(),f.facing(),f.health(),f.energy(),f.action(),0,0,0,0,0,true,0,-1,0,0,0,0,0,0,0,121,0);assertTrue(FightHudModel.blinkLabel(cooldown).contains("3s"));
    }
    @Test void shiftDropWorksFromAnAlreadyGuardedRoofForBothPlayersWithoutSpendingBlinkEnergy(){
        for(int player=0;player<2;player++){
            var s=new FightSession(new FightSessionFactory.Config(FighterKind.FEIDUDU,FighterKind.FEIDUDU,FightSessionFactory.Mode.DUEL),3);s.start();var controls=new FightControls();
            int down=player==0?com.badlogic.gdx.Input.Keys.S:com.badlogic.gdx.Input.Keys.DOWN,jump=player==0?com.badlogic.gdx.Input.Keys.K:com.badlogic.gdx.Input.Keys.NUMPAD_2;
            controls.down(jump);send(s,controls,player);controls.up(jump);for(int i=0;i<70;i++){send(s,controls,player);}assertTrue(s.snapshot().fighters().get(player).platform()>=0);
            controls.down(down);send(s,controls,player);assertEquals(FightSnapshot.Action.GUARD,s.snapshot().fighters().get(player).action());double energy=s.snapshot().fighters().get(player).energy(),height=s.snapshot().fighters().get(player).y();
            controls.down(com.badlogic.gdx.Input.Keys.SHIFT_LEFT);controls.down(jump);send(s,controls,player);var f=s.snapshot().fighters().get(player);
            assertTrue(f.y()<height&&f.vy()<0);assertTrue(s.snapshot().teleports().isEmpty());assertEquals(0,f.teleportCooldown());assertTrue(f.energy()>=energy);controls.clear();
        }
    }
    @Test void rightShiftAlsoRecordsAnExplicitDropAndClearRemovesTheModifier(){
        var controls=new FightControls();controls.down(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT);controls.down(com.badlogic.gdx.Input.Keys.DOWN);controls.down(com.badlogic.gdx.Input.Keys.UP);var drop=controls.command(1);
        assertTrue(drop.jump()&&drop.down());assertFalse(drop.guard());controls.clear();controls.down(com.badlogic.gdx.Input.Keys.DOWN);controls.command(1);controls.down(com.badlogic.gdx.Input.Keys.NUMPAD_2);assertTrue(controls.command(1).guard());
    }
    private static void send(FightSession s,FightControls controls,int player){s.submitInput(new dev.gamecore.api.session.PlayerInput<>(s.tickNumber()+1,player,0,controls.command(player)));s.tick();}
}
