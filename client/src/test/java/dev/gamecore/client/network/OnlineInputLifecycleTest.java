package dev.gamecore.client.network;

import dev.gamecore.online.GameRegistry;
import dev.gamecore.online.tank.TankFactory;
import dev.gamecore.protocol.GameProtocol;
import dev.gamecore.server.multigame.*;
import dev.gamecore.tank.TankCommand;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 真 TCP 验证清键早于新世代采样；暂停时残留的移动不能在恢复时被重新盖上新 epoch。 */
class OnlineInputLifecycleTest {
    @Test void inputLatchIsClearedBeforeResumeSampling() throws Exception{
        var games=GameRegistry.builtIn();var f=new TankFactory();
        try(var server=NettyMultiGameServer.start("127.0.0.1",0,games,RoomHost.defaults(games),false);
            var a=new RemoteArenaSession(f,"127.0.0.1",server.address().getPort(),"tank-1","",false,true);
            var b=new RemoteArenaSession(f,"127.0.0.1",server.address().getPort(),"tank-1","",false,true)){
            var held=new AtomicBoolean();var clears=new AtomicInteger();
            Runnable update=()->{a.update(()->f.inputs().encode(held.get()?new TankCommand(1,0,false):TankCommand.IDLE),System.nanoTime(),()->{held.set(false);clears.incrementAndGet();});b.update(()->f.inputs().encode(TankCommand.IDLE),System.nanoTime());};
            await(update,()->a.roomState()!=null&&b.roomState()!=null&&a.status().equals("SETUP")&&b.status().equals("SETUP"));
            a.control(GameProtocol.RoomAction.READY);b.control(GameProtocol.RoomAction.READY);await(update,()->a.status().equals("RUNNING")&&b.status().equals("RUNNING"));
            a.control(GameProtocol.RoomAction.PAUSE);await(update,()->a.status().equals("PAUSED")&&b.status().equals("PAUSED"));
            var before=f.snapshots().decode(a.snapshot().getState().toByteArray()).tanks().get(a.network().joined().getPlayerSlot()).position();
            held.set(true);a.control(GameProtocol.RoomAction.RESUME);b.control(GameProtocol.RoomAction.RESUME);
            await(update,()->a.status().equals("RUNNING")&&b.status().equals("RUNNING"));
            long until=System.nanoTime()+150_000_000L;while(System.nanoTime()<until){update.run();Thread.sleep(3);}
            assertFalse(held.get());assertTrue(clears.get()>=4);
            assertEquals(before,f.snapshots().decode(a.snapshot().getState().toByteArray()).tanks().get(a.network().joined().getPlayerSlot()).position());
            a.network().closeAsync().get(5,java.util.concurrent.TimeUnit.SECONDS);b.network().closeAsync().get(5,java.util.concurrent.TimeUnit.SECONDS);
        }
    }
    private static void await(Runnable update,java.util.function.BooleanSupplier done) throws Exception{
        long end=System.nanoTime()+5_000_000_000L;while(!done.getAsBoolean()&&System.nanoTime()<end){update.run();Thread.sleep(3);}assertTrue(done.getAsBoolean());
    }
}
