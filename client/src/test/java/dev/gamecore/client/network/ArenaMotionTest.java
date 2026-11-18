package dev.gamecore.client.network;

import dev.gamecore.api.session.SessionState;
import dev.gamecore.client.network.sync.*;
import dev.gamecore.fight.*;
import dev.gamecore.online.tank.TankFactory;
import dev.gamecore.tank.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 展示平滑只读契约：插值坐标可变，权威生命/结果/技能来源不变。 */
class ArenaMotionTest {
    private static TankSnapshot tank(long tick,double x,int health,List<TankSnapshot.Bullet> bullets){
        var base=new TankFactory().sessions().create(new TankFactory().defaults(),42).snapshot();
        return new TankSnapshot(base.map(),tick,TankGame.State.RUNNING,TankGame.Result.NONE,
                List.of(new TankSnapshot.Tank(0,new Vec2(x,2),359,new Vec2(1,0),health,0),base.tanks().get(1)),bullets,List.of());
    }
    private static FightSnapshot fight(long tick,double x,int health,FightSnapshot.Action action,List<FightSnapshot.Teleport> teleports){
        var a=new FightSnapshot.Fighter(0,FighterKind.NAIWA,x,0,1,health,42,action,2,0,0,100,0,true,0,-1,0,1,0,0,0,0,0);
        var b=new FightSnapshot.Fighter(1,FighterKind.FEIDUDU,1200,0,-1,100,0,FightSnapshot.Action.IDLE,0,0,0,0,0,true,0,-1,0,0,0,0,0,0,0);
        return new FightSnapshot(tick,SessionState.RUNNING,FightSnapshot.Result.NONE,List.of(a,b),List.of(),List.of(),0,FightMap.NEON,List.of(),teleports);
    }
    @Test void rendersBetweenThirtyHzSnapshotsAtSixtyFpsAndStopsAtEndpoint(){
        var timeline=new ArenaSnapshotTimeline<TankSnapshot>();var a=tank(0,0,5,List.of());var b=tank(2,2,4,List.of());
        timeline.offer(0,a,1_000_000_000L);timeline.offer(2,b,1_033_333_333L);
        var middle=ArenaMotion.tank(b,timeline.frame(1_050_000_000L));
        assertEquals(1,middle.tanks().getFirst().position().x(),.00001);assertEquals(4,middle.tanks().getFirst().health());
        assertEquals(2,middle.tick());assertEquals(2,b.tanks().getFirst().position().x());
        assertEquals(2,ArenaMotion.tank(b,timeline.frame(2_000_000_000L)).tanks().getFirst().position().x());
    }
    @Test void timelineIsBoundedMonotonicWithJitterAndClearRemovesOldMatch(){
        var timeline=new ArenaSnapshotTimeline<Long>();double prior=-1;
        for(long tick=0;tick<500;tick+=2){
            long now=1_000_000_000L+tick*1_000_000_000L/60+(tick%6)*1_000_000L;
            timeline.offer(tick,tick,now);timeline.offer(tick-1,-1L,now);
            var frame=timeline.frame(now);double cursor=frame.from()+(frame.to()-frame.from())*frame.alpha();
            assertTrue(cursor>=prior);assertTrue(cursor<=tick);prior=cursor;
        }
        assertEquals(64,timeline.size());timeline.clear();assertNull(timeline.frame(Long.MAX_VALUE));
        timeline.offer(0,0L,1);assertEquals(0,timeline.frame(1).to());
    }
    @Test void bulletsUseStableIdsAndDoNotResurrectDestroyedObjects(){
        var old=new TankSnapshot.Bullet(1,0,new Vec2(0,0),new Vec2(1,0),10);
        var next=new TankSnapshot.Bullet(1,0,new Vec2(2,0),new Vec2(1,0),8);
        var newborn=new TankSnapshot.Bullet(2,0,new Vec2(9,0),new Vec2(1,0),20);
        var a=tank(0,0,5,List.of(old));var b=tank(2,2,4,List.of(next));
        var authority=tank(4,4,3,List.of(newborn));
        var display=ArenaMotion.tank(authority,new ArenaSnapshotTimeline.Frame<>(a,b,.5));
        assertEquals(List.of(newborn),display.bullets());assertEquals(authority.events(),display.events());
    }
    @Test void fightPreservesAuthoritativeMetadataAndNeverSlidesTeleport(){
        var a=fight(0,100,100,FightSnapshot.Action.WALK,List.of());var b=fight(2,120,90,FightSnapshot.Action.PUNCH,List.of());
        var frame=new ArenaSnapshotTimeline.Frame<>(a,b,.5);var display=ArenaMotion.fight(b,frame);
        assertEquals(110,display.fighters().getFirst().x());assertEquals(90,display.fighters().getFirst().health());
        assertEquals(FightSnapshot.Action.PUNCH,display.fighters().getFirst().action());assertEquals(b.impacts(),display.impacts());
        var teleport=fight(4,800,90,FightSnapshot.Action.TELEPORT,List.of(new FightSnapshot.Teleport(1,0,100,0,800,0,1,0)));
        assertSame(teleport,ArenaMotion.fight(teleport,frame));
    }
    @Test void staleOrDifferentMapCannotBlendIntoCurrentArena(){
        var a=fight(0,100,100,FightSnapshot.Action.WALK,List.of());
        var other=new FightSnapshot(2,SessionState.RUNNING,FightSnapshot.Result.NONE,a.fighters(),List.of(),List.of(),0,FightMap.FORGE);
        assertSame(other,ArenaMotion.fight(other,new ArenaSnapshotTimeline.Frame<>(a,a,.5)));
    }
}
