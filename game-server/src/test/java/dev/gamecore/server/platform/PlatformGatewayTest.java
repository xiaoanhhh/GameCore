package dev.gamecore.server.platform;
import com.sun.net.httpserver.HttpServer;
import dev.gamecore.online.GameRegistry;
import dev.gamecore.platform.api.*;
import dev.gamecore.platform.api.PlatformMessages.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
/** 真实HTTP故障/磁盘重启重发；数据库真实性另由platform-service的PostgreSQL集成测试验证。 */
class PlatformGatewayTest {
    @TempDir Path directory;
    private static void await(BooleanSupplier test) throws Exception {long end=System.nanoTime()+8_000_000_000L;while(!test.getAsBoolean()&&System.nanoTime()<end){new CountDownLatch(1).await(20,TimeUnit.MILLISECONDS);}assertTrue(test.getAsBoolean());}
    @Test void pendingResultSurvivesGatewayRestartAndRetriesWithoutCredentialsOnDisk() throws Exception {
        String id=UUID.randomUUID().toString(),key="test-only-random-"+UUID.randomUUID();Finish value=new Finish(id,"RULES","semantic-state");Path file=directory.resolve(id+".pending");Files.write(file,PlatformHttpClient.JSON.writeValueAsBytes(value));assertFalse(Files.readString(file).contains(key));
        var failing=new AtomicBoolean(true);var attempts=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),8);
        server.createContext("/api/internal/results",e->{attempts.incrementAndGet();assertEquals(key,e.getRequestHeaders().getFirst("X-Game-Server-Key"));assertEquals(value,PlatformHttpClient.JSON.readValue(e.getRequestBody().readAllBytes(),Finish.class));
            byte[] reply=PlatformHttpClient.JSON.writeValueAsBytes(failing.get()?new ApiError("DATABASE_UNAVAILABLE","safe"):new Match(id,"snake","FINISHED","DRAW",100,0,List.of()));e.sendResponseHeaders(failing.get()?503:200,reply.length);try(var out=e.getResponseBody()){out.write(reply);}});server.start();
        try {String url="http://127.0.0.1:"+server.getAddress().getPort();PlatformGateway first=new PlatformGateway(url,key,directory,GameRegistry.builtIn());try{await(()->attempts.get()>0);assertEquals(1,first.queued());}finally{first.close();}assertTrue(first.terminated());assertTrue(Files.exists(file));
            failing.set(false);PlatformGateway second=new PlatformGateway(url,key,directory,GameRegistry.builtIn());try{await(()->second.delivered()==1);assertEquals(0,second.queued());assertFalse(Files.exists(file));}finally{second.close();}assertTrue(second.terminated());
        }finally{server.stop(0);}
    }
    @Test void startupRejectsOverCapacityBeforeStartingHttpThreads() throws Exception {for(int i=0;i<129;i++){Files.writeString(directory.resolve(UUID.randomUUID()+".pending"),"{}");}assertThrows(java.io.IOException.class,()->new PlatformGateway("http://127.0.0.1:1","test-only-long-key-"+UUID.randomUUID(),directory,GameRegistry.builtIn()));}
}
