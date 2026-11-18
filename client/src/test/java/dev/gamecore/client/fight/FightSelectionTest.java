package dev.gamecore.client.fight;

import dev.gamecore.fight.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 选择事务不依赖 GL；覆盖默认/同角色/两模式、返回取消、提交边界及布局投影。 */
class FightSelectionTest {
    @Test void startsWithTwoDifferentFightersAndDefaultMapInBothModes(){for(var mode:FightSessionFactory.Mode.values()){
        var s=new FightSelection(mode);assertEquals(FightSelection.Page.FIGHTERS,s.page());assertEquals(FighterKind.NAIWA,s.fighter(0));assertEquals(FighterKind.FEIDUDU,s.fighter(1));assertEquals(FightMap.NEON,s.map());assertEquals(mode,s.committed().mode());}}
    @Test void browsingDoesNotCommitAndOnlySecondNextCommitsCompleteConfiguration(){
        var s=new FightSelection(FightSessionFactory.Mode.BOT);var original=s.committed();s.choose(0,FighterKind.FEIDUDU);s.choose(1,FighterKind.NAIWA);s.choose(FightMap.FORGE);
        assertEquals(original,s.committed());assertFalse(s.next());assertEquals(FightSelection.Page.MAPS,s.page());assertEquals(original,s.committed());assertTrue(s.next());
        assertFalse(s.active());assertEquals(new FightSessionFactory.Config(FighterKind.FEIDUDU,FighterKind.NAIWA,FightSessionFactory.Mode.BOT,FightMap.FORGE),s.committed());}
    @Test void returningToFightersKeepsDraftWhileCancelRestoresCommittedValues(){
        var s=new FightSelection(FightSessionFactory.Mode.DUEL);s.choose(0,FighterKind.FEIDUDU);s.maps();s.choose(FightMap.TEMPLE);s.fighters();assertEquals(FighterKind.FEIDUDU,s.fighter(0));assertEquals(FightMap.TEMPLE,s.map());s.cancel();
        assertFalse(s.active());assertEquals(FighterKind.NAIWA,s.fighter(0));assertEquals(FightMap.NEON,s.map());s.open();assertEquals(FightSelection.Page.FIGHTERS,s.page());}
    @Test void confirmedChoicesSurviveReopeningAndSameCharacterIsAllowed(){
        var s=new FightSelection(FightSessionFactory.Mode.DUEL);s.choose(1,FighterKind.NAIWA);s.choose(FightMap.TEMPLE);s.next();s.next();s.open();
        assertEquals(s.fighter(0),s.fighter(1));assertEquals(FightMap.TEMPLE,s.map());s.choose(FightMap.FORGE);s.cancel();assertEquals(FightMap.TEMPLE,s.committed().map());}
    @Test void keyboardCyclesWrapInEitherDirectionAndNeverChangeTheOtherPlayer(){
        var s=new FightSelection(FightSessionFactory.Mode.DUEL);s.cycleFighter(0,-1);assertEquals(FighterKind.FEIDUDU,s.fighter(0));assertEquals(FighterKind.FEIDUDU,s.fighter(1));s.cycleFighter(0,1);assertEquals(FighterKind.NAIWA,s.fighter(0));s.cycleMap(-1);assertEquals(FightMap.FROST,s.map());s.cycleMap(1);assertEquals(FightMap.NEON,s.map());}
    @Test void invalidSlotsNullChoicesAndClosedDraftChangesAreRejected(){
        var s=new FightSelection(FightSessionFactory.Mode.DUEL);assertThrows(IllegalArgumentException.class,()->s.choose(2,FighterKind.NAIWA));assertThrows(IllegalArgumentException.class,()->s.fighter(-1));assertThrows(NullPointerException.class,()->s.choose((FightMap)null));assertThrows(NullPointerException.class,()->s.choose(0,null));s.cancel();assertThrows(IllegalStateException.class,()->s.next());assertThrows(IllegalStateException.class,()->s.choose(FightMap.NEON));}
    @Test void fittedCoordinatesKeepTheWholeDesignAndButtonsInsideDifferentViewports(){
        for(float[] size:new float[][]{{1280,720},{1280,853.333f},{1600,900},{2560,1494},{2560,1080}}){var f=FightSelectionLayout.fit(size[0],size[1]);assertTrue(f.x(0)>=0&&f.y(0)>=0);assertTrue(f.x(1280)<=size[0]+.01&&f.y(720)<=size[1]+.01);assertEquals(f.size(100),f.x(100)-f.x(0),.01);assertEquals(f.size(100),f.y(100)-f.y(0),.01);
            assertTrue(f.x(906)+f.size(342)<=size[0]);assertTrue(f.y(28)>=0);}}
    @Test void fittedLayoutRejectsNonFiniteAndEmptyViewports(){for(float bad:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY}){assertThrows(IllegalArgumentException.class,()->FightSelectionLayout.fit(bad,720));assertThrows(IllegalArgumentException.class,()->FightSelectionLayout.fit(1280,bad));}}
}
