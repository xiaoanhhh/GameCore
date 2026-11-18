package dev.gamecore.lwjgl3;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.sun.net.httpserver.HttpServer;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.screen.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/** 本机真实窗口与HTTP故障场景：启动即游客大厅、无地址输入、恢复/空列表/坏消息/反复离页。 */
public final class PlatformLobbySmokeTest {
    private PlatformLobbySmokeTest() { }
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args[0]).toAbsolutePath();Files.createDirectories(out);int port;try(var socket=new java.net.ServerSocket(0,8,InetAddress.getLoopbackAddress())){port=socket.getLocalPort();}
        System.setProperty("gamecore.platform.url","http://127.0.0.1:"+port);
        var probe=new Probe(out,port);var config=Lwjgl3Launcher.configuration(DesktopSettings.defaults());config.disableAudio(true);config.setTitle("GameCore automatic lobby acceptance");
        try{new Lwjgl3Application(probe,config);probe.platform().termination().get(8,TimeUnit.SECONDS);if(!probe.done||!probe.isDisposed()){throw new IllegalStateException("Lobby acceptance incomplete");}}
        finally{if(probe.http!=null){probe.http.stop(0);}System.clearProperty("gamecore.platform.url");}
        Files.writeString(out.resolve("result.txt"),"PLATFORM_LOBBY_OK\nAutoStartup=true\nNoAddressField=true\nGuestPreview=true\nEmptyAndMalformed=true\nRetryRecovery=true\nExpiredSessionCleared=true\nRepeatedExit=true\nHttpTerminated=true\n");System.out.println("PLATFORM_LOBBY_OK");
    }
    private static final class Probe extends GameCoreClient {
        final Path out;final int port;final long deadline=System.nanoTime()+50_000_000_000L;int phase,frames,cycles;volatile int mode;boolean done,expiryTested;HttpServer http;PlatformScreen previous;
        final String room="r-"+"a".repeat(32);
        Probe(Path out,int port){super(out.resolve("preferences.properties"));this.out=out;this.port=port;}
        Actor actor(String id){return getScreen() instanceof UiScreen ui?ui.stage().getRoot().findActor(id):null;}
        boolean notice(String value){return actor("platform-notice") instanceof Label l&&l.getText().toString().contains(value);}
        void click(String id){var screen=(UiScreen)getScreen();var a=actor(id);if(a==null){throw new IllegalStateException("Missing "+id);}screen.stage().setKeyboardFocus(a);var point=a.localToStageCoordinates(new Vector2(a.getWidth()/2,a.getHeight()/2));var hit=screen.stage().hit(point.x,point.y,true);if(hit!=a&&(hit==null||!hit.isDescendantOf(a))){throw new IllegalStateException("Unreachable "+id);}screen.stage().stageToScreenCoordinates(point);Gdx.input.getInputProcessor().touchDown(Math.round(point.x),Math.round(point.y),0,Input.Buttons.LEFT);Gdx.input.getInputProcessor().touchUp(Math.round(point.x),Math.round(point.y),0,Input.Buttons.LEFT);}
        void capture(String name){var pixmap=Pixmap.createFromFrameBuffer(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());PixmapIO.writePNG(new FileHandle(out.resolve(name).toFile()),pixmap,6,true);pixmap.dispose();}
        void startHttp() throws java.io.IOException {http=HttpServer.create(new InetSocketAddress("127.0.0.1",port),8);http.createContext("/api/public/lobby",e->{
            String body=mode==2?"{broken}":"{\"games\":[{\"id\":\"snake\",\"version\":\"1\",\"rulesVersion\":\"snake/1\",\"players\":2,\"tps\":20}],\"rooms\":"+(mode==1?"[]":"[{\"id\":\""+room+"\",\"gameId\":\"snake\",\"status\":\"OPEN\",\"players\":1,\"capacity\":2,\"readyPlayers\":0}]")+"}";
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);try(var output=e.getResponseBody()){output.write(bytes);}e.close();});
            http.createContext("/api/auth/login",e->{byte[] bytes="{\"token\":\"test-only\",\"expiresAt\":1,\"profile\":{\"userId\":\"test\",\"username\":\"player01\",\"displayName\":\"玩家\",\"role\":\"USER\"}}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);try(var o=e.getResponseBody()){o.write(bytes);}e.close();});
            http.createContext("/api/games",e->{byte[] bytes="{\"code\":\"UNAUTHORIZED\",\"message\":\"test\"}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(401,bytes.length);try(var o=e.getResponseBody()){o.write(bytes);}e.close();});http.start();}
        @Override public void render(){super.render();if(System.nanoTime()>deadline){throw new IllegalStateException("Lobby smoke timeout phase="+phase);}if(++frames%4!=0){return;}
            try{if(actor("platform-url")!=null){throw new IllegalStateException("Address input exposed");}
                if(phase==0&&getScreen() instanceof PlatformScreen&&notice("平台暂时无法连接")){capture("unavailable.png");startHttp();click("platform-refresh");phase++;}
                else if(phase==1&&actor("platform-join-"+room)!=null){capture("guest-rooms.png");click("platform-join-"+room);phase++;}
                else if(phase==2&&actor("platform-register")!=null){capture("login.png");mode=1;click("platform-browse");phase++;}
                else if(phase==3&&actor("platform-empty-rooms")!=null&&notice("房间列表已更新")){capture("empty.png");mode=2;click("platform-refresh");phase++;}
                else if(phase==4&&notice("平台暂时无法连接")){mode=0;click("platform-refresh");phase++;}
                else if(phase==5&&actor("platform-join-"+room)!=null&&notice("房间列表已更新")){if(!expiryTested){click("platform-guest-login");phase=7;}else{previous=(PlatformScreen)getScreen();click("platform-offline");phase=6;}}
                else if(phase==6&&getScreen() instanceof HomeScreen){if(!previous.isDisposed()){throw new IllegalStateException("Stage retained");}if(++cycles<5){click("open-platform");phase=5;}else{capture("offline.png");done=true;Gdx.app.exit();}}
                else if(phase==7&&actor("platform-login")!=null){((com.badlogic.gdx.scenes.scene2d.ui.TextField)actor("platform-username")).setText("player01");((com.badlogic.gdx.scenes.scene2d.ui.TextField)actor("platform-password")).setText("test-password-only");click("platform-login");phase=8;}
                else if(phase==8&&notice("已过期")&&actor("platform-login")!=null){if(platform().profile()!=null){throw new IllegalStateException("Expired account retained");}capture("expired.png");expiryTested=true;click("platform-browse");phase=5;}
            }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        }
    }
}
