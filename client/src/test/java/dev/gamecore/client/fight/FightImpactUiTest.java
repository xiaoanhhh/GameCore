package dev.gamecore.client.fight;

import com.badlogic.gdx.Input;
import dev.gamecore.api.session.PlayerInput;
import dev.gamecore.fight.*;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 无 GL 验证真实命中投影、去重/有界时钟、正确键位与能量边界；不靠截图代替数据与生命周期断言。 */
class FightImpactUiTest {
    private static FightSession game(){var s=new FightSession(new FightSessionFactory.Config(FighterKind.NAIWA,FighterKind.FEIDUDU,FightSessionFactory.Mode.DUEL),7);s.start();return s;}
    private static void step(FightSession s,FightCommand a,FightCommand b){s.submitInput(new PlayerInput<>(s.tickNumber()+1,0,0,a));s.submitInput(new PlayerInput<>(s.tickNumber()+1,1,1,b));s.tick();}
    private static FightCommand move(int n){return new FightCommand(n,false,false,false,false,false,false);}
    private static FightSnapshot sample(long id,boolean blocked,boolean ultimate,FighterKind kind){var v=game().snapshot();return new FightSnapshot(id,v.state(),v.result(),v.fighters(),List.of(),List.of(new FightSnapshot.Impact(id,1100,55,blocked,kind,24,blocked?1:ultimate?20:7,1,ultimate)),3,v.map());}
    @Test void actualThreePunchHitsHaveDistinctFeedbackAndIdentifyVictimWithoutChangingRules(){
        var s=game();for(int i=0;i<160&&s.snapshot().fighters().get(1).x()-s.snapshot().fighters().get(0).x()>94;i++){step(s,move(1),move(-1));}
        for(int i=0;i<5;i++){step(s,FightCommand.NONE,FightCommand.NONE);}
        var feedback=new FightImpactFeedback();int expectedHits=0;
        for(var style:new FightImpactFeedback.Style[]{FightImpactFeedback.Style.JAB,FightImpactFeedback.Style.CROSS,FightImpactFeedback.Style.HOOK}){
            step(s,new FightCommand(0,false,true,false,false,false,false),FightCommand.NONE);
            for(int t=0;t<18&&s.snapshot().fighters().get(0).hits()==expectedHits;t++){step(s,FightCommand.NONE,FightCommand.NONE);}
            expectedHits++;assertEquals(expectedHits,s.snapshot().fighters().get(0).hits());var before=s.snapshot();assertTrue(feedback.ingest(before));
            var hit=feedback.hit(expectedHits-1);assertEquals(style,hit.style());assertEquals(1,hit.victim());assertTrue(feedback.hurtFlash(1)>0);assertEquals(0,feedback.hurtFlash(0));
            feedback.advance(.02f);assertEquals(before,s.snapshot());
            for(int t=0;t<45&&s.snapshot().fighters().get(0).action()!=FightSnapshot.Action.IDLE;t++){step(s,FightCommand.NONE,FightCommand.NONE);}
        }
    }
    @Test void blockedUltimateHasShieldFeedbackAndLowerImpulseThanUnblockedUltimate(){
        var effects=new FightImpactFeedback();effects.ingest(sample(1,true,true,FighterKind.NAIWA));assertEquals(FightImpactFeedback.Style.BLOCK,effects.hit(0).style());float shield=effects.impulse();assertEquals(0,effects.hurtFlash(1));
        effects.ingest(sample(2,false,true,FighterKind.NAIWA));assertEquals(FightImpactFeedback.Style.ULTIMATE,effects.hit(1).style());assertTrue(effects.impulse()>shield);
        effects.ingest(sample(3,false,false,FighterKind.NAIWA));assertEquals(FightImpactFeedback.Style.LIGHTNING,effects.hit(2).style());
        effects.ingest(sample(4,false,false,FighterKind.FEIDUDU));assertEquals(FightImpactFeedback.Style.WEB,effects.hit(3).style());
    }
    @Test void duplicateSnapshotsDoNotRestartEffectsAndOverflowRemainsBounded(){
        var effects=new FightImpactFeedback();var first=sample(1,false,false,FighterKind.NAIWA);assertTrue(effects.ingest(first));effects.advance(.05f);float age=effects.hit(0).age();
        assertFalse(effects.ingest(first));assertEquals(age,effects.hit(0).age());assertEquals(0,effects.impulse());
        for(int i=2;i<90;i++){effects.ingest(sample(i,false,false,FighterKind.NAIWA));}assertEquals(FightImpactFeedback.CAPACITY,effects.count());
        for(int i=0;i<12;i++){effects.advance(.1f);}assertEquals(0,effects.count());
    }
    @Test void invalidDeltaAndFrozenSnapshotDoNotProgressAndRollbackClearsOldIds(){
        var effects=new FightImpactFeedback();effects.ingest(sample(8,false,true,FighterKind.NAIWA));effects.advance(Float.NaN);effects.advance(Float.POSITIVE_INFINITY);effects.advance(-1);effects.advance(0);assertEquals(0,effects.hit(0).age());
        effects.ingest(game().snapshot());assertEquals(0,effects.count());assertTrue(effects.ingest(sample(1,false,false,FighterKind.NAIWA)));effects.clear();assertEquals(0,effects.count());
    }
    @Test void effectsExpireAtSameWallTimeAcrossThirtySixtyAndHighRefreshRates(){
        for(int fps:new int[]{30,60,144}){var effects=new FightImpactFeedback();effects.ingest(sample(1,false,true,FighterKind.FEIDUDU));for(int i=0;i<fps/2;i++){effects.advance(1f/fps);}assertEquals(.5f,effects.hit(0).age(),.0001f);assertEquals(1,effects.count());for(int i=0;i<fps;i++){effects.advance(1f/fps);}assertEquals(0,effects.count());}
    }
    @Test void skillEnergyThresholdsUseRuleCostsAndIconsFollowActualCharacter(){
        assertFalse(FightHudModel.Skill.RANGED.ready(FightSession.RANGED_COST-.001));assertTrue(FightHudModel.Skill.RANGED.ready(FightSession.RANGED_COST));
        assertFalse(FightHudModel.Skill.ULTIMATE.ready(FightSession.ULTIMATE_COST-.001));assertTrue(FightHudModel.Skill.ULTIMATE.ready(FightSession.ULTIMATE_COST));assertFalse(FightHudModel.Skill.ULTIMATE.ready(Double.NaN));
        assertFalse(FightHudModel.Skill.GUARD.ready(FightSession.GUARD_COST-.001));assertTrue(FightHudModel.Skill.GUARD.ready(FightSession.GUARD_COST));assertEquals(0,FightHudModel.Skill.GUARD.cost());
        assertEquals(FightIcon.BOLT,FightHudModel.Skill.RANGED.icon(FighterKind.NAIWA));assertEquals(FightIcon.WEB,FightHudModel.Skill.RANGED.icon(FighterKind.FEIDUDU));
        assertTrue(FightHudModel.Skill.PUNCH.active(FightSnapshot.Action.PUNCH_3));assertFalse(FightHudModel.Skill.PUNCH.active(FightSnapshot.Action.HURT));assertTrue(FightHudModel.Skill.GUARD.active(FightSnapshot.Action.GUARD_HIT));
    }
    @Test void hudKeyLabelsMatchActualIndependentP1AndNumpadP2Inputs(){
        var controls=new FightControls();controls.down(Input.Keys.U);assertTrue(controls.command(0).ranged());assertFalse(controls.command(1).ranged());controls.clear();
        controls.down(Input.Keys.NUMPAD_4);assertTrue(controls.command(1).ranged());assertFalse(controls.command(0).ranged());
        assertEquals("U",FightHudModel.Skill.RANGED.key(0));assertEquals("N4",FightHudModel.Skill.RANGED.key(1));assertEquals("N1",FightHudModel.Skill.PUNCH.key(1));assertEquals("N5",FightHudModel.Skill.ULTIMATE.key(1));assertEquals("↓",FightHudModel.Skill.GUARD.key(1));assertThrows(IllegalArgumentException.class,()->FightHudModel.Skill.PUNCH.key(2));
    }
    @Test void healthTrailWaitsThenCatchesUpWhilePausedOrInvalidDeltaStaysFrozen(){
        var meter=new FightHudModel.Meter(1);meter.target(.8f);assertEquals(.8f,meter.value());assertEquals(1,meter.trail());
        meter.advance(.1f,true);meter.advance(Float.NaN,false);assertEquals(.24f,meter.delay());meter.advance(.1f,false);meter.advance(.1f,false);assertEquals(1,meter.trail());
        meter.advance(.1f,false);assertTrue(meter.trail()<1&&meter.trail()>.8f);for(int i=0;i<15;i++){meter.advance(.1f,false);}assertEquals(.8f,meter.trail());
    }
    @Test void newRoundResetsDamageTrailAndInvalidValuesCannotProduceBadMeterGeometry(){
        var meter=new FightHudModel.Meter(1);meter.target(.1f);meter.advance(.1f,false);meter.reset(1);assertEquals(1,meter.value());assertEquals(1,meter.trail());assertEquals(0,meter.delay());
        meter.target(9);assertEquals(1,meter.value());meter.target(Float.NaN);assertEquals(0,meter.value());meter.reset(-1);assertEquals(0,meter.trail());
    }
    @Test void originalIconPathsAreDistinctFiniteNormalizedAndNonempty(){
        var unique=new HashSet<String>();for(var icon:FightIcon.values()){var key=new StringBuilder();assertTrue(icon.paths()>0);
            for(int path=0;path<icon.paths();path++){assertTrue(icon.points(path)>=2);for(int p=0;p<icon.points(path);p++){float x=icon.x(path,p),y=icon.y(path,p);assertTrue(Float.isFinite(x)&&x>=0&&x<=1);assertTrue(Float.isFinite(y)&&y>=0&&y<=1);key.append(x).append(':').append(y).append('/');}}
            assertTrue(unique.add(key.toString()));}
    }
    @Test void movementDustFollowsActualCharacterAfterSwappingPlayerSides(){
        var s=new FightSession(new FightSessionFactory.Config(FighterKind.FEIDUDU,FighterKind.NAIWA,FightSessionFactory.Mode.DUEL),8);s.start();var effects=new FightVfx();
        for(int i=0;i<30;i++){step(s,move(1),FightCommand.NONE);effects.ingest(s.snapshot());effects.update(1f/60);}
        int found=0;for(int i=0;i<FightVfx.CAPACITY;i++){if(effects.life[i]>0&&effects.type[i]==FightVfx.DUST){found++;assertEquals(1,effects.element[i]);}}assertTrue(found>0);
    }
}
