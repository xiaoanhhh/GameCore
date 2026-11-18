package dev.gamecore.client.fight;

import dev.gamecore.api.session.SessionState;
import dev.gamecore.fight.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 无 GL 验证开场时间轴与镜头；演出只能观察 READY 快照，不能偷偷推进 Bot 或战斗计时。 */
class FightOpeningTest {
    private static FightSnapshot view(FighterKind a, FighterKind b) {
        return new FightSession(new FightSessionFactory.Config(a,b,FightSessionFactory.Mode.BOT), 73).snapshot();
    }
    private static void advance(FightOpening o, double seconds) { for (int i=0; i<Math.round(seconds*60); i++) { o.advance(1.0/60); } }
    @Test void identityOrderSurvivesPlayerSwapAndMirrorMatchesStillShowBothPlayers() {
        var o=new FightOpening(4); o.start(view(FighterKind.FEIDUDU,FighterKind.NAIWA)); assertEquals(1,o.focusedPlayer());
        advance(o,1.8);assertEquals(0,o.focusedPlayer());
        o.start(view(FighterKind.FEIDUDU,FighterKind.FEIDUDU));assertEquals(0,o.focusedPlayer());advance(o,1.8);assertEquals(1,o.focusedPlayer());
    }
    @Test void greetingsThenEveryCountdownNumberThenFightFinishAtSameDuration() {
        var o=new FightOpening(3);var v=view(FighterKind.NAIWA,FighterKind.FEIDUDU);o.start(v);
        advance(o,1.8);assertEquals(FightOpening.Phase.SECOND,o.phase());advance(o,1.8);assertEquals(FightOpening.Phase.VERSUS,o.phase());
        advance(o,.5);assertEquals(3,o.number());advance(o,1);assertEquals(2,o.number());advance(o,1);assertEquals(1,o.number());
        advance(o,1);assertEquals(FightOpening.Phase.FIGHT,o.phase());advance(o,.55);assertFalse(o.active());assertEquals(FightOpening.TOTAL_SECONDS,o.elapsed(),1e-6);
        assertEquals(0,v.tick());assertEquals(SessionState.READY,v.state());
    }
    @Test void pauseSkipAndInvalidDeltasNeverLeakElapsedTime() {
        var o=new FightOpening(8);o.start(view(FighterKind.NAIWA,FighterKind.FEIDUDU));advance(o,.5);o.pause();double t=o.elapsed();
        assertFalse(o.advance(2));assertFalse(o.skip());assertEquals(t,o.elapsed());o.resume();
        for(double dt:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,0}){assertFalse(o.advance(dt));assertEquals(t,o.elapsed());}
        assertTrue(o.skip());assertFalse(o.active());assertFalse(o.skip());o.reset();assertEquals(FightOpening.Phase.IDLE,o.phase());
    }
    @Test void randomGreetingsVaryAcrossRoundsWithoutImmediateRepeatAndCoverAllFourStyles() {
        var o=new FightOpening(18);var v=view(FighterKind.NAIWA,FighterKind.FEIDUDU);var seen=new java.util.HashSet<Integer>();int previous=-1;
        for(int i=0;i<40;i++){o.start(v);int style=o.style(0);assertTrue(style>=0&&style<4);assertNotEquals(previous,style);seen.add(style);previous=style;assertTrue(o.greetingPose(0)>=0);assertEquals(-1,o.greetingPose(1));}
        assertEquals(4,seen.size());
    }
    @Test void presentationDurationIsFrameRateIndependentAndLagIsCapped() {
        for(int fps:new int[]{30,60,144}){var o=new FightOpening(9);o.start(view(FighterKind.NAIWA,FighterKind.FEIDUDU));int frames=0;while(o.active()&&frames<fps*10){o.advance(1.0/fps);frames++;}assertFalse(o.active());assertEquals(FightOpening.TOTAL_SECONDS,frames/(double)fps,1.0/fps);}
        var o=new FightOpening(9);o.start(view(FighterKind.NAIWA,FighterKind.FEIDUDU));o.advance(30);assertEquals(.1,o.elapsed(),1e-9);
    }
    @Test void focusedCameraEnlargesActualCharacterAndReturnsToBattleProjection() {
        var c=new FightCamera();var v=view(FighterKind.NAIWA,FighterKind.FEIDUDU);c.viewport(0,0,1280,500);
        for(int i=0;i<120;i++){c.update(v,1f/60,0);}assertTrue(c.zoom()>3.5);assertTrue(Math.abs(c.center()-635)<5);
        float x=c.x(560);c.update(v,0,0);assertEquals(x,c.x(560));
        for(int i=0;i<180;i++){c.update(v,1f/60,-1);}assertTrue(c.zoom()<=1.01);assertTrue(Math.abs(c.center()-1120)<2);c.reset();assertEquals(.86f,c.zoom());
    }
}
