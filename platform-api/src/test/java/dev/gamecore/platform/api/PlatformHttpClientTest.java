package dev.gamecore.platform.api;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class PlatformHttpClientTest {
    private record Exchange(String method,String path,int status,String code,long nanos){}
    @Test void observerSeesOnlyMethodPathStatusAndCode() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),8);var seen=new java.util.concurrent.CopyOnWriteArrayList<Exchange>();
        server.createContext("/api/ok",e->{e.getRequestBody().readAllBytes();byte[] b="[\"response-body\"]".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(201,b.length);try(var out=e.getResponseBody()){out.write(b);}});
        server.createContext("/api/fail",e->{byte[] b="{\"code\":\"CONFLICT\",\"message\":\"safe\"}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(409,b.length);try(var out=e.getResponseBody()){out.write(b);}});
        server.createContext("/api/bad",e->{byte[] b="not-json".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);try(var out=e.getResponseBody()){out.write(b);}});
        server.start();
        try(var c=new PlatformHttpClient("http://127.0.0.1:"+server.getAddress().getPort())){
            c.token("sentinel-token");c.observer((method,path,status,code,nanos)->seen.add(new Exchange(method,path,status,code,nanos)));
            c.call("POST","/api/ok",java.util.Map.of("password","sentinel-password"),Object[].class);
            assertThrows(PlatformHttpClient.Failure.class,()->c.call("GET","/api/fail",null,Object.class));
            assertThrows(RuntimeException.class,()->c.call("GET","/api/bad",null,Object[].class));
            assertEquals(3,seen.size());
            assertEquals("POST /api/ok 201 ",seen.get(0).method()+" "+seen.get(0).path()+" "+seen.get(0).status()+" "+seen.get(0).code());
            assertTrue(seen.get(0).nanos()>0);
            assertEquals("GET /api/fail 409 CONFLICT",seen.get(1).method()+" "+seen.get(1).path()+" "+seen.get(1).status()+" "+seen.get(1).code());
            assertEquals(200,seen.get(2).status());assertEquals("INVALID_RESPONSE",seen.get(2).code());
            assertFalse(seen.toString().contains("sentinel"));assertFalse(seen.toString().contains("response-body"));
        }finally{server.stop(0);}
    }
    @Test void unreachableServerAndFailingObserverKeepTheOriginalResult() throws Exception {
        int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
        var seen=new java.util.concurrent.CopyOnWriteArrayList<Exchange>();
        try(var c=new PlatformHttpClient("http://127.0.0.1:"+port)){
            c.observer((method,path,status,code,nanos)->seen.add(new Exchange(method,path,status,code,nanos)));
            var failure=assertThrows(PlatformHttpClient.Failure.class,()->c.call("GET","/api/down",null,Object.class));
            assertEquals("PLATFORM_UNAVAILABLE",failure.getMessage());assertEquals(503,seen.getFirst().status());
            c.observer((method,path,status,code,nanos)->{throw new IllegalStateException("observer");});
            assertEquals("PLATFORM_UNAVAILABLE",assertThrows(PlatformHttpClient.Failure.class,()->c.call("GET","/api/down",null,Object.class)).getMessage());
        }
    }
    @Test void publicLobbyValuesRejectMalformedUnboundedOrPrivateDataShapes(){
        assertThrows(IllegalArgumentException.class,()->new PlatformMessages.RoomSummary("bad","tank","OPEN",1,2,0));
        assertThrows(IllegalArgumentException.class,()->new PlatformMessages.RoomSummary("r-"+"a".repeat(32),"tank","OPEN",3,2,0));
        var room=new PlatformMessages.RoomSummary("r-"+"a".repeat(32),"tank","OPEN",1,2,0);
        assertThrows(IllegalArgumentException.class,()->new PlatformMessages.LobbyOverview(java.util.List.of(),java.util.Collections.nCopies(129,room)));
        var list=new java.util.ArrayList<>(java.util.List.of(room));var data=new PlatformMessages.LobbyOverview(java.util.List.of(),list);list.clear();assertEquals(1,data.rooms().size());
        assertThrows(IllegalArgumentException.class,()->PlatformHttpClient.endpoint("//example.com"));assertThrows(IllegalArgumentException.class,()->PlatformHttpClient.endpoint("https://example.com:65536"));
    }
    @org.junit.jupiter.api.Test void ticketTlsIsAdditiveAndSecretStillRedacted(){
        var old=PlatformHttpClient.JSON.readValue("{\"host\":\"localhost\",\"port\":7201,\"roomId\":\"r\",\"gameId\":\"tank\",\"gameVersion\":\"1\",\"rulesVersion\":\"tank/1\",\"matchId\":\"m\",\"slot\":0,\"credential\":\"hidden\",\"expiresAt\":1}",PlatformMessages.Ticket.class);
        org.junit.jupiter.api.Assertions.assertFalse(old.tls());var secure=new PlatformMessages.Ticket(old.host(),old.port(),old.roomId(),old.gameId(),old.gameVersion(),old.rulesVersion(),old.matchId(),old.slot(),old.credential(),old.expiresAt(),true);
        org.junit.jupiter.api.Assertions.assertTrue(PlatformHttpClient.JSON.readValue(PlatformHttpClient.JSON.writeValueAsString(secure),PlatformMessages.Ticket.class).tls());org.junit.jupiter.api.Assertions.assertFalse(secure.toString().contains("hidden"));
    }
    @Test void publicHttpAndCredentialUrlsAreRejected(){for(String url:new String[]{"http://example.com","https://user:secret@example.com","https://example.com/redirect","file:///tmp/a"}){assertThrows(IllegalArgumentException.class,()->new PlatformHttpClient(url));}try(var c=new PlatformHttpClient("https://example.com")){assertThrows(IllegalArgumentException.class,()->c.call("GET","/../secrets",null,Object.class));}}
    @Test void sensitiveRecordsDoNotPrintSecrets(){String secret="not-for-logs";assertFalse(new PlatformMessages.Credentials("alice",secret).toString().contains(secret));assertFalse(new PlatformMessages.Login(secret,0,null).toString().contains(secret));assertFalse(new PlatformMessages.Redeem(secret,"id").toString().contains(secret));var copy=PlatformHttpClient.JSON.readValue(PlatformHttpClient.JSON.writeValueAsBytes(new PlatformMessages.Credentials("alice",secret)),PlatformMessages.Credentials.class);assertEquals(secret,copy.password());}
    @Test void responseIsBoundedAndErrorsAreStable() throws Exception {var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),8);
        server.createContext("/api/big",e->{byte[] b=new byte[300000];e.sendResponseHeaders(200,b.length);try(var out=e.getResponseBody()){out.write(b);}catch(java.io.IOException ignored){ }});
        server.createContext("/api/fail",e->{byte[] b="{\"code\":\"CONFLICT\",\"message\":\"safe\"}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(409,b.length);try(var out=e.getResponseBody()){out.write(b);}});server.start();
        try(var c=new PlatformHttpClient("http://127.0.0.1:"+server.getAddress().getPort())){assertThrows(PlatformHttpClient.Failure.class,()->c.call("GET","/api/big",null,Object.class));var error=assertThrows(PlatformHttpClient.Failure.class,()->c.call("GET","/api/fail",null,Object.class));assertEquals(409,error.status());assertEquals("CONFLICT",error.getMessage());}finally{server.stop(0);}}
    @Test void stalledBodyTimesOutBeforeUiWorkerCanHang() throws Exception {var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),8);var release=new CountDownLatch(1);var worker=Executors.newSingleThreadExecutor();server.setExecutor(worker);
        server.createContext("/api/stall",e->{e.sendResponseHeaders(200,10);e.getResponseBody().write(1);e.getResponseBody().flush();try{release.await(8,TimeUnit.SECONDS);}catch(InterruptedException x){Thread.currentThread().interrupt();}e.close();});server.start();long before=System.nanoTime();
        try(var c=new PlatformHttpClient("http://127.0.0.1:"+server.getAddress().getPort())){assertThrows(PlatformHttpClient.Failure.class,()->c.call("GET","/api/stall",null,Object.class));assertTrue(System.nanoTime()-before<7_000_000_000L);}finally{release.countDown();server.stop(0);worker.shutdownNow();}}
}
