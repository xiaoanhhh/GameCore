package dev.gamecore.client.network;
import dev.gamecore.online.GameRegistry;
import dev.gamecore.online.snake.SnakeFactory;
import dev.gamecore.server.multigame.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
/** 无图形验证页面背后的会话生命周期；图形绘制另由桌面冒烟覆盖。 */
class RemoteArenaTest {
    private static void pump(RemoteArenaSession r,java.util.function.BooleanSupplier condition) throws Exception{
        long end=System.nanoTime()+4_000_000_000L;while(!condition.getAsBoolean()&&System.nanoTime()<end){r.update(()->null,System.nanoTime());r.network().receive(Duration.ofMillis(5));}assertTrue(condition.getAsBoolean(),r.notice());
    }
    @Test void repeatedEnterExitAndDisconnectPreserveRoomResources() throws Exception{
        var games=GameRegistry.builtIn();try(var server=NettyMultiGameServer.start("127.0.0.1",0,games,RoomHost.defaults(games),false)){
            for(int i=0;i<4;i++){
                var r=new RemoteArenaSession(new SnakeFactory(),"127.0.0.1",server.address().getPort(),"snake-1");r.network().ready().get(4,TimeUnit.SECONDS);pump(r,()->r.snapshot()!=null);assertEquals("WAITING",r.status());r.close();r.network().closeAsync().get(4,TimeUnit.SECONDS);
                long end=System.nanoTime()+2_000_000_000L;while(server.rooms().require("snake-1").metrics().players()!=0&&System.nanoTime()<end){java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);}
                assertEquals(0,server.rooms().require("snake-1").metrics().players());assertEquals(0,server.rooms().require("snake-1").metrics().queued());
            }
        }
    }
    @Test void failureIsFriendlyAndDoesNotThrowIntoRenderLoop() throws Exception{
        var games=GameRegistry.builtIn();var server=NettyMultiGameServer.start("127.0.0.1",0,games,RoomHost.defaults(games),false);
        var late=new java.util.concurrent.atomic.AtomicReference<java.util.logging.LogRecord>();var logger=java.util.logging.Logger.getLogger("io.netty.util.concurrent.DefaultPromise");
        var handler=new java.util.logging.Handler(){@Override public void publish(java.util.logging.LogRecord r){if(r.getLevel().intValue()>=java.util.logging.Level.SEVERE.intValue()){late.set(r);}}@Override public void flush(){}@Override public void close(){}};logger.addHandler(handler);
        try(var r=new RemoteArenaSession(new SnakeFactory(),"127.0.0.1",server.address().getPort(),"missing")){
            pump(r,()->r.terminal());assertEquals("DISCONNECTED",r.status());assertTrue(r.notice().contains("返回大厅"));
            r.network().termination().get(4,TimeUnit.SECONDS);r.network().closeAsync().get(4,TimeUnit.SECONDS);assertNull(late.get(),"Late close must not schedule onto a terminated EventLoop");
        }finally{logger.removeHandler(handler);server.close();}assertTrue(server.terminated());
    }
    @Test void realAuthorityMovesAfterRttUpdatesWithoutRenderThreadCallbacks() throws Exception{
        var games=GameRegistry.builtIn();var f=new dev.gamecore.online.tank.TankFactory();try(var server=NettyMultiGameServer.start("127.0.0.1",0,games,RoomHost.defaults(games),false);
            var a=new RemoteArenaSession(f,"127.0.0.1",server.address().getPort(),"tank-1");var b=new RemoteArenaSession(f,"127.0.0.1",server.address().getPort(),"tank-1")){
            a.network().ready().get(4,TimeUnit.SECONDS);b.network().ready().get(4,TimeUnit.SECONDS);long until=System.nanoTime()+2_200_000_000L;
            while(System.nanoTime()<until){a.update(()->f.inputs().encode(new dev.gamecore.tank.TankCommand(1,0,false)),System.nanoTime());b.update(()->f.inputs().encode(dev.gamecore.tank.TankCommand.IDLE),System.nanoTime());a.network().receive(Duration.ofMillis(5));}
            int slot=a.network().joined().getPlayerSlot();assertEquals("RUNNING",a.status());assertTrue(a.network().rttNanos()>0);assertNotNull(a.snapshot());assertTrue(a.snapshot().getAcks(slot).getExecuted()>0);assertNotEquals(dev.gamecore.online.tank.TankFactory.map("crossfire").spawns().get(slot).position(),f.snapshots().decode(a.snapshot().getState().toByteArray()).tanks().get(slot).position());
        }
    }
    @Test void finalOutcomeSurvivesRepeatedSnapshotConsumption() throws Exception{
        var games=GameRegistry.builtIn();var f=new SnakeFactory();try(var server=NettyMultiGameServer.start("127.0.0.1",0,games,RoomHost.defaults(games),false);
            var a=new RemoteArenaSession(f,"127.0.0.1",server.address().getPort(),"snake-1");var b=new RemoteArenaSession(f,"127.0.0.1",server.address().getPort(),"snake-1")){
            a.network().ready().get(4,TimeUnit.SECONDS);b.network().ready().get(4,TimeUnit.SECONDS);long end=System.nanoTime()+5_000_000_000L;
            while((!a.status().equals("FINISHED")||!b.status().equals("FINISHED"))&&System.nanoTime()<end){a.update(()->null,System.nanoTime());b.update(()->null,System.nanoTime());java.util.concurrent.locks.LockSupport.parkNanos(2_000_000);}
            assertEquals("FINISHED",a.status());for(int i=0;i<5;i++){a.update(()->null,System.nanoTime());}assertTrue(a.notice().contains("平局"));assertEquals(a.snapshot(),b.snapshot());
        }
    }
    @Test void fullRttLeadAllowsInputsThroughTwoHundredMillisecondTcpRelay() throws Exception{
        var games=GameRegistry.builtIn();var f=new dev.gamecore.online.tank.TankFactory();try(var server=NettyMultiGameServer.start("127.0.0.1",0,games,RoomHost.defaults(games),false);
            var relay=new DelayedTcpRelay(server.address().getPort());
            var a=new RemoteArenaSession(f,"127.0.0.1",relay.port(),"tank-1");var b=new RemoteArenaSession(f,"127.0.0.1",relay.port(),"tank-1")){
            a.network().ready().get(4,TimeUnit.SECONDS);b.network().ready().get(4,TimeUnit.SECONDS);long end=System.nanoTime()+5_000_000_000L;
            while(System.nanoTime()<end){a.update(()->f.inputs().encode(new dev.gamecore.tank.TankCommand(1,0,false)),System.nanoTime());b.update(()->f.inputs().encode(dev.gamecore.tank.TankCommand.IDLE),System.nanoTime());a.network().receive(Duration.ofMillis(5));}
            assertEquals("RUNNING",a.status());assertTrue(a.network().rttNanos()>=150_000_000L);assertTrue(a.snapshot().getAcks(a.network().joined().getPlayerSlot()).getExecuted()>30,"Authority must execute sustained inputs at measured RTT");
        }
    }
}
