package dev.gamecore.fight;

import dev.gamecore.api.session.PlayerInput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** V2 公开输入场景：加速/刹车、腾空预算、单向台面、连段与顿帧；不篡改私有世界。 */
class FightV2Test {
    @Test void dropModifierSurvivesLandingInputBuffer() {
        var s=game(); step(s,J,N);
        for(int i=0;i<60&&!f(s,0).grounded();i++){step(s,N,N);}
        assertEquals(120,f(s,0).y());
        step(s,new FightCommand(0,true,false,false,false,false,false,true),N);
        steps(s,8,N,N);assertTrue(f(s,0).y()<120);assertTrue(f(s,0).vy()<0);
    }
    private static final FightCommand N = FightCommand.NONE;
    private static final FightCommand J = new FightCommand(0,true,false,false,false,false,false);
    private static final FightCommand P = new FightCommand(0,false,true,false,false,false,false);
    private static FightCommand move(int v) { return new FightCommand(v,false,false,false,false,false,false); }
    private static FightSession game() { var s = new FightSession(new FightSessionFactory.Config(FighterKind.NAIWA,FighterKind.FEIDUDU,FightSessionFactory.Mode.DUEL),4); s.start();return s; }
    private static void step(FightSession s,FightCommand a,FightCommand b) { s.submitInput(new PlayerInput<>(s.tickNumber()+1,0,0,a));s.submitInput(new PlayerInput<>(s.tickNumber()+1,1,1,b));s.tick(); }
    private static void steps(FightSession s,int n,FightCommand a,FightCommand b) { for(int i=0;i<n;i++){step(s,a,b);} }
    private static FightSnapshot.Fighter f(FightSession s,int p) { return s.snapshot().fighters().get(p); }
    private static void contact(FightSession s) { for(int i=0;i<160&&f(s,1).x()-f(s,0).x()>FightSession.BODY_SPACING+.01;i++){step(s,move(1),move(-1));}steps(s,5,N,N); }
    @Test void accelerationHasNoVelocityStepAndHoldStartsRun() {
        var s=game(); double old=0;
        for(int i=1;i<=25;i++){step(s,move(1),N);double speed=f(s,0).vx();assertTrue(speed>=old);assertTrue(speed-old<=.620001);if(i<12){assertNotEquals(FightSnapshot.Action.RUN,f(s,0).action());}old=speed;}
        assertEquals(FightSession.RUN_MAX,old,1e-6);assertEquals(FightSnapshot.Action.RUN,f(s,0).action());
    }
    @Test void releaseBrakesInFourStepsAndReverseSkids() {
        var s=game();steps(s,30,move(1),N);double x=f(s,0).x();step(s,N,N);assertTrue(f(s,0).x()>x);assertEquals(FightSnapshot.Action.SKID,f(s,0).action());steps(s,3,N,N);assertEquals(0,f(s,0).vx());
        steps(s,25,move(1),N);step(s,move(-1),N);assertTrue(f(s,0).vx()>0);assertEquals(FightSnapshot.Action.SKID,f(s,0).action());steps(s,20,move(-1),N);assertTrue(f(s,0).vx()<0);
    }
    @Test void secondJumpAddsHeightButThirdCannotResetVelocity() {
        var s=game();step(s,J,N);steps(s,19,N,N);double first=f(s,0).y();step(s,J,N);assertEquals(2,f(s,0).jumpCount());steps(s,9,N,N);double vy=f(s,0).vy();step(s,J,N);assertTrue(f(s,0).vy()<vy);assertTrue(f(s,0).y()>first);assertEquals(2,f(s,0).jumpCount());
    }
    @Test void ascendingPassesThroughAndDescendingLandsOnLowPlate() {
        var s=game();step(s,J,N);boolean passed=false;
        for(int i=0;i<70;i++){step(s,N,N);if(f(s,0).y()>120&&f(s,0).vy()>0){passed=true;assertFalse(f(s,0).grounded());}if(passed&&f(s,0).grounded()){break;}}
        assertTrue(passed);assertEquals(120,f(s,0).y());assertEquals(0,f(s,0).platform());assertEquals(0,f(s,0).jumpCount());
    }
    private static void lowPlate(FightSession s) {step(s,J,N);steps(s,65,N,N);assertEquals(120,f(s,0).y());}
    @Test void downJumpDropsThroughInsteadOfJumping() {
        var s=game();lowPlate(s);step(s,new FightCommand(0,true,false,false,false,true,false,true),N);assertFalse(f(s,0).grounded());assertTrue(f(s,0).vy()<0);steps(s,60,N,N);assertEquals(0,f(s,0).y());assertEquals(-1,f(s,0).platform());
    }
    @Test void leavingPlateFallsNaturallyAndCoyoteJumpWorks() {
        var s=game();lowPlate(s);for(int i=0;i<40&&f(s,0).grounded();i++){step(s,move(1),N);}assertFalse(f(s,0).grounded());step(s,J,N);assertEquals(1,f(s,0).jumpCount());assertTrue(f(s,0).vy()>10);
    }
    @Test void groundDoubleJumpCanReachMiddleButNotHighPlate() {
        var s=game();while(f(s,0).x()<860){step(s,move(1),N);}steps(s,5,N,N);assertTrue(f(s,0).x()<900);
        step(s,J,N);steps(s,20,N,N);step(s,J,N);double max=0;for(int i=0;i<90;i++){step(s,N,N);max=Math.max(max,f(s,0).y());}assertTrue(max>=240&&max<360);assertEquals(240,f(s,0).y());
        step(s,J,N);steps(s,70,N,N);assertEquals(360,f(s,0).y());
    }
    @Test void arenaIsMirroredAndDiscreteJumpReachIsMeasured() {
        assertEquals(5,FightArena.PLATFORMS.size());for(var p:FightArena.PLATFORMS){assertTrue(FightArena.PLATFORMS.stream().anyMatch(q->q.height()==p.height()&&Math.abs(q.left()-(2240-p.right()))<.001));}
        double y=0,vy=FightSession.JUMP_VY,max=0;for(int i=0;i<60;i++){y+=vy;vy-=FightSession.GRAVITY;max=Math.max(max,y);}assertTrue(max>120&&max<160);
        assertEquals(-1,FightArena.landing(560,100,130,-1));assertEquals(0,FightArena.landing(560,125,115,-1));assertEquals(-1,FightArena.landing(560,125,115,0));
    }
    @Test void threePunchesHaveDifferentActionsAndLowerTotalDamage() {
        var s=game();contact(s);boolean second=false,third=false;
        for(int i=0;i<100;i++){step(s,i==0||i==24||i==52?P:N,N);second|=f(s,0).action()==FightSnapshot.Action.PUNCH_2;third|=f(s,0).action()==FightSnapshot.Action.PUNCH_3;}
        assertTrue(second&&third);assertEquals(81,f(s,1).health());assertEquals(3,f(s,0).maxCombo());assertEquals(19,f(s,0).totalDamage());
    }
    @Test void hitstopFreezesBothPositionsAndBufferedPunchSurvives() {
        var s=game();contact(s);step(s,P,N);for(int i=0;i<10&&s.snapshot().hitstopTicks()==0;i++){step(s,N,N);}assertTrue(s.snapshot().hitstopTicks()>0);
        double a=f(s,0).x(),b=f(s,1).x();step(s,P,move(-1));assertEquals(a,f(s,0).x());assertEquals(b,f(s,1).x());
        boolean chained=false;for(int i=0;i<30;i++){step(s,N,N);chained|=f(s,0).action()==FightSnapshot.Action.PUNCH_2;}assertTrue(chained);
    }
    @Test void comboExpiresAndEnergyAndHealthStayBounded() {
        var s=game();contact(s);step(s,P,N);steps(s,15,N,N);assertEquals(1,f(s,0).comboCount());steps(s,60,N,N);assertEquals(0,f(s,0).comboCount());
        for(int i=0;i<600;i++){step(s,i%27==0?P:N,N);for(var v:s.snapshot().fighters()){assertTrue(v.health()>=0&&v.health()<=100);assertTrue(v.energy()>=0&&v.energy()<=100);}}
    }
    @Test void guardedCounterPunchHasAnActualDamageBonus() {
        var s=game();contact(s);var guard=new FightCommand(0,false,false,false,false,true,false);step(s,P,guard);
        for(int i=0;i<12&&f(s,1).health()==100;i++){step(s,N,guard);}assertEquals(99,f(s,1).health());step(s,N,P);steps(s,30,N,N);assertEquals(92,f(s,0).health());
    }
}
