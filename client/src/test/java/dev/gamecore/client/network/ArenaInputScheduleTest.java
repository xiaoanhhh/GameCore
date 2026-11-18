package dev.gamecore.client.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 虚拟单调时钟验证发送节拍/安全余量；不把模型数值冒充公网输入延迟。 */
class ArenaInputScheduleTest {
    @Test void fixedDeadlinesKeepThirtyHzAtSixtyFpsWithoutBursts(){
        var clock=new ArenaInputSchedule();int sends=0,oldSends=0;long oldDue=0,last=0;
        for(int frame=0;frame<300;frame++){
            long now=1_000_000_000L+frame*1_000_000_000L/60+(frame%3)*200_000L;
            if(now>=oldDue){oldSends++;oldDue=now+1_000_000_000L/30;}
            long arrival=1_000_000_000L+(frame/2)*1_000_000_000L/30;
            long target=clock.target(now,30,100+frame/2,arrival,20_000_000L,false);
            if(target>0){assertTrue(target>last,"No same-Tick pulse replacement");last=target;sends++;}
        }
        assertEquals(150,sends);assertTrue(oldSends<sends);
        System.out.println("CADENCE_MODEL fiveSeconds fps=60 old="+oldSends+" new="+sends);
    }
    @Test void removesOneTickMarginButStillCoversFullRoundTripAndSnapshotAge(){
        var clock=new ArenaInputSchedule();long period=1_000_000_000L/30;
        long target=clock.target(1_080_000_000L,30,100,1_000_000_000L,200_000_000L,false);
        assertEquals(110,target);
        long oldLead=(200_000_000L+2*period+80_000_000L+period-1)/period;
        assertEquals(1,100+oldLead-target);
        assertTrue((target-100)*period>=280_000_000L);
    }
    @Test void stalledRenderNeverCatchesUpAndNeverExceedsFutureWindow(){
        var clock=new ArenaInputSchedule();assertTrue(clock.target(1_000_000_000L,30,100,1_000_000_000L,10_000_000L,false)>0);
        assertEquals(115,clock.target(5_000_000_000L,30,100,1_000_000_000L,10_000_000L,false));
        assertEquals(0,clock.target(5_000_000_001L,30,100,1_000_000_000L,10_000_000L,true));
        clock.reset();assertEquals(102,clock.target(5_000_000_001L,30,100,5_000_000_001L,10_000_000L,false));
    }
    @Test void releaseHasItsOwnTickAndEpochResetRemovesOldDeadline(){
        var clock=new ArenaInputSchedule();long first=clock.target(1_000_000_000L,60,100,1_000_000_000L,50_000_000L,false);
        assertEquals(first+1,clock.target(1_000_000_001L,60,100,1_000_000_000L,50_000_000L,true));
        clock.reset();assertEquals(first,clock.target(1_000_000_002L,60,100,1_000_000_002L,50_000_000L,false));
    }
    @Test void actualLateFeedbackAddsBoundedMarginAndEpochResetRestoresLowDelay(){
        var clock=new ArenaInputSchedule();assertEquals(1,clock.marginTicks());
        for(int i=0;i<20;i++){clock.lateInput();}assertEquals(4,clock.marginTicks());
        assertEquals(113,clock.target(1_080_000_000L,30,100,1_000_000_000L,200_000_000L,false));
        clock.reset();assertEquals(1,clock.marginTicks());
        assertEquals(110,clock.target(1_080_000_000L,30,100,1_000_000_000L,200_000_000L,false));
    }
}
