package dev.gamecore.server.deployment;

import dev.gamecore.network.transport.*;
import dev.gamecore.online.*;
import dev.gamecore.protocol.GameProtocol;
import dev.gamecore.server.multigame.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** 部署垂直切片：真实JDK TLS、严格配置、健康隔离、双人权威同步、关闭通知和限额。 */
@SuppressWarnings("try") // 生命周期测试必须在断言前显式关闭，再验证幂等的资源收束。
class DeploymentTest {
    @TempDir Path dir;
    private DeploymentConfig config(String text,Map<String,String> env) throws Exception {var file=dir.resolve("server.properties");Files.writeString(file,text);return DeploymentConfig.load(file,env);}
    @Test void strictConfigEnvironmentAndGameTickValidation() throws Exception {
        var c=config("port=0\nhealthPort=0\n",Map.of("GAMECORE_MAX_ROOMS","6","GAMECORE_SNAPSHOT_RATE","8"));
        assertEquals(6,c.limits().maxRooms());assertEquals(8,c.limits().snapshotRate());assertEquals(3,c.rooms().size());
        assertEquals(30,c.limits().tickRates().get("fight"));
        for(String bad:List.of("port=1\nport=2","unknown=3","host=0.0.0.0","tls=yes","maxPlayersPerRoom=4","snapshotRate=61","snake.tickRate=30","maxRooms=1","maxPendingInputs=2\npendingPerConnection=3")){
            assertThrows(IllegalArgumentException.class,()->config(bad,Map.of()),bad);
        }
    }
    @Test void publicCredentialCannotDowngradeToPlaintext(){assertThrows(IllegalArgumentException.class,()->GameNetworkConnection.openTicket("example.com",7201,"tank-1","tank","1","tank/1","ticket",null));}
    @Test void interactiveSnapshotDefaultIsThirtyAndExplicitLowRateIsPreserved() throws Exception {
        assertEquals(30,config("",Map.of()).limits().snapshotRate());
        assertEquals(10,config("snapshotRate=10",Map.of()).limits().snapshotRate());
    }
    @Test void roomsAndInputQueuesHaveConfiguredBounds() throws Exception {
        var f=new dev.gamecore.online.tank.TankFactory();var limits=new ServerLimits(1,2,1,1,2,1,10,Map.of());
        try(var host=new RoomHost(GameRegistry.builtIn(),List.of(new RoomHost.Spec("tank-1","tank",f.defaults(),42)),1,false,s->new RoomActor.Observer(){},limits)){
            assertThrows(IllegalStateException.class,()->host.ensure(new RoomHost.Spec("tank-2","tank",f.defaults(),42)));
            host.destroy("tank-1").get(3,TimeUnit.SECONDS);assertEquals(0,host.scheduledRooms());
            for(int i=0;i<20;i++){String id="tank-"+i;host.ensure(new RoomHost.Spec(id,"tank",f.defaults(),42));host.destroy(id).get(3,TimeUnit.SECONDS);assertEquals(0,host.scheduledRooms());}
        }
        try(var room=new RoomActor("tank-1",f,f.defaults(),42,false,new RoomActor.Observer(){},limits)){
            var peer=new TestPeer();assertTrue(room.input(peer,GameProtocol.Input.getDefaultInstance()));assertFalse(room.input(peer,GameProtocol.Input.getDefaultInstance()));assertEquals(1,peer.pending.get());
            var second=new TestPeer();assertTrue(room.input(second,GameProtocol.Input.getDefaultInstance()));assertFalse(room.input(new TestPeer(),GameProtocol.Input.getDefaultInstance()));room.close();assertEquals(0,peer.pending.get());assertEquals(0,second.pending.get());
        }
    }
    private static final class TestPeer implements RoomPeer {
        final java.util.concurrent.atomic.AtomicInteger pending=new java.util.concurrent.atomic.AtomicInteger();
        public String id(){return "test";}public boolean active(){return true;}public java.util.concurrent.atomic.AtomicInteger pending(){return pending;}
        public void reliable(GameProtocol.Envelope value){}public void snapshot(GameProtocol.Envelope value){}public void disconnect(){}
    }
    private static void pem(Path file,String type,byte[] bytes) throws Exception {Files.writeString(file,"-----BEGIN "+type+"-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(bytes)+"\n-----END "+type+"-----\n");}
    private Path[] certificate(String san) throws Exception {
        var store=dir.resolve("server.p12");var java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool");
        var p=new ProcessBuilder(java.toString(),"-genkeypair","-alias","server","-keyalg","EC","-groupname","secp256r1","-validity","2","-dname","CN=GameCore local test","-ext","SAN="+san,"-storetype","PKCS12","-keystore",store.toString(),"-storepass","test-only-password","-noprompt").redirectErrorStream(true).redirectOutput(dir.resolve("keytool.txt").toFile()).start();
        assertTrue(p.waitFor(20,TimeUnit.SECONDS));assertEquals(0,p.exitValue());var ks=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(store)){ks.load(in,"test-only-password".toCharArray());}
        Path cert=dir.resolve("server.pem"),key=dir.resolve("server.key");pem(cert,"CERTIFICATE",ks.getCertificate("server").getEncoded());pem(key,"PRIVATE KEY",ks.getKey("server","test-only-password".toCharArray()).getEncoded());return new Path[]{cert,key};
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {long end=System.nanoTime()+5_000_000_000L;while(!condition.getAsBoolean()&&System.nanoTime()<end){java.util.concurrent.locks.LockSupport.parkNanos(2_000_000);}assertTrue(condition.getAsBoolean());}
    @Test void tlsTwoPlayersHealthSnapshotRateAndGracefulShutdown() throws Exception {
        var cert=certificate("DNS:localhost,IP:127.0.0.1");var games=GameRegistry.builtIn();var limits=new ServerLimits(3,4,1,1,64,16,5,Map.of());
        var server=NettyMultiGameServer.startConfigured("127.0.0.1",0,games,RoomHost.defaults(games),false,s->new RoomActor.Observer(){},null,limits,TransportTls.server(cert[0],cert[1]));
        try(server;var health=new HealthEndpoint(server,0);var a=secure(server,"127.0.0.1",cert[0]);var b=secure(server,"localhost",cert[0]);var http=HttpClient.newHttpClient()){
            a.ready().get(8,TimeUnit.SECONDS);b.ready().get(8,TimeUnit.SECONDS);await(()->a.latest()!=null&&a.latest().getStatus().equals("RUNNING"));
            await(server::healthy);String base="http://127.0.0.1:"+health.port();assertEquals(200,http.send(HttpRequest.newBuilder(URI.create(base+"/health")).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
            var room=server.rooms().require("tank-1");await(()->room.metrics().tick()>=15);assertTrue(room.metrics().snapshotCount()<=7,"Snapshot frequency is decoupled from 30 TPS");
            health.drain();assertEquals(503,http.send(HttpRequest.newBuilder(URI.create(base+"/health")).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
            server.close();GameProtocol.Envelope notice=null,result=null;for(GameProtocol.Envelope e;(e=a.poll())!=null;){if(e.hasError()&&e.getError().getCode().equals("SERVER_SHUTDOWN")){notice=e;}if(e.hasResult()){result=e;}}
            assertNotNull(notice);assertNotNull(result);assertEquals("ABORTED",result.getResult().getFinalSnapshot().getStatus());assertTrue(server.terminated());
        }
    }
    private static GameNetworkConnection secure(NettyMultiGameServer server,String host,Path ca){return GameNetworkConnection.openTicket(host,server.address().getPort(),"tank-1","tank","1","tank/1","",null,true,ca);}
    @Test void untrustedCertificateAndWrongHostnameAreRejected() throws Exception {
        var cert=certificate("DNS:wrong.example");var games=GameRegistry.builtIn();try(var server=NettyMultiGameServer.startConfigured("127.0.0.1",0,games,RoomHost.defaults(games),false,s->new RoomActor.Observer(){},null,ServerLimits.legacy(),TransportTls.server(cert[0],cert[1]));var wrong=secure(server,"127.0.0.1",cert[0]);var untrusted=secure(server,"127.0.0.1",null)){
            assertThrows(ExecutionException.class,()->wrong.ready().get(8,TimeUnit.SECONDS));assertThrows(ExecutionException.class,()->untrusted.ready().get(8,TimeUnit.SECONDS));await(()->server.connections()==0);assertEquals(0,server.rooms().require("tank-1").metrics().players());
        }
    }
    @Test void connectionCapAndPlaintextCannotBypassTls() throws Exception {
        var cert=certificate("DNS:localhost,IP:127.0.0.1");var games=GameRegistry.builtIn();var limits=new ServerLimits(3,2,1,1,64,16,10,Map.of());
        try(var server=NettyMultiGameServer.startConfigured("127.0.0.1",0,games,RoomHost.defaults(games),false,s->new RoomActor.Observer(){},null,limits,TransportTls.server(cert[0],cert[1]));var a=new java.net.Socket("127.0.0.1",server.address().getPort());var b=new java.net.Socket("127.0.0.1",server.address().getPort())){
            assertTrue(a.isConnected()&&b.isConnected());await(()->server.connections()==2);
            try(var excess=new java.net.Socket("127.0.0.1",server.address().getPort())){excess.setSoTimeout(2000);assertEquals(-1,excess.getInputStream().read());}
            assertEquals(2,server.connections());a.getOutputStream().write(new byte[]{0,0,0,5,8,2,16,1,0});a.setSoTimeout(2000);assertEquals(-1,a.getInputStream().read());await(()->server.connections()==1);
        }
    }
}
