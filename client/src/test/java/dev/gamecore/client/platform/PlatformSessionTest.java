package dev.gamecore.client.platform;
import com.sun.net.httpserver.HttpServer;
import dev.gamecore.platform.api.*;
import dev.gamecore.platform.api.PlatformMessages.*;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
/** 真实回环HTTP验证游客/注册登录/退出的内存生命周期，不启动图形或使用真实账号。 */
class PlatformSessionTest {
    @Test void startupDefaultsToLobbyAndSupportsExplicitOfflineMode(){String old=System.getProperty("gamecore.offline");var s=new PlatformSession(new PlatformEndpoint("",""));try{System.setProperty("gamecore.offline","false");assertTrue(s.autoOpen());System.setProperty("gamecore.offline","true");assertFalse(s.autoOpen());}finally{if(old==null){System.clearProperty("gamecore.offline");}else{System.setProperty("gamecore.offline",old);}s.closeAsync();}}
    @Test void guestBrowseRegisterLoginAndFailedLogoutClearOnlyAccountState() throws Exception {
        var calls=new AtomicInteger();var publicBearer=new AtomicReference<String>();var profile=new Profile("test-id","player01","玩家","USER");
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),8);
        server.createContext("/api/",e->{String path=e.getRequestURI().getPath();Object value;int status=200;calls.incrementAndGet();
            if(path.equals("/api/public/lobby")){publicBearer.set(e.getRequestHeaders().getFirst("Authorization"));value=new LobbyOverview(List.of(new Game("snake","1","snake/1",2,20)),List.of(new RoomSummary("r-"+"a".repeat(32),"snake","OPEN",1,2,0)));}
            else if(path.equals("/api/auth/register")){value=profile;}
            else if(path.equals("/api/auth/login")){value=new Login("temporary-token",123,profile);}
            else if(path.equals("/api/auth/logout")){assertEquals("Bearer temporary-token",e.getRequestHeaders().getFirst("Authorization"));status=503;value=new ApiError("DATABASE_UNAVAILABLE","test");}
            else{value=new ApiError("UNAUTHORIZED","test");status=401;}
            byte[] bytes=PlatformHttpClient.JSON.writeValueAsBytes(value);e.sendResponseHeaders(status,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}e.close();
        });server.start();
        var session=new PlatformSession(new PlatformEndpoint("http://127.0.0.1:"+server.getAddress().getPort(),""));
        try{assertNull(session.profile());assertEquals(0,calls.get());assertEquals(1,session.browse().rooms().size());assertNull(publicBearer.get());
            assertEquals(profile,session.login("player01","test-password-only",true));assertEquals(3,calls.get());session.room("test-room");
            assertThrows(PlatformHttpClient.Failure.class,session::logout);assertNull(session.profile());assertEquals("",session.room());assertEquals(1,session.browse().rooms().size());assertNull(publicBearer.get());
        }finally{session.closeAsync();server.stop(0);}assertThrows(IllegalStateException.class,session::client);assertThrows(IllegalStateException.class,()->session.configure("http://127.0.0.1:1"));
    }
    @Test void missingEndpointAndCloseDoNotFallBackToDeveloperOrCloudAddresses(){var s=new PlatformSession(new PlatformEndpoint("",""));assertThrows(IllegalStateException.class,s::client);assertTrue(s.configurationNotice().contains("暂时无法使用在线服务"));s.closeAsync();assertThrows(IllegalStateException.class,s::client);}
}
