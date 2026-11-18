package dev.gamecore.client.network;

import dev.gamecore.online.GameRegistry;
import dev.gamecore.online.tank.TankFactory;
import dev.gamecore.server.multigame.*;
import dev.gamecore.tank.TankCommand;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 只在回环测试中继上施加每方向80..100ms延迟，验证新时钟在实际TCP/Room中执行。 */
class ArenaLatencyIntegrationTest {
    @Test void waitingSnapshotIsDecodedOnceAcrossOneHundredRenderUpdates() throws Exception {
        var base=new TankFactory();var decoded=new java.util.concurrent.atomic.AtomicInteger();
        var codec=new dev.gamecore.api.online.GameSnapshotCodec<dev.gamecore.tank.TankSnapshot>(){
            public byte[] encode(dev.gamecore.tank.TankSnapshot s){return base.snapshots().encode(s);}
            public dev.gamecore.tank.TankSnapshot decode(byte[] bytes){decoded.incrementAndGet();return base.snapshots().decode(bytes);}
            public String outcome(dev.gamecore.tank.TankSnapshot s){return base.snapshots().outcome(s);}
            public int entityCount(dev.gamecore.tank.TankSnapshot s){return base.snapshots().entityCount(s);}
        };
        var factory=new dev.gamecore.api.online.GameFactory<TankCommand,dev.gamecore.tank.TankSnapshot>(){
            public dev.gamecore.api.online.GameType type(){return base.type();}
            public dev.gamecore.api.online.GameRuleConfig defaults(){return base.defaults();}
            public void validate(dev.gamecore.api.online.GameRuleConfig c){base.validate(c);}
            public dev.gamecore.api.online.GameSessionFactory<TankCommand,dev.gamecore.tank.TankSnapshot> sessions(){return base.sessions();}
            public dev.gamecore.api.online.GameInputCodec<TankCommand> inputs(){return base.inputs();}
            public dev.gamecore.api.online.GameSnapshotCodec<dev.gamecore.tank.TankSnapshot> snapshots(){return codec;}
            public TankCommand bot(dev.gamecore.tank.TankSnapshot s,int slot,long step,boolean active){return base.bot(s,slot,step,active);}
        };
        var games=GameRegistry.builtIn();
        try(var server=NettyMultiGameServer.start("127.0.0.1",0,games,RoomHost.defaults(games),false);
            var session=new RemoteArenaSession(factory,"127.0.0.1",server.address().getPort(),"tank-1")){
            session.network().ready().get(5,TimeUnit.SECONDS);long until=System.nanoTime()+2_000_000_000L;
            while(session.snapshot()==null&&System.nanoTime()<until){session.update(()->null,System.nanoTime());Thread.sleep(2);}
            assertNotNull(session.snapshot());var first=session.snapshot();
            for(int frame=0;frame<100;frame++){session.update(()->null,System.nanoTime());}
            assertSame(first,session.snapshot());assertEquals(1,decoded.get());
        }
    }
    @Test void jitteredTcpExecutesInputsAndMeasuresActualSnapshotCadence() throws Exception {
        var games=GameRegistry.builtIn();var f=new TankFactory();var limits=new ServerLimits(3,4,1,1,64,16,30,Map.of());
        try(var server=NettyMultiGameServer.startConfigured("127.0.0.1",0,games,RoomHost.defaults(games),false,s->new RoomActor.Observer(){},null,limits,null);
            var relay=new DelayedTcpRelay(server.address().getPort(),80,20);
            var a=new RemoteArenaSession(f,"127.0.0.1",relay.port(),"tank-1");
            var b=new RemoteArenaSession(f,"127.0.0.1",relay.port(),"tank-1")){
            a.network().ready().get(5,TimeUnit.SECONDS);b.network().ready().get(5,TimeUnit.SECONDS);
            long until=System.nanoTime()+3_000_000_000L;
            while(System.nanoTime()<until){
                a.update(()->f.inputs().encode(new TankCommand(1,0,false)),System.nanoTime());
                b.update(()->f.inputs().encode(TankCommand.IDLE),System.nanoTime());Thread.sleep(16);
            }
            assertEquals("RUNNING",a.status());long executed=a.snapshot().getAcks(a.network().joined().getPlayerSlot()).getExecuted();
            assertTrue(executed>60);
            // 真TCP/OS调度可产生偶发迟到；要求自适应后持续执行，不能用“永远零拒绝”掩盖统计。
            assertTrue(a.rejectedInputs()<executed/5,"Late feedback must not cause sustained rejection: "+a.diagnostics(System.nanoTime()));
            assertTrue(a.network().snapshotRate()>25&&a.network().snapshotRate()<35);
            assertTrue(a.network().rttNanos()>=150_000_000L);assertTrue(a.diagnostics(System.nanoTime()).contains("输入拒绝 "+a.rejectedInputs()));
            var received=a.network().receivedSnapshot();assertNotNull(received);assertTrue(received.arrivalNanos()>0);assertEquals(a.network().latest(),received.value());
            System.out.println("TCP_JITTER rttMs="+a.network().rttNanos()/1_000_000+" snapshotsPerSecond="+a.network().snapshotRate()+" executed="+executed+" rejectedCount="+a.rejectedInputs()+" lastError="+a.inputError());
        }
    }
}
