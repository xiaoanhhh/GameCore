package dev.gamecore.server.multigame;
import dev.gamecore.online.*;
import dev.gamecore.network.transport.GameNetworkConnection;
import dev.gamecore.protocol.GameProtocol;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
class MultiGameNetworkTest {
    static void await(BooleanSupplier condition,GameNetworkConnection connection) throws Exception {long end=System.nanoTime()+5_000_000_000L;while(!condition.getAsBoolean()&&System.nanoTime()<end){connection.receive(Duration.ofMillis(5));}assertTrue(condition.getAsBoolean(),"Condition timed out");}
    static GameNetworkConnection connect(NettyMultiGameServer server,String game) throws Exception{var f=GameRegistry.builtIn().require(game);var c=GameNetworkConnection.open("127.0.0.1",server.address().getPort(),game+"-1",game,f.type().gameVersion(),f.type().rulesVersion());c.ready().get(5,TimeUnit.SECONDS);return c;}
    @Test void twoSnakeClientsShareAuthoritativeFoodBodiesAndResult() throws Exception{
        var registry=GameRegistry.builtIn();var server=NettyMultiGameServer.start("127.0.0.1",0,registry,RoomHost.defaults(registry),false);
        try(server;var a=connect(server,"snake");var b=connect(server,"snake")){
            await(()->a.latest()!=null&&a.latest().getStatus().equals("FINISHED")&&b.latest()!=null&&b.latest().getStatus().equals("FINISHED"),a);
            assertEquals(a.latest(),b.latest());assertEquals(20,a.joined().getGame().getSimulationTps());
            var snapshot=registry.require("snake").snapshots().decode(a.latest().getState().toByteArray());assertInstanceOf(dev.gamecore.snake.SnakeSnapshot.class,snapshot);
            assertEquals(dev.gamecore.snake.SnakeGame.Result.DRAW,((dev.gamecore.snake.SnakeSnapshot)snapshot).result());assertNotEquals(a.joined().getPlayerSlot(),b.joined().getPlayerSlot());
        }assertTrue(server.terminated());assertEquals(0,server.connections());
    }
    @Test void simultaneousRoomsAreIsolatedAndTankMoves() throws Exception{
        var registry=GameRegistry.builtIn();try(var server=NettyMultiGameServer.start("127.0.0.1",0,registry,RoomHost.defaults(registry),true);
            var a=connect(server,"tank");var b=connect(server,"tank");var snake=connect(server,"snake")){
            assertTrue(b.active());assertTrue(snake.active());
            await(()->a.latest()!=null&&a.latest().getStatus().equals("RUNNING"),a);var f=new dev.gamecore.online.tank.TankFactory();var before=f.snapshots().decode(a.latest().getState().toByteArray());
            a.input(1,a.latest().getServerTick()+3,f.inputs().encode(new dev.gamecore.tank.TankCommand(1,0,true)));
            await(()->a.latest().getAcks(0).getExecuted()==1,a);var after=f.snapshots().decode(a.latest().getState().toByteArray());assertNotEquals(before.tanks().getFirst().position(),after.tanks().getFirst().position());
            assertEquals("WAITING",server.rooms().require("snake-1").metrics().status());assertEquals(0,server.rooms().require("snake-1").metrics().coreTick());
            a.sendRaw(ProtocolValues.envelope(99,e->e.setInput(GameProtocol.Input.newBuilder().setRoomId("snake-1").setGameId("snake").setMatchId(a.joined().getMatchId()).setSequence(2).setTargetTick(a.latest().getServerTick()+3).setCommand(com.google.protobuf.ByteString.copyFrom(f.inputs().encode(dev.gamecore.tank.TankCommand.IDLE))))));
            GameProtocol.Envelope error=null;long end=System.nanoTime()+2_000_000_000L;while(System.nanoTime()<end){var e=a.receive(Duration.ofMillis(10));if(e!=null&&e.hasError()){error=e;break;}}assertNotNull(error);assertEquals("WRONG_GAME_OR_MATCH",error.getError().getCode());
            server.rooms().destroy("tank-1").get(3,TimeUnit.SECONDS);assertThrows(IllegalArgumentException.class,()->server.rooms().require("tank-1"));assertEquals(1,server.rooms().require("snake-1").metrics().players());
            await(()->!a.active()&&!b.active(),snake);assertEquals(1,server.connections());
        }
    }
    @Test void incompatibleGameAndProtocolRejectedWithoutWorld() throws Exception{
        var games=GameRegistry.builtIn();try(var server=NettyMultiGameServer.start("127.0.0.1",0,games,RoomHost.defaults(games),false);
            var bad=GameNetworkConnection.open("127.0.0.1",server.address().getPort(),"snake-1","snake","999","snake/1")){
            assertThrows(java.util.concurrent.ExecutionException.class,()->bad.ready().get(5,TimeUnit.SECONDS));assertEquals("GAME_VERSION",bad.failure());assertEquals(0,server.rooms().require("snake-1").metrics().players());
        }
    }
    @Test void disconnectAbortsSurvivorAndAllResourcesClose() throws Exception{
        var games=GameRegistry.builtIn();var server=NettyMultiGameServer.start("127.0.0.1",0,games,RoomHost.defaults(games),false);
        try(server;var a=connect(server,"tank");var b=connect(server,"tank")){await(()->a.latest()!=null&&a.latest().getStatus().equals("RUNNING"),a);b.closeAsync().join();await(()->a.latest().getStatus().equals("ABORTED"),a);assertEquals(1,server.rooms().require("tank-1").metrics().players());}
        assertTrue(server.terminated());
    }
    @Test void publicDevelopmentModeIsRejected(){assertThrows(IllegalArgumentException.class,()->NettyMultiGameServer.start("0.0.0.0",0,GameRegistry.builtIn(),RoomHost.defaults(GameRegistry.builtIn()),true));}
}
