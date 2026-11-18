package dev.gamecore.client.ui;

import dev.gamecore.client.fight.FightHudModel;
import dev.gamecore.fight.*;
import dev.gamecore.online.snake.SnakeFactory;
import dev.gamecore.online.tank.TankFactory;
import dev.gamecore.snake.*;
import dev.gamecore.tank.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 无图形验证解码后的权威快照与本地 HUD 数值、速度、计时和本机操作提示一致。 */
class ArenaHudTextTest {
    @Test void snakeScoresAndSharedSpeedComeFromActualSnapshot(){
        var view=new SnakeSnapshot(SnakeMode.DUO,SnakeGame.State.RUNNING,SnakeGame.Result.NONE,12,
                List.of(new SnakeSnapshot.Player(List.of(new Cell(2,3)),Direction.RIGHT,20,SnakeGame.Collision.NONE),
                        new SnakeSnapshot.Player(List.of(new Cell(8,9)),Direction.LEFT,10,SnakeGame.Collision.NONE)),Set.of(),new Cell(5,5));
        var codec=new SnakeFactory().snapshots();var remote=codec.decode(codec.encode(view));
        assertEquals("P1  20\nP2  10",ArenaHudText.snakeScores(remote,true));
        assertEquals("P1  20    P2  10",ArenaHudText.snakeScores(view,false));
        assertEquals("速度 12 格/秒  ·  每 3 颗加速，最高 20",ArenaHudText.snakeSpeed(remote));
        assertEquals(ArenaHudText.snakeSpeed(view),ArenaHudText.snakeSpeed(remote));
    }
    @Test void tankUsesDecodedHealthAndRoundsRemainingSecondsUp(){
        var factory=new TankFactory();var initial=factory.sessions().create(factory.defaults(),42).snapshot();
        for(long tick:new long[]{0,1,60,TankGame.MATCH_TICKS,TankGame.MATCH_TICKS+60}){
            var local=new TankSnapshot(initial.map(),tick,initial.state(),initial.result(),initial.tanks(),initial.bullets(),initial.events());
            var remote=factory.snapshots().decode(factory.snapshots().encode(local));
            assertEquals(ArenaHudText.tankHealth(local),ArenaHudText.tankHealth(remote));
            assertEquals(tick<=1?"剩余 03:00":tick==60?"剩余 02:59":"剩余 00:00",ArenaHudText.tankTime(remote));
        }
    }
    /** 地图浮层：进行中不遮挡；结算区分胜方/人机/平局，并按真实 tick 区分“时间到”与“对局结束”。 */
    @Test void tankBannerFollowsSnapshotStateAndResult(){
        var factory=new TankFactory();var s=factory.sessions().create(factory.defaults(),42).snapshot();
        java.util.function.BiFunction<TankGame.State,TankGame.Result,TankSnapshot> at=(state,result)->
                new TankSnapshot(s.map(),120,state,result,s.tanks(),s.bullets(),s.events());
        assertEquals("准备出击",ArenaHudText.tankBanner(at.apply(TankGame.State.READY,TankGame.Result.NONE),false).title());
        assertEquals("已暂停",ArenaHudText.tankBanner(at.apply(TankGame.State.PAUSED,TankGame.Result.NONE),false).title());
        assertNull(ArenaHudText.tankBanner(at.apply(TankGame.State.RUNNING,TankGame.Result.NONE),false));
        var p2=at.apply(TankGame.State.FINISHED,TankGame.Result.PLAYER_TWO);
        assertEquals("P2 获胜",ArenaHudText.tankBanner(p2,false).title());
        assertEquals("电脑获胜",ArenaHudText.tankBanner(p2,true).title());
        assertTrue(ArenaHudText.tankBanner(p2,false).detail().startsWith("对局结束"));
        var timeout=new TankSnapshot(s.map(),TankGame.MATCH_TICKS,TankGame.State.FINISHED,TankGame.Result.DRAW,s.tanks(),s.bullets(),s.events());
        assertEquals("本局平局",ArenaHudText.tankBanner(timeout,true).title());
        assertTrue(ArenaHudText.tankBanner(timeout,true).detail().startsWith("时间到"));
    }
    @Test void remoteSlotTwoUsesLocalPlayerOneKeysAndOpponentHasNoKeys(){
        assertEquals("J 出拳",FightHudModel.Skill.PUNCH.label(0));
        assertEquals("N1 出拳",FightHudModel.Skill.PUNCH.label(1));
        assertEquals("出拳",FightHudModel.Skill.PUNCH.label(-1));
        var session=new FightSession(new FightSessionFactory.Config(FighterKind.NAIWA,FighterKind.FEIDUDU,FightSessionFactory.Mode.DUEL),7);
        var fighter=session.snapshot().fighters().get(1);
        assertTrue(FightHudModel.blinkLabel(fighter,0).contains("S+K"));
        assertFalse(FightHudModel.blinkLabel(fighter,-1).contains("N2"));
        assertTrue(FightHudModel.blinkLabel(fighter).contains("↓+N2"));
    }
}
