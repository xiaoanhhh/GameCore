package dev.gamecore.server.multigame;

import dev.gamecore.online.GameRegistry;
import dev.gamecore.network.transport.GameNetworkConnection;
import dev.gamecore.protocol.GameProtocol;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 真 TCP 验证 Joined/准备状态/快照跨局顺序，旧帧不能在新身份后写出。 */
class InteractiveNetworkTest {
    private static void await(java.util.function.BooleanSupplier done,GameNetworkConnection a,GameNetworkConnection b) throws Exception{
        long end=System.nanoTime()+5_000_000_000L;
        while(!done.getAsBoolean()&&System.nanoTime()<end){assertEquals("",a.failure());assertEquals("",b.failure());Thread.sleep(3);}
        assertTrue(done.getAsBoolean(),"Room did not advance: "+a.failure()+" / "+b.failure());
    }
    @Test void bothPeersRetainConnectionsAcrossInteractiveRounds() throws Exception{
        var games=GameRegistry.builtIn();try(var server=NettyMultiGameServer.start("127.0.0.1",0,games,RoomHost.defaults(games),false);
            var a=GameNetworkConnection.openTicket("127.0.0.1",server.address().getPort(),"fight-1","fight","1","fight/7","",null,false,null,true);
            var b=GameNetworkConnection.openTicket("127.0.0.1",server.address().getPort(),"fight-1","fight","1","fight/7","",null,false,null,true)){
            a.ready().get(5,TimeUnit.SECONDS);b.ready().get(5,TimeUnit.SECONDS);await(()->a.roomState()!=null&&b.roomState()!=null&&a.roomState().getPhase().equals("SETUP")&&b.roomState().getPhase().equals("SETUP"),a,b);
            a.control(1,GameProtocol.RoomAction.READY,Map.of());b.control(1,GameProtocol.RoomAction.READY,Map.of());await(()->a.roomState().getPhase().equals("OPENING")&&b.roomState().getPhase().equals("OPENING"),a,b);
            a.control(2,GameProtocol.RoomAction.SKIP_OPENING,Map.of());b.control(2,GameProtocol.RoomAction.SKIP_OPENING,Map.of());await(()->a.roomState().getPhase().equals("RUNNING")&&b.roomState().getPhase().equals("RUNNING"),a,b);
            String previous=a.joined().getMatchId();a.control(3,GameProtocol.RoomAction.RESTART,Map.of());b.control(3,GameProtocol.RoomAction.RESTART,Map.of());
            await(()->!a.joined().getMatchId().equals(previous)&&a.roomState()!=null&&a.roomState().getPhase().equals("SETUP")&&b.roomState()!=null&&a.joined().getMatchId().equals(b.joined().getMatchId()),a,b);
            assertEquals(2,server.rooms().require("fight-1").metrics().players());assertEquals("",a.failure());assertEquals("",b.failure());
            server.rooms().destroyIfMatch("fight-1",previous).get(3,TimeUnit.SECONDS);
            assertEquals("SETUP",server.rooms().require("fight-1").metrics().status());
            server.rooms().retire("fight-1",a.joined().getMatchId());
            a.closeAsync().get(4,TimeUnit.SECONDS);b.closeAsync().get(4,TimeUnit.SECONDS);
            await(()->server.rooms().rooms().stream().noneMatch(r->r.id().equals("fight-1")),a,b);
        }
    }
}
