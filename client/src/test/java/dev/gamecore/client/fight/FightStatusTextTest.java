package dev.gamecore.client.fight;

import dev.gamecore.api.session.SessionState;
import dev.gamecore.fight.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 在线状态栏复用本地对阵/提示/胜负文案，只额外说明双方同意的房间控制。 */
class FightStatusTextTest {
    private static FightSnapshot snapshot(SessionState state,FightSnapshot.Result result,long tick){
        var base=new FightSessionFactory().create(new FightSessionFactory.Config(FighterKind.NAIWA,FighterKind.FEIDUDU,FightSessionFactory.Mode.DUEL,FightMap.NEON),73).snapshot();
        return new FightSnapshot(tick,state,result,base.fighters(),List.of(),List.of(),0,base.map());
    }
    @Test void onlineUsesLocalMatchupHintAndResult(){
        var ready=snapshot(SessionState.READY,FightSnapshot.Result.NONE,0);
        assertTrue(FightStatusText.local(ready).endsWith(FightStatusText.matchup(ready)));
        assertEquals("选择完成后准备 · 1 / 2 已准备  /  P1 奶蛙 · 闪电波   VS   P2 肥嘟嘟 · 蜘蛛丝",FightStatusText.online("SETUP",false,false,false,false,ready,"选择完成后准备 · 1 / 2 已准备"));
        var running=snapshot(SessionState.RUNNING,FightSnapshot.Result.NONE,30);
        assertEquals(FightStatusText.local(running),FightStatusText.online("RUNNING",false,false,false,false,running,"比赛进行中"));
        var ko=snapshot(SessionState.FINISHED,FightSnapshot.Result.PLAYER_TWO,600);
        assertEquals("P2 肥嘟嘟 获胜 · K.O.",FightStatusText.online("FINISHED",false,false,false,false,ko,"比赛结束"));
        var timeout=snapshot(SessionState.FINISHED,FightSnapshot.Result.DRAW,FightSession.MATCH_TICKS);
        assertEquals(FightStatusText.local(timeout),FightStatusText.online("FINISHED",false,false,false,false,timeout,"比赛结束"));
        assertEquals("本局平局 · 时间到",FightStatusText.local(timeout));
    }
    @Test void roomControlsExplainSharedConsentAndKeepRequestsVisible(){
        var running=snapshot(SessionState.RUNNING,FightSnapshot.Result.NONE,30);
        assertEquals("已请求跳过开场 · 等待对手确认",FightStatusText.online("OPENING",false,true,false,false,running,""));
        assertTrue(FightStatusText.online("OPENING",false,false,false,false,running,"").contains("Enter / Space 跳过开场"));
        assertTrue(FightStatusText.online("OPENING",true,false,false,false,running,"").startsWith("Ⅱ 开场已暂停"));
        assertEquals("Ⅱ 已暂停 · 等待对手继续",FightStatusText.online("PAUSED",true,false,true,false,running,""));
        assertTrue(FightStatusText.online("FINISHED",true,false,false,false,running,"").startsWith("Ⅱ 庆祝已暂停"));
        assertEquals("收到重开请求",FightStatusText.online("RUNNING",false,false,false,true,running,"收到重开请求"));
        assertEquals("比赛结束",FightStatusText.online("FINISHED",false,false,false,false,running,"比赛结束"),"尚未收到终局快照时不伪造胜者");
        assertEquals("正在准备新一局",FightStatusText.online("RESETTING",false,false,false,false,running,"正在准备新一局"));
    }
}
