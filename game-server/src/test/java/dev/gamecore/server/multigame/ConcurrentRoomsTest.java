package dev.gamecore.server.multigame;
import dev.gamecore.network.transport.GameNetworkConnection;
import dev.gamecore.network.transport.IoResources;
import dev.gamecore.online.GameRegistry;
import dev.gamecore.online.tank.TankFactory;
import dev.gamecore.protocol.GameProtocol;
import dev.gamecore.server.devtools.DevToolsMain;
import dev.gamecore.tank.TankCommand;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import static org.junit.jupiter.api.Assertions.*;

/** 服务器并发场景：抢座、多房同时运行、单连接洪泛、运行中关闭。全部走真实回环 TCP 和随机端口。 */
class ConcurrentRoomsTest {
    private static GameNetworkConnection open(NettyMultiGameServer server,String room,String game){
        var type=GameRegistry.builtIn().require(game).type();
        return GameNetworkConnection.open("127.0.0.1",server.address().getPort(),room,game,type.gameVersion(),type.rulesVersion());
    }
    private static void settle(GameNetworkConnection connection) throws Exception{
        try{connection.ready().get(8,TimeUnit.SECONDS);}catch(ExecutionException refused){assertFalse(connection.failure().isEmpty());}
    }
    private static void until(java.util.function.BooleanSupplier done,String what) throws Exception{
        long end=System.nanoTime()+8_000_000_000L;while(!done.getAsBoolean()&&System.nanoTime()<end){new CountDownLatch(1).await(10,TimeUnit.MILLISECONDS);}
        assertTrue(done.getAsBoolean(),what);
    }

    @Test void sixteenClientsRacingForTwoSeatsLeaveExactlyTwoPlayers() throws Exception{
        var games=GameRegistry.builtIn();var server=NettyMultiGameServer.start("127.0.0.1",0,games,RoomHost.defaults(games),false);
        var connections=new ArrayList<GameNetworkConnection>();
        try(server;var pool=Executors.newFixedThreadPool(16)){
            var gate=new CountDownLatch(1);var tasks=new ArrayList<Future<GameNetworkConnection>>();
            for(int i=0;i<16;i++){tasks.add(pool.submit(()->{gate.await();return open(server,"tank-1","tank");}));}
            gate.countDown();for(var task:tasks){connections.add(task.get(8,TimeUnit.SECONDS));}
            for(var connection:connections){settle(connection);}
            var joined=connections.stream().filter(c->c.joined()!=null).toList();
            assertEquals(2,joined.size());assertEquals(2,server.rooms().require("tank-1").metrics().players());
            assertEquals(2,joined.stream().map(c->c.joined().getPlayerSlot()).distinct().count());
            assertEquals(Set.of(joined.get(0).joined().getMatchId()),Set.of(joined.get(1).joined().getMatchId()));
            for(var connection:connections){if(connection.joined()==null){assertEquals("ROOM_FULL",connection.failure());}}
        }finally{for(var connection:connections){connection.closeAsync().join();}}
        assertTrue(server.terminated());assertEquals(0,server.connections());
    }

    @Test void thirtyConcurrentRoomsKeepPairwiseIdenticalAuthority() throws Exception{
        var games=GameRegistry.builtIn();var specs=DevToolsMain.specs(games,"all",30,2,true);var io=IoResources.group(2,"concurrent-test");
        var server=NettyMultiGameServer.start("127.0.0.1",0,games,specs,false);var clients=new LinkedHashMap<HeadlessGameClient,RoomHost.Spec>();
        var seen=new IdentityHashMap<HeadlessGameClient,TreeMap<Long,com.google.protobuf.ByteString>>();
        try(server){
            for(var spec:specs){for(int i=0;i<2;i++){var client=new HeadlessGameClient("127.0.0.1",server.address().getPort(),spec.id(),games.require(spec.game()),io);clients.put(client,spec);seen.put(client,new TreeMap<>());}}
            for(var client:clients.keySet()){client.network().ready().get(8,TimeUnit.SECONDS);}
            long end=System.nanoTime()+4_000_000_000L;
            while(System.nanoTime()<end){
                for(var client:clients.keySet()){client.step(true);var snapshot=client.network().latest();if(snapshot!=null){seen.get(client).put(snapshot.getServerTick(),snapshot.getStateHash());}}
                new CountDownLatch(1).await(20,TimeUnit.MILLISECONDS);
            }
            var byRoom=new HashMap<String,List<HeadlessGameClient>>();clients.forEach((client,spec)->byRoom.computeIfAbsent(spec.id(),k->new ArrayList<>()).add(client));
            assertEquals(30,byRoom.size());
            for(var pair:byRoom.entrySet()){
                var a=seen.get(pair.getValue().get(0));var b=seen.get(pair.getValue().get(1));
                var common=new TreeSet<>(a.keySet());common.retainAll(b.keySet());assertFalse(common.isEmpty(),pair.getKey()+" 没有共同 Tick");
                for(long tick:common){assertEquals(a.get(tick),b.get(tick),pair.getKey()+" 第 "+tick+" 步出现分歧");}
                assertTrue(pair.getValue().stream().allMatch(c->c.network().failure().isEmpty()),pair.getKey()+" 连接失败");
            }
        }finally{for(var client:clients.keySet()){client.network().closeAsync().join();}IoResources.stop(io);}
        assertTrue(server.terminated());
    }

