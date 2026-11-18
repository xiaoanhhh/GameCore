package dev.gamecore.fight;

import dev.gamecore.api.session.PlayerInput;
import dev.gamecore.api.session.SessionState;
import dev.gamecore.api.replay.RecordingSession;
import dev.gamecore.api.replay.ReplayRunner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 奶蛙娱乐动作真实输入验证：有限时长、可打断/受击，无伤害/无消耗，不依靠渲染伪造规则状态。 */
class FightLaughTest {
    private static final FightCommand LAUGH=FightCommand.NONE.withLaugh(true);
    private static FightSession game(FighterKind one,FighterKind two){var s=new FightSession(new FightSessionFactory.Config(one,two,FightSessionFactory.Mode.DUEL),73);s.start();return s;}
    private static void step(FightSession s,FightCommand one,FightCommand two){s.submitInput(new PlayerInput<>(s.tickNumber()+1,0,0,one));s.submitInput(new PlayerInput<>(s.tickNumber()+1,1,1,two));s.tick();}
    private static void waitFor(FightSession s,int count){for(int i=0;i<count;i++){step(s,FightCommand.NONE,FightCommand.NONE);}}
    @Test void legacyInputsDefaultToNoLaughAndWithLaughPreservesEveryOtherField(){
        var c=new FightCommand(1,true,true,false,false,true,true,true);assertFalse(c.laugh());assertTrue(c.withLaugh(true).laugh());assertEquals(c,c.withLaugh(true).withLaugh(false));assertFalse(new FightCommand(0,false,false,false,false,false,false).laugh());assertEquals("7",new FightSessionFactory().rules().version());}
    @Test void finiteLaughHasNoDamageAttackOrEnergyCostWhileTheMatchKeepsRunning(){
        var a=game(FighterKind.NAIWA,FighterKind.FEIDUDU);var baseline=game(FighterKind.NAIWA,FighterKind.FEIDUDU);step(a,LAUGH,FightCommand.NONE);step(baseline,FightCommand.NONE,FightCommand.NONE);
        assertEquals(FightSnapshot.Action.LAUGH,a.snapshot().fighters().get(0).action());assertEquals(168,a.snapshot().fighters().get(0).actionTicks());
        waitFor(a,170);waitFor(baseline,170);var f=a.snapshot().fighters().get(0);assertEquals(FightSnapshot.Action.IDLE,f.action());assertEquals(100,f.health());assertEquals(0,f.attacks());assertEquals(0,f.totalDamage());assertEquals(baseline.snapshot().fighters().get(0).energy(),f.energy());assertEquals(171,a.tickNumber());assertEquals(SessionState.RUNNING,a.state());}
    @Test void onlyGroundedFreeNaiwaCanLaughAndBusyRequestsAreNotDeferred(){
        var s=game(FighterKind.FEIDUDU,FighterKind.NAIWA);step(s,LAUGH,LAUGH);assertNotEquals(FightSnapshot.Action.LAUGH,s.snapshot().fighters().get(0).action());assertEquals(FightSnapshot.Action.LAUGH,s.snapshot().fighters().get(1).action());
        var a=game(FighterKind.NAIWA,FighterKind.FEIDUDU);step(a,new FightCommand(0,true,false,false,false,false,false),FightCommand.NONE);step(a,LAUGH,FightCommand.NONE);assertNotEquals(FightSnapshot.Action.LAUGH,a.snapshot().fighters().get(0).action());waitFor(a,100);assertNotEquals(FightSnapshot.Action.LAUGH,a.snapshot().fighters().get(0).action());
        var b=game(FighterKind.NAIWA,FighterKind.FEIDUDU);step(b,new FightCommand(0,false,true,false,false,false,false),FightCommand.NONE);step(b,LAUGH,FightCommand.NONE);waitFor(b,50);assertNotEquals(FightSnapshot.Action.LAUGH,b.snapshot().fighters().get(0).action());}
    @Test void movingAttackingJumpingAndGuardingCancelLaughImmediately(){
        for(var command:new FightCommand[]{new FightCommand(1,false,false,false,false,false,false),new FightCommand(0,false,true,false,false,false,false),new FightCommand(0,true,false,false,false,false,false),new FightCommand(0,false,false,false,false,true,false)}){
            var s=game(FighterKind.NAIWA,FighterKind.FEIDUDU);step(s,LAUGH,FightCommand.NONE);waitFor(s,20);step(s,command,FightCommand.NONE);assertNotEquals(FightSnapshot.Action.LAUGH,s.snapshot().fighters().get(0).action());}}
    @Test void incomingProjectileHurtsAndInterruptsLaughWithoutInvulnerability(){
        var s=game(FighterKind.NAIWA,FighterKind.FEIDUDU);step(s,LAUGH,new FightCommand(0,false,false,true,false,false,false));
        for(int i=0;i<120&&s.snapshot().fighters().get(0).health()==100;i++){step(s,FightCommand.NONE,FightCommand.NONE);}
        var f=s.snapshot().fighters().get(0);assertEquals(94,f.health());assertNotEquals(FightSnapshot.Action.LAUGH,f.action());assertTrue(f.stunTicks()>0);}
    @Test void pausingFreezesLaughAndResumeContinuesTheSamePoseTick(){
        var s=game(FighterKind.NAIWA,FighterKind.FEIDUDU);step(s,LAUGH,FightCommand.NONE);waitFor(s,25);s.pause();var frozen=s.snapshot();for(int i=0;i<50;i++){s.tick();}assertEquals(frozen,s.snapshot());s.resume();s.tick();assertEquals(frozen.fighters().get(0).poseTick()+1,s.snapshot().fighters().get(0).poseTick());}
    @Test void laughterReplaysDeterministicallyOnEveryMapAndMode(){
        var factory=new FightSessionFactory();for(var mode:FightSessionFactory.Mode.values()){for(var map:FightMap.values()){
            var config=new FightSessionFactory.Config(FighterKind.NAIWA,FighterKind.FEIDUDU,mode,map);var r=new RecordingSession<>(factory,config,73);r.start();
            for(int i=0;i<240;i++){r.submitInput(new PlayerInput<>(r.tickNumber()+1,0,0,i%100==0?LAUGH:FightCommand.NONE));r.tick();}
            assertEquals(r.tape().expected(),ReplayRunner.verify(factory,r.tape()));}}
    }
    @Test void repeatedRequestsDuringLaughDoNotResetItsAgeOrQueueAnotherLaugh(){
        var s=game(FighterKind.NAIWA,FighterKind.FEIDUDU);step(s,LAUGH,FightCommand.NONE);for(int i=0;i<160;i++){step(s,LAUGH,FightCommand.NONE);}assertEquals(160,s.snapshot().fighters().get(0).poseTick());waitFor(s,20);assertEquals(FightSnapshot.Action.IDLE,s.snapshot().fighters().get(0).action());}
}
