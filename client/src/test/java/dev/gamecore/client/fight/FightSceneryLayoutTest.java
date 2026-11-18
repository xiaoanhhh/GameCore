package dev.gamecore.client.fight;

import dev.gamecore.fight.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 纯 Java 验证建筑顶沿与实际支撑完全一致、构件不可变、五卡片不出界，防止美术虚构落脚面。 */
class FightSceneryLayoutTest {
    @Test void everyArchitecturalPropUsesExactlyOneRealSupportWithoutExtraStandingEdges(){
        for(var map:FightMap.values()){var props=FightSceneryLayout.props(map);assertEquals(map.platforms().size(),props.size());
            for(var p:props){assertSame(map.platforms().get(p.support().id()),p.support());assertEquals(p.support().left(),p.left());assertEquals(p.support().right(),p.right());assertEquals(p.support().height(),p.top());assertTrue(p.depth()>0&&p.bottom()>=0);}
            assertThrows(UnsupportedOperationException.class,()->props.clear());assertEquals(3,props.stream().map(FightSceneryLayout.Prop::shape).distinct().count());}}
    @Test void newMapsOfferSevenSupportsAndTheirExtraLayerIsVisibleInTheRouteInformation(){
        assertEquals(7,FightSceneryLayout.props(FightMap.BAMBOO).size());assertEquals(7,FightSceneryLayout.props(FightMap.FROST).size());assertEquals("100 / 190 / 245 / 365",FightSceneryLayout.levels(FightMap.BAMBOO));assertEquals("130 / 230 / 275 / 390",FightSceneryLayout.levels(FightMap.FROST));assertEquals("120 / 240 / 360",FightSceneryLayout.levels(FightMap.NEON));}
    @Test void fiveMapCardsFitInThePreviewColumnWithoutOverlappingOrTouchingTheIntelPanel(){
        float right=0;for(int i=0;i<FightMap.values().length;i++){var card=FightSelectionLayout.mapCard(i,FightMap.values().length);assertTrue(card.x()>=32&&card.x()>=right);assertTrue(card.width()>150);right=card.x()+card.width();assertTrue(right<=890.1f);assertEquals(118,card.y());assertEquals(108,card.height());}
        assertThrows(IllegalArgumentException.class,()->FightSelectionLayout.mapCard(0,0));assertThrows(IllegalArgumentException.class,()->FightSelectionLayout.mapCard(5,5));}
    @Test void projectedFiveCardButtonsRemainInsideSmallTallAndWideWindows(){
        for(float[] size:new float[][]{{1280,720},{1280,853.333f},{2560,1494},{2560,1080}}){var fit=FightSelectionLayout.fit(size[0],size[1]);for(int i=0;i<FightMap.values().length;i++){var c=FightSelectionLayout.mapCard(i,FightMap.values().length);assertTrue(fit.x(c.x())>=0&&fit.y(c.y())>=0&&fit.x(c.x()+c.width())<=size[0]&&fit.y(c.y()+c.height())<=size[1]);}}}
    @Test void sixRealMaterialRegionsAreInsideTheirActualAtlasAndHaveDifferentSamples() throws Exception {
        try(var in=getClass().getResourceAsStream("/"+FightSceneryMaterial.ASSET)){assertNotNull(in);var image=javax.imageio.ImageIO.read(in);assertEquals(FightSceneryMaterial.WIDTH,image.getWidth());assertEquals(FightSceneryMaterial.HEIGHT,image.getHeight());
            var samples=new java.util.HashSet<Integer>();for(var m:FightSceneryMaterial.values()){assertTrue(m.x>=3&&m.y>=3&&m.x+FightSceneryMaterial.SIZE<=image.getWidth()&&m.y+FightSceneryMaterial.SIZE<=image.getHeight());samples.add(image.getRGB(m.x+250,m.y+250));}assertEquals(6,samples.size());}
    }
}