    @Test void floodingClientIsClosedWithoutStallingOtherRooms() throws Exception{
        var games=GameRegistry.builtIn();var server=NettyMultiGameServer.start("127.0.0.1",0,games,DevToolsMain.specs(games,"tank",2,2,false),false);
        try(server;var flood=open(server,"tank-1","tank");var peer=open(server,"tank-1","tank");var c=open(server,"tank-2","tank");var d=open(server,"tank-2","tank")){
            for(var connection:List.of(flood,peer,c,d)){connection.ready().get(8,TimeUnit.SECONDS);}
            until(()->flood.latest()!=null&&c.latest()!=null,"比赛没有开始");
            var victim=server.rooms().require("tank-2");long before=victim.metrics().tick();
            byte[] command=new TankFactory().inputs().encode(new TankCommand(1,0,false));long target=flood.latest().getServerTick()+2;
            // Windows 的 parkNanos 精度约 1~15ms，会让发送速度低于限流线，这里用忙等保证一秒内超过 120 条。
            // 边发边读事件：客户端邮箱只有 64 格，发完再读时逐条拒绝可能先把它填满，触发 EVENT_OVERFLOW。
            String code=null;long end=System.nanoTime()+5_000_000_000L;int sent=0;
            while(code==null&&flood.failure().isEmpty()&&System.nanoTime()<end){
                if(sent<200&&flood.active()){flood.input(++sent,target,command);long due=System.nanoTime()+200_000;while(System.nanoTime()<due){Thread.onSpinWait();}}
                GameProtocol.Envelope event;while((event=flood.poll())!=null){if(event.hasError()&&event.getError().getCode().equals("INPUT_RATE")){code="INPUT_RATE";}}
                if(sent>=200){var next=flood.receive(Duration.ofMillis(20));if(next!=null&&next.hasError()&&next.getError().getCode().equals("INPUT_RATE")){code="INPUT_RATE";}}
            }
            // 正常负载下由服务器限流关闭；CPU 饱和时发送方的 128 格队列可能先满并主动断开，两者都是有界保护。
            assertTrue("INPUT_RATE".equals(code)||flood.failure().equals("SEND_OVERFLOW"),"sent="+sent+" failure="+flood.failure()+" active="+flood.active());until(()->!flood.active(),"洪泛连接没有被关闭");
            new CountDownLatch(1).await(1,TimeUnit.SECONDS);
            assertTrue(victim.metrics().tick()-before>=20,"其他房间被拖慢");assertEquals("RUNNING",victim.metrics().status());assertTrue(c.active()&&d.active());
        }
    }

    @Test void serverClosesCleanlyWhileTwentyClientsAreSendingInput() throws Exception{
        var games=GameRegistry.builtIn();var specs=DevToolsMain.specs(games,"all",10,2,false);var io=IoResources.group(2,"concurrent-close");
        var server=NettyMultiGameServer.start("127.0.0.1",0,games,specs,false);var clients=new ArrayList<HeadlessGameClient>();
        try{
            for(var spec:specs){for(int i=0;i<2;i++){clients.add(new HeadlessGameClient("127.0.0.1",server.address().getPort(),spec.id(),games.require(spec.game()),io));}}
            for(var client:clients){client.network().ready().get(8,TimeUnit.SECONDS);}
            var stop=new java.util.concurrent.atomic.AtomicBoolean();
            var sender=new Thread(()->{while(!stop.get()){for(var client:clients){client.step(true);}LockSupport.parkNanos(10_000_000);}},"concurrent-close-sender");sender.start();
            new CountDownLatch(1).await(500,TimeUnit.MILLISECONDS);
            long start=System.nanoTime();server.close();stop.set(true);sender.join(3000);
            assertTrue((System.nanoTime()-start)/1_000_000_000d<10,"关闭耗时过长");assertTrue(server.terminated());assertEquals(0,server.connections());
            until(()->clients.stream().noneMatch(c->c.network().active()),"客户端连接没有断开");
        }finally{for(var client:clients){client.network().closeAsync().join();}server.close();IoResources.stop(io);}
    }
}
