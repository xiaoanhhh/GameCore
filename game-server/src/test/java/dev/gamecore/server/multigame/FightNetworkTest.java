package dev.gamecore.server.multigame;
import dev.gamecore.online.*;
import dev.gamecore.online.fight.FightFactory;
import dev.gamecore.fight.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
class FightNetworkTest {
    @Test void twoFightPeersMoveAndReceiveSameAuthoritativeConclusion() throws Exception{
        var games=GameRegistry.builtIn();try(var server=NettyMultiGameServer.start("127.0.0.1",0,games,RoomHost.defaults(games),true);
            var a=MultiGameNetworkTest.connect(server,"fight");var b=MultiGameNetworkTest.connect(server,"fight")){
            MultiGameNetworkTest.await(()->a.latest()!=null&&a.latest().getStatus().equals("RUNNING"),a);var f=new FightFactory();var before=f.snapshots().decode(a.latest().getState().toByteArray());
            var room=server.rooms().require("fight-1");room.debug(true,false).get(3,TimeUnit.SECONDS);
            a.input(1,room.metrics().tick()+1,f.inputs().encode(new FightCommand(1,false,false,false,false,false,false)));
            MultiGameNetworkTest.await(()->room.metrics().queued()>0,a);room.debug(true,true).get(3,TimeUnit.SECONDS);
            MultiGameNetworkTest.await(()->a.latest().getAcks(0).getExecuted()==1,a);var after=f.snapshots().decode(a.latest().getState().toByteArray());assertTrue(after.fighters().getFirst().x()>before.fighters().getFirst().x());
            for(int i=0;i<=FightSession.MATCH_TICKS&&!room.metrics().status().equals("FINISHED");i++){
                room.debug(true,Math.min(30,FightSession.MATCH_TICKS+1-i)).get(3,TimeUnit.SECONDS);i+=29;while(a.poll()!=null){ }while(b.poll()!=null){ }
            }
            MultiGameNetworkTest.await(()->a.latest().getStatus().equals("FINISHED")&&b.latest()!=null&&b.latest().getStatus().equals("FINISHED"),a);assertEquals(a.latest(),b.latest());assertEquals("fight/7",a.joined().getGame().getRulesVersion());
        }
    }
}
