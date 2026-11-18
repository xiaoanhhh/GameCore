package dev.gamecore.server.multigame;

import dev.gamecore.online.GameRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 用真实Room/Codec比较2秒静止场景的发送频率和Protobuf帧字节；不是公网压测或容量结论。 */
class SnapshotCadenceTest {
    @Test void higherCadenceKeepsIdenticalAuthorityAndReportsBandwidthCost(){
        for(String game:java.util.List.of("tank","fight")){
            var f=GameRegistry.builtIn().require(game);var config=new dev.gamecore.api.online.GameRuleConfig(2,30,f.defaults().options());
            byte[] prior=null;
            for(int rate:new int[]{10,30}){
                var limits=new ServerLimits(3,4,1,1,64,16,rate,Map.of());
                try(var room=new RoomActor(game+"-1",f,config,42,true,new RoomActor.Observer(){},limits)){
                    var a=new RoomActorTest.Peer();RoomActorTest.join(room,a,new RoomActorTest.Peer());
                    long initial=room.metrics().snapshotCount();a.messages.clear();
                    for(int tick=0;tick<60;tick++){RoomActorTest.step(room);}
                    assertEquals(rate*2,room.metrics().snapshotCount()-initial);
                    byte[] state=room.latest().getState().toByteArray();if(prior!=null){assertArrayEquals(prior,state);}prior=state;
                    long bytes=a.messages.stream().filter(e->e.hasSnapshot()).mapToLong(e->e.getSerializedSize()+4L).sum();
                    System.out.println("SNAPSHOT_MODEL game="+game+" rate="+rate+" durationSeconds=2 perClientBytes="+bytes+" twoClientBitsPerSecond="+bytes*8);
                }
            }
        }
    }
}
