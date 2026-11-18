package dev.gamecore.fight;

import dev.gamecore.api.replay.RecordingSession;
import dev.gamecore.api.replay.ReplayRunner;
import dev.gamecore.api.session.PlayerInput;
import dev.gamecore.api.session.SessionState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 特殊技真实输入验收：时序、躲避/格挡、束缚/爆裂、瞬移/击倒、边界、暂停与确定性回放。 */
class FightSpecialTest {
    private static final FightCommand N=FightCommand.NONE,U=new FightCommand(0,false,false,false,true,false,false),
            G=new FightCommand(0,false,false,false,false,true,false),GJ=new FightCommand(0,true,false,false,false,true,false),
            P=new FightCommand(0,false,true,false,false,false,false),R=new FightCommand(0,false,false,true,false,false,false);
    private static FightCommand move(int d){return new FightCommand(d,false,false,false,false,false,false);}
    private static FightSession game(FighterKind one,FighterKind two){var s=new FightSession(new FightSessionFactory.Config(one,two,FightSessionFactory.Mode.DUEL),91);s.start();return s;}
    private static FightSnapshot.Fighter f(FightSession s,int p){return s.snapshot().fighters().get(p);}
    private static void step(FightSession s,FightCommand a,FightCommand b){s.submitInput(new PlayerInput<>(s.tickNumber()+1,0,0,a));s.submitInput(new PlayerInput<>(s.tickNumber()+1,1,1,b));s.tick();}
    private static void steps(FightSession s,int n,FightCommand a,FightCommand b){for(int i=0;i<n;i++){step(s,a,b);}}
    private static void charge(FightSession s){steps(s,710,N,N);step(s,U,N);assertEquals(0,f(s,0).energy());}
    private static void age(FightSession s,int age){for(int i=0;i<500&&s.snapshot().ultimates().getFirst().age()<age;i++){step(s,N,N);}assertEquals(age,s.snapshot().ultimates().getFirst().age());}
    @Test void lightningHasFourTelegraphedStrikesAndExactlyTwentyRealDamage(){
        var s=game(FighterKind.NAIWA,FighterKind.FEIDUDU);charge(s);assertTrue(s.snapshot().projectiles().isEmpty());int damage=0;long last=0;
        for(int i=0;i<210;i++){step(s,N,N);for(var hit:s.snapshot().impacts()){if(hit.id()>last){last=hit.id();damage+=hit.damage();assertEquals(FightSnapshot.Attack.ULTIMATE,hit.attack());assertEquals(1,hit.defender());}}
            if(!s.snapshot().ultimates().isEmpty()){for(var bolt:s.snapshot().ultimates().getFirst().strikes()){if(bolt.age()==-1){assertTrue(bolt.aimed());assertFalse(bolt.hit());}}}}
        assertEquals(20,damage);assertEquals(80,f(s,1).health());assertEquals(4,f(s,0).attacks());assertTrue(s.snapshot().ultimates().isEmpty());
    }
    @Test void dashCanEscapeALockedLightningPoint(){
        var s=game(FighterKind.NAIWA,FighterKind.FEIDUDU);charge(s);age(s,FightSpecial.BOLT_FIRST-FightSpecial.BOLT_AIM);double aim=s.snapshot().ultimates().getFirst().strikes().getFirst().x();
        step(s,N,new FightCommand(1,false,false,false,false,false,true));steps(s,FightSpecial.BOLT_AIM-1,N,move(1));
        var bolt=s.snapshot().ultimates().getFirst().strikes().getFirst();assertFalse(bolt.hit());assertTrue(f(s,1).x()-aim>90);assertEquals(100,f(s,1).health());
    }
    @Test void webReallyBindsWithoutEarlyDamageAndBurstsOnce(){
        var s=game(FighterKind.FEIDUDU,FighterKind.NAIWA);charge(s);age(s,FightSpecial.WEB_CAPTURE);var ritual=s.snapshot().ultimates().getFirst();
        assertTrue(ritual.captured());assertEquals(ritual.id(),f(s,1).boundBy());assertEquals(100,f(s,1).health());double x=f(s,1).x(),y=f(s,1).y();
        for(int i=0;i<20;i++){step(s,N,new FightCommand(1,true,true,true,true,true,true));assertEquals(x,f(s,1).x());assertEquals(y,f(s,1).y());assertEquals(FightSnapshot.Action.WEB_BOUND,f(s,1).action());assertEquals(100,f(s,1).health());}
        age(s,FightSpecial.WEB_BURST);assertEquals(80,f(s,1).health());assertEquals(0,f(s,1).boundBy());assertTrue(f(s,1).vx()>0);assertFalse(f(s,1).grounded());
        steps(s,100,N,N);assertEquals(80,f(s,1).health());assertEquals(1,f(s,0).hits());
    }
    @Test void movingOutOfWebLockPreventsCaptureAndExplosionDamage(){
        var s=game(FighterKind.FEIDUDU,FighterKind.NAIWA);charge(s);age(s,FightSpecial.WEB_AIM);steps(s,35,N,move(1));
        assertTrue(s.snapshot().ultimates().getFirst().missed());assertFalse(s.snapshot().ultimates().getFirst().captured());assertEquals(0,f(s,1).boundBy());steps(s,120,N,N);assertEquals(100,f(s,1).health());
    }
    @Test void facingGuardRepelsWebBindingWithOnlyChipDamage(){
        var s=game(FighterKind.FEIDUDU,FighterKind.NAIWA);charge(s);age(s,FightSpecial.WEB_AIM);steps(s,35,N,G);
        assertTrue(s.snapshot().ultimates().getFirst().missed());assertEquals(0,f(s,1).boundBy());assertEquals(99,f(s,1).health());steps(s,120,N,N);assertEquals(99,f(s,1).health());
    }
    @Test void hitDuringChargeCancelsRitualWithoutRefundOrDelayedStrike(){
        var s=game(FighterKind.NAIWA,FighterKind.FEIDUDU);steps(s,710,N,N);
        for(int i=0;i<160&&f(s,1).x()-f(s,0).x()>94;i++){step(s,move(1),move(-1));}steps(s,5,N,N);step(s,U,P);steps(s,7,N,N);
        assertEquals(95,f(s,0).health());assertTrue(s.snapshot().ultimates().isEmpty());assertTrue(f(s,0).energy()<10);steps(s,160,N,N);assertEquals(100,f(s,1).health());
    }
    @Test void pauseFreezesBindingStrikesAndCooldownWithoutAcceptingStaleInput(){
        var s=game(FighterKind.FEIDUDU,FighterKind.NAIWA);charge(s);age(s,FightSpecial.WEB_CAPTURE+10);s.pause();var paused=s.snapshot();steps(s,100,GJ,GJ);assertEquals(paused,s.snapshot());s.resume();step(s,N,N);assertEquals(paused.ultimates().getFirst().age()+1,s.snapshot().ultimates().getFirst().age());
    }
    @Test void guardThenJumpTeleportsImmediatelyAndAppliesRealKnockbackAndKnockdown(){
        var s=game(FighterKind.NAIWA,FighterKind.FEIDUDU);step(s,N,G);double old=f(s,1).x();step(s,N,GJ);
        assertEquals(FightSnapshot.Action.TELEPORT,f(s,1).action());assertTrue(Math.abs(f(s,1).x()-old)>900);assertEquals(FightSpecial.BLINK_COOLDOWN,f(s,1).teleportCooldown());
        assertEquals(95,f(s,0).health());assertTrue(f(s,0).vx()>0);assertTrue(f(s,0).vy()>0);assertFalse(f(s,0).grounded());assertEquals(1,s.snapshot().teleports().size());
        assertEquals(FightSnapshot.Attack.TELEPORT,s.snapshot().impacts().getFirst().attack());boolean down=false;for(int i=0;i<70;i++){step(s,N,N);down|=f(s,0).action()==FightSnapshot.Action.KNOCKDOWN;}assertTrue(down);assertTrue(s.snapshot().teleports().isEmpty());
    }
    @Test void initialSimultaneousGuardJumpAndNaiwaDoNotTriggerBlink(){
        var s=game(FighterKind.NAIWA,FighterKind.FEIDUDU);step(s,GJ,GJ);assertTrue(s.snapshot().teleports().isEmpty());assertTrue(f(s,0).y()>0);assertTrue(f(s,1).y()>0);
        var n=game(FighterKind.NAIWA,FighterKind.FEIDUDU);step(n,G,N);step(n,GJ,N);assertTrue(n.snapshot().teleports().isEmpty());assertEquals(100,f(n,1).health());
    }
    @Test void blinkCooldownAttemptIsConsumedAndDoesNotReplayLater(){
        var s=game(FighterKind.NAIWA,FighterKind.FEIDUDU);step(s,N,G);step(s,N,GJ);steps(s,35,N,N);step(s,N,G);step(s,N,GJ);assertTrue(f(s,1).teleportCooldown()>0);assertEquals(FightSnapshot.Action.GUARD,f(s,1).action());
        steps(s,230,N,N);assertEquals(0,f(s,1).teleportCooldown());assertEquals(95,f(s,0).health());assertTrue(s.snapshot().teleports().isEmpty());
    }
    @Test void insufficientEnergyDoesNotTeleportOrQueueAJump(){
        var s=game(FighterKind.NAIWA,FighterKind.FEIDUDU);step(s,N,R);steps(s,35,N,N);step(s,N,G);double y=f(s,1).y();step(s,N,GJ);assertEquals(y,f(s,1).y());assertTrue(s.snapshot().teleports().isEmpty());steps(s,20,N,N);assertEquals(y,f(s,1).y());
    }
    @Test void teleportFallbackAtWallsStaysInBoundsOnEveryMapAndSide(){
        for(var map:FightMap.values()){for(int side=0;side<2;side++){
            var s=new FightSession(new FightSessionFactory.Config(side==0?FighterKind.FEIDUDU:FighterKind.NAIWA,side==1?FighterKind.FEIDUDU:FighterKind.NAIWA,FightSessionFactory.Mode.DUEL,map),3);s.start();
            steps(s,300,side==0?N:move(-1),side==0?move(1):N);step(s,side==0?G:N,side==1?G:N);step(s,side==0?GJ:N,side==1?GJ:N);
            assertEquals(1,s.snapshot().teleports().size());for(var f:s.snapshot().fighters()){assertTrue(f.x()>=60&&f.x()<=2180);assertTrue(Double.isFinite(f.y()));}
            assertTrue(Math.abs(f(s,0).x()-f(s,1).x())>=FightSession.BODY_SPACING);
        }}
    }
    @Test void simultaneousWebsReleaseBothBindingsAndDamageBothSides(){
        var s=game(FighterKind.FEIDUDU,FighterKind.FEIDUDU);steps(s,710,N,N);step(s,U,U);steps(s,FightSpecial.WEB_CAPTURE,N,N);assertTrue(f(s,0).boundBy()>0&&f(s,1).boundBy()>0);
        steps(s,60,N,N);assertEquals(80,f(s,0).health());assertEquals(80,f(s,1).health());assertEquals(0,f(s,0).boundBy());assertEquals(0,f(s,1).boundBy());
    }
    @Test void timeoutCancelsOngoingBindingAndTerminalSnapshotStaysFrozen(){
        var s=game(FighterKind.FEIDUDU,FighterKind.NAIWA);steps(s,FightSession.MATCH_TICKS-90,N,N);step(s,U,N);steps(s,70,N,N);assertTrue(f(s,1).boundBy()>0);steps(s,30,N,N);
        assertEquals(SessionState.FINISHED,s.state());assertTrue(s.snapshot().ultimates().isEmpty());assertEquals(0,f(s,1).boundBy());var end=s.snapshot();steps(s,50,N,N);assertEquals(end,s.snapshot());
    }
    @Test void newSnapshotsAreDeeplyImmutableAndRecordBothSpecialsForReplay(){
        for(var map:FightMap.values()){for(var mode:FightSessionFactory.Mode.values()){
            var factory=new FightSessionFactory();var s=new RecordingSession<>(factory,new FightSessionFactory.Config(FighterKind.FEIDUDU,FighterKind.NAIWA,mode,map),38);s.start();boolean blink=false,ult=false;
            for(int t=0;t<1300&&!s.isFinished();t++){
                var command=t==0?G:t==1?GJ:t==1050?U:N;s.submitInput(new PlayerInput<>(s.tickNumber()+1,0,0,command));s.tick();blink|=!s.snapshot().teleports().isEmpty();ult|=!s.snapshot().ultimates().isEmpty();
                if(!s.snapshot().ultimates().isEmpty()){assertThrows(UnsupportedOperationException.class,()->s.snapshot().ultimates().clear());assertThrows(UnsupportedOperationException.class,()->s.snapshot().ultimates().getFirst().strikes().clear());}
            }
            assertEquals(s.tape().expected(),ReplayRunner.verify(factory,s.tape()));assertTrue(blink);if(mode==FightSessionFactory.Mode.DUEL){assertTrue(ult);}
        }}
    }
    @Test void blinkCanArriveOnARealRoofAndSimultaneousDownJumpStillDropsThrough(){
        var s=game(FighterKind.FEIDUDU,FighterKind.NAIWA);step(s,N,new FightCommand(0,true,false,false,false,false,false));steps(s,70,N,N);assertEquals(1,f(s,1).platform());
        step(s,G,N);step(s,GJ,N);assertEquals(120,f(s,0).y());assertEquals(1,f(s,0).platform());assertTrue(f(s,0).grounded());
        steps(s,45,N,N);assertEquals(FightSnapshot.Action.IDLE,f(s,0).action());step(s,GJ,N);assertFalse(f(s,0).grounded());assertTrue(f(s,0).vy()<0);assertTrue(f(s,0).y()<120);
    }
    @Test void guardHitAndGlobalHitstopDoNotQueueALaterBlink(){
        var s=game(FighterKind.NAIWA,FighterKind.FEIDUDU);steps(s,710,N,N);step(s,R,G);
        for(int i=0;i<110&&f(s,1).health()==100;i++){step(s,N,G);}assertEquals(FightSnapshot.Action.GUARD_HIT,f(s,1).action());step(s,N,GJ);steps(s,20,N,G);
        assertTrue(s.snapshot().teleports().isEmpty());assertEquals(100,f(s,0).health());
    }
    @Test void blinkHasNoInvulnerabilityAgainstAProjectileAtItsDestination(){
        var s=game(FighterKind.NAIWA,FighterKind.FEIDUDU);steps(s,300,move(1),N);
        // 先用真实跳跃越过对手再走到墙边，不能假设身体分离允许从地面直接穿人。
        step(s,new FightCommand(1,true,false,false,false,false,false),move(-1));steps(s,70,move(1),move(-1));steps(s,5,N,N);
        assertEquals(2180,f(s,0).x());step(s,R,G);steps(s,11,N,G);step(s,N,GJ);
        assertEquals(95,f(s,0).health());assertEquals(93,f(s,1).health());assertEquals(FightSnapshot.Action.HURT,f(s,1).action());assertEquals(1,s.snapshot().teleports().size());
    }
}
