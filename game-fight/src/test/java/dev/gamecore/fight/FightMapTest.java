package dev.gamecore.fight;

import dev.gamecore.api.session.PlayerInput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 多地图几何和真实公开输入路线；默认入口保留，地图必须确实进入规则及不可变快照。 */
class FightMapTest {
    private static final FightCommand N=FightCommand.NONE,J=new FightCommand(0,true,false,false,false,false,false);
    private static FightCommand move(int d,boolean jump){return new FightCommand(d,jump,false,false,false,false,false);}
    private static FightSession game(FightMap map){var s=new FightSession(new FightSessionFactory.Config(FighterKind.NAIWA,FighterKind.FEIDUDU,FightSessionFactory.Mode.DUEL,map),73);s.start();return s;}
    private static void step(FightSession s,FightCommand c){s.submitInput(new PlayerInput<>(s.tickNumber()+1,0,0,c));s.tick();}
    private static void waitFor(FightSession s,int n){for(int i=0;i<n;i++){step(s,N);}}
    @Test void mapsAreImmutableMirroredDistinctAndLegacyConfigStillDefaultsToNeon(){
        var old=new FightSessionFactory.Config(FighterKind.NAIWA,FighterKind.FEIDUDU,FightSessionFactory.Mode.DUEL);assertEquals(FightMap.NEON,old.map());
        var geometries=new java.util.HashSet<java.util.List<FightArena.Plate>>();
        for(var map:FightMap.values()){
            assertEquals(map==FightMap.BAMBOO||map==FightMap.FROST?7:5,map.platforms().size());assertThrows(UnsupportedOperationException.class,()->map.platforms().clear());geometries.add(map.platforms());
            for(int i=0;i<map.platforms().size();i++){var p=map.platforms().get(i);assertEquals(i,p.id());assertTrue(p.left()>60&&p.right()<2180&&p.height()>0&&p.height()<=400);
                assertTrue(map.platforms().stream().anyMatch(q->q.height()==p.height()&&Math.abs(q.left()-(2240-p.right()))<1e-8&&Math.abs(q.right()-(2240-p.left()))<1e-8));}
            var s=game(map);assertEquals(map,s.snapshot().map());assertEquals(560,s.snapshot().fighters().get(0).x());assertEquals(1680,s.snapshot().fighters().get(1).x());
        }
        assertEquals(FightMap.values().length,geometries.size());
    }
    @Test void landingQueriesUseChosenGeometryAndHonorOneWayAndIgnoredSupport(){
        for(var map:FightMap.values()){for(var p:map.platforms()){double x=(p.left()+p.right())/2;
            assertEquals(p.id(),FightArena.landing(map.platforms(),x,p.height()+2,p.height()-2,-1));
            assertEquals(-1,FightArena.landing(map.platforms(),x,p.height()-2,p.height()+2,-1));
            assertEquals(-1,FightArena.landing(map.platforms(),x,p.height()+2,p.height()-2,p.id()));}}
    }
    @Test void samePublicJumpLandsAtDifferentActualMapHeights(){
        for(var map:FightMap.values()){var s=game(map);step(s,J);waitFor(s,80);var f=s.snapshot().fighters().get(0);
            assertTrue(f.grounded());assertEquals(0,f.platform());assertEquals(map.platforms().get(0).height(),f.y());assertEquals(0,f.jumpCount());}
    }
    @Test void eachMapHasAnActualInputRouteFromGroundViaLowSupportToHighestLayer(){
        for(var map:FightMap.values()){
            var s=game(map);step(s,J);waitFor(s,80);
            int[] route=map==FightMap.FORGE?new int[]{2,3}:map==FightMap.BAMBOO||map==FightMap.FROST?new int[]{3,5}:new int[]{3};
            for(int id:route){var p=map.platforms().get(id);double target=map==FightMap.FORGE&&id==2?880:(p.left()+p.right())/2;jumpTo(s,target,p.height());}
            double highest=map.platforms().stream().mapToDouble(FightArena.Plate::height).max().orElseThrow();
            assertEquals(highest,s.snapshot().fighters().get(0).y(),map.toString());
        }
    }
    /** 通过公开方向/两跳在可达路径上落稳，不写角色位置；刹车预测只服务测试输入。 */
    private static void jumpTo(FightSession s,double x,double y){
        for(int t=0;t<130;t++){var f=s.snapshot().fighters().get(0);double stop=f.x()+f.vx()*Math.abs(f.vx())/.68;
            int direction=stop<x-12?1:stop>x+12?-1:Math.abs(f.vx())>.6?-Integer.signum((int)Math.signum(f.vx())):0;
            step(s,move(direction,t==0||t==19&&!f.grounded()));
            if(t>8&&s.snapshot().fighters().get(0).grounded()){waitFor(s,4);assertEquals(y,s.snapshot().fighters().get(0).y(),s.snapshot().map()+" route x="+x);return;}}
        fail("Route never landed: "+s.snapshot());
    }
    @Test void commandsRemainDeterministicAndBotSeesTheSelectedMap(){
        for(var map:FightMap.values()){
            var config=new FightSessionFactory.Config(FighterKind.NAIWA,FighterKind.FEIDUDU,FightSessionFactory.Mode.BOT,map);
            var a=new FightSession(config,47);var b=new FightSession(config,47);a.start();b.start();
            for(int tick=0;tick<1200&&!a.isFinished();tick++){
                var c=move(tick%200<100?1:-1,tick%80==0||tick%80==20);
                step(a,c);step(b,c);assertEquals(a.snapshot(),b.snapshot());assertEquals(a.events(),b.events());
                for(var f:a.snapshot().fighters()){assertTrue(Double.isFinite(f.x())&&Double.isFinite(f.y()));if(f.grounded()&&f.platform()>=0){assertEquals(map.platforms().get(f.platform()).height(),f.y());}}
            }
            assertEquals(map,a.snapshot().map());
        }
    }
    @Test void actualRecordingAndReplayIncludeAllMapsAndBothModes(){
        var factory=new FightSessionFactory();
        for(var map:FightMap.values()){for(var mode:FightSessionFactory.Mode.values()){
            var recording=new dev.gamecore.api.replay.RecordingSession<>(factory,new FightSessionFactory.Config(FighterKind.NAIWA,FighterKind.FEIDUDU,mode,map),86);
            recording.start();
            for(int t=0;t<500&&!recording.isFinished();t++){
                recording.submitInput(new PlayerInput<>(recording.tickNumber()+1,0,0,move(1,t%60==0||t%60==20)));recording.tick();
            }
            assertEquals(map,recording.snapshot().map());
            assertEquals(recording.tape().expected(),dev.gamecore.api.replay.ReplayRunner.verify(factory,recording.tape()));
        }}
    }
    @Test void newSceneryKeepsOneWayRoofsAndDoesNotTurnItsFacadesIntoInvisibleWalls(){
        for(var map:new FightMap[]{FightMap.BAMBOO,FightMap.FROST}){
            var s=game(map);step(s,J);waitFor(s,80);assertEquals(0,s.snapshot().fighters().get(0).platform());
            step(s,new FightCommand(0,true,false,false,false,true,false,true));waitFor(s,60);assertEquals(0,s.snapshot().fighters().get(0).y());
            for(int i=0;i<80;i++){step(s,move(1,false));}assertTrue(s.snapshot().fighters().get(0).x()>1000);assertEquals(0,s.snapshot().fighters().get(0).y());
        }
    }
}
