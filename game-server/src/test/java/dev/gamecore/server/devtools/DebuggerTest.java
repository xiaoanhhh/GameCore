package dev.gamecore.server.devtools;
import dev.gamecore.online.GameRegistry;
import dev.gamecore.server.multigame.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
class DebuggerTest {
    @Test void debuggerIsLocalAndPausedRoomStepsWhileOtherRoomContinues() throws Exception{
        var games=GameRegistry.builtIn();try(var server=NettyMultiGameServer.start("127.0.0.1",0,games,RoomHost.defaults(games),true);
            var one=new HeadlessGameClient("127.0.0.1",server.address().getPort(),"tank-1",games.require("tank"));
            var two=new HeadlessGameClient("127.0.0.1",server.address().getPort(),"tank-1",games.require("tank"))){
            one.network().ready().get(4,TimeUnit.SECONDS);two.network().ready().get(4,TimeUnit.SECONDS);var debugger=new TickDebugger(server.rooms(),true);var before=debugger.pause("tank-1").get(4,TimeUnit.SECONDS);
            assertTrue(before.paused());var next=debugger.step("tank-1").get(4,TimeUnit.SECONDS);assertEquals(before.tick()+1,next.tick());assertEquals(before.coreTick()+2,next.coreTick());assertEquals(2,next.entities());assertNotEquals(before.hash(),next.hash());assertEquals(3,debugger.rooms().size());assertThrows(IllegalArgumentException.class,()->new TickDebugger(server.rooms(),false));
            var resumed=debugger.resume("tank-1").get(4,TimeUnit.SECONDS);assertFalse(resumed.paused());
        }
    }
    @Test void telemetryQuantilesAndConfigLimits(){assertEquals(99,ServerTelemetry.percentile(java.util.stream.LongStream.rangeClosed(1,100).boxed().toList(),.99));assertEquals(0,ServerTelemetry.percentile(java.util.List.of(),.5));assertThrows(IllegalArgumentException.class,()->DevToolsMain.specs(GameRegistry.builtIn(),"tank",1,1,true));
        var a=DevToolsMain.specs(GameRegistry.builtIn(),"all",3,2,false);var b=DevToolsMain.specs(GameRegistry.builtIn(),"all",3,2,true);for(int i=0;i<3;i++){assertNotEquals(a.get(i).config(),b.get(i).config());}
    }
}
