package dev.gamecore.client.fight;

import dev.gamecore.fight.*;
import dev.gamecore.online.fight.FightFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 在线复用本地草稿和演出，只读服务器配置/时钟，不生成第二份世界。 */
class OnlineFightPresentationTest {
    @Test void serverClockMatchesOpeningAndAuthoritativePause(){
        assertEquals(FightOpening.TOTAL_SECONDS*1000,new FightFactory().openingMillis(),.00001);
        var opening=new FightOpening(7);opening.synchronize(3.8,true);assertEquals(FightOpening.Phase.VERSUS,opening.phase());
        opening.advance(1);assertEquals(3.8,opening.elapsed(),.00001);opening.synchronize(7.3,false);assertEquals(FightOpening.Phase.FIGHT,opening.phase());
        opening.synchronize(7.65,false);assertEquals(FightOpening.Phase.DONE,opening.phase());
    }
    @Test void onlineDraftKeepsOwnChoiceAndCancellationRestoresActualConfiguration(){
        var selection=new FightSelection(FightSessionFactory.Mode.DUEL);selection.choose(1,FighterKind.NAIWA);
        var actual=new FightSessionFactory.Config(FighterKind.FEIDUDU,FighterKind.FEIDUDU,FightSessionFactory.Mode.DUEL,FightMap.values()[4]);
        selection.observe(actual,1);assertEquals(FighterKind.NAIWA,selection.fighter(1));assertEquals(FighterKind.FEIDUDU,selection.fighter(0));assertEquals(actual.map(),selection.map());
        selection.cancel();assertEquals(actual,selection.committed());assertEquals(FighterKind.FEIDUDU,selection.fighter(1));
    }
}
