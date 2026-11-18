package dev.gamecore.client.fight;

import dev.gamecore.fight.*;
import dev.gamecore.api.session.SessionState;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 只读终局值的视觉测试，覆盖两侧胜者/平局、高台取景、舞步和暂停，不给规则注入胜负。 */
class FightVictoryTest {
    private static FightSnapshot terminal(FightSnapshot.Result result){
        var base=new FightSession(new FightSessionFactory.Config(FighterKind.NAIWA,FighterKind.FEIDUDU,FightSessionFactory.Mode.DUEL),4).snapshot();
        return new FightSnapshot(500,SessionState.FINISHED,result,base.fighters(),List.of(),List.of(),0,base.map());
    }
    private static void advance(FightVictory v,double time){for(int i=0;i<Math.round(time*60);i++){v.advance(1.0/60);}}
    @Test void focusesTheActualWinnerOnEitherSideAfterKnockoutBeat(){
        for(int p=0;p<2;p++){var v=new FightVictory();var view=terminal(p==0?FightSnapshot.Result.PLAYER_ONE:FightSnapshot.Result.PLAYER_TWO);v.observe(view);
            assertEquals(p,v.winner());assertEquals(-1,v.focusedPlayer());advance(v,.6);assertEquals(p,v.focusedPlayer());assertEquals(0,v.pose());}
    }
    @Test void danceUsesAllEightPosesAndHoldsItsFinalPoseWithoutChangingTheSnapshot(){
        var view=terminal(FightSnapshot.Result.PLAYER_ONE);var v=new FightVictory();v.observe(view);var poses=new java.util.HashSet<Integer>();
        for(int i=0;i<420;i++){v.advance(1.0/60);if(v.pose()>=0){poses.add(v.pose());}assertEquals(500,view.tick());assertEquals(SessionState.FINISHED,view.state());}
        assertEquals(8,poses.size());assertEquals(7,v.pose());assertEquals(0,v.xOffset());assertEquals(0,v.bob());
    }
    @Test void drawDoesNotInventAWinnerOrVictoryDance(){var v=new FightVictory();v.observe(terminal(FightSnapshot.Result.DRAW));advance(v,7);assertEquals(-1,v.winner());assertEquals(-1,v.focusedPlayer());assertEquals(-1,v.pose());}
    @Test void pauseInvalidTimeAndRepeatedObservationDoNotRestartOrAdvanceDance(){
        var v=new FightVictory();var view=terminal(FightSnapshot.Result.PLAYER_ONE);v.observe(view);advance(v,2);double before=v.elapsed();
        v.observe(view);assertEquals(before,v.elapsed());v.pause();v.advance(1);assertEquals(before,v.elapsed());v.resume();
        for(double dt:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}){v.advance(dt);assertEquals(before,v.elapsed());}
        v.advance(10);assertEquals(before+.1,v.elapsed(),1e-9);v.reset();assertFalse(v.finished());assertEquals(-1,v.winner());
    }
    @Test void visualLandingAndFocusedCameraKeepElevatedWinnerInsideTheViewport(){
        var base=terminal(FightSnapshot.Result.PLAYER_ONE);var high=new FightSnapshot.Fighter(0,FighterKind.NAIWA,850,500,1,100,80,FightSnapshot.Action.WIN,0,0,0,0,0,false,2,-1,0,0,3,6,100,20,14);
        var view=new FightSnapshot(500,SessionState.FINISHED,FightSnapshot.Result.PLAYER_ONE,List.of(high,base.fighters().get(1)),List.of(),List.of(),0,FightMap.FORGE);
        var v=new FightVictory();v.observe(view);advance(v,1);assertEquals(400,v.feetY());assertEquals(500,view.fighters().get(0).y());
        var camera=new FightCamera();camera.viewport(0,0,1280,480);for(int i=0;i<180;i++){camera.update(view,1f/60,v.focusedPlayer(),v.feetY());}
        assertTrue(camera.y(v.feetY())>=0&&camera.y(v.feetY())<100);assertTrue(camera.y(v.feetY()+120)<480);
    }
    @Test void danceClockAndPoseAgreeAtThirtySixtyAndOneFortyFourFps(){
        int pose=-1;for(int fps:new int[]{30,60,144}){var v=new FightVictory();v.observe(terminal(FightSnapshot.Result.PLAYER_TWO));for(int i=0;i<fps*3;i++){v.advance(1.0/fps);}assertEquals(3,v.elapsed(),1e-8);if(pose<0){pose=v.pose();}assertEquals(pose,v.pose());}
    }
}
