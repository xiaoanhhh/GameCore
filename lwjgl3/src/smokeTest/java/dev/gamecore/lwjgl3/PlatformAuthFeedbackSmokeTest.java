package dev.gamecore.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.sun.net.httpserver.HttpServer;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.screen.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 回归“点击注册无反应”：未配置/坏配置不显示可填表单；等待、重复按键与错误均在表单旁反馈。 */
public final class PlatformAuthFeedbackSmokeTest {
    private PlatformAuthFeedbackSmokeTest() { }
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args[0]).toAbsolutePath();Files.createDirectories(out);System.setProperty("gamecore.offline","false");
        try {
            for(String name:new String[]{"missing","invalid","configured"}){
                Path directory=out.resolve(name);Files.createDirectories(directory);HttpServer server=name.equals("configured")?HttpServer.create(new InetSocketAddress("127.0.0.1",0),8):null;
                System.setProperty("gamecore.platform.url",name.equals("missing")?"":name.equals("invalid")?"http://example.com":"http://127.0.0.1:"+server.getAddress().getPort());
                // GameCoreClient在构造时读取发布配置，必须在设置属性后构造实际受测客户端。
                var probe=new Probe(directory,name,server);var config=Lwjgl3Launcher.configuration(DesktopSettings.defaults());config.disableAudio(true);config.setTitle("GameCore auth feedback: "+name);
                try{new Lwjgl3Application(probe,config);probe.platform().termination().get(8,TimeUnit.SECONDS);if(!probe.done||!probe.isDisposed()){throw new IllegalStateException("Auth feedback incomplete: "+name);}}
                finally{probe.release.countDown();if(probe.http!=null){probe.http.stop(0);}}
            }
        }finally{System.clearProperty("gamecore.platform.url");System.clearProperty("gamecore.offline");}
        Files.writeString(out.resolve("result.txt"),"PLATFORM_AUTH_FEEDBACK_OK\nMissingAndInvalidEndpoint=true\nNoMisleadingForm=true\nInlineFeedback=true\nPendingAndDuplicateInput=true\nConflictAndBadPassword=true\nNetworkFailure=true\nHttpTerminated=true\n");System.out.println("PLATFORM_AUTH_FEEDBACK_OK");
    }
    private static final class Probe extends GameCoreClient {
        final Path out;final String scenario;final long deadline=System.nanoTime()+45_000_000_000L;
        final CountDownLatch release=new CountDownLatch(1);final AtomicInteger registrations=new AtomicInteger();HttpServer http;int phase,frames;boolean done;
        Probe(Path out,String scenario,HttpServer server){super(out.resolve("preferences.properties"));this.out=out;this.scenario=scenario;http=server;if(http!=null){
            http.createContext("/api/public/lobby",e->respond(e,200,"{\"games\":[],\"rooms\":[]}"));
            http.createContext("/api/auth/register",e->{registrations.incrementAndGet();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException x){Thread.currentThread().interrupt();}respond(e,409,"{\"code\":\"CONFLICT\",\"message\":\"test\"}");});
            http.createContext("/api/auth/login",e->respond(e,401,"{\"code\":\"LOGIN_FAILED\",\"message\":\"test\"}"));http.start();
        }}
        static void respond(com.sun.net.httpserver.HttpExchange e,int status,String body) throws java.io.IOException {byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(status,bytes.length);try(var o=e.getResponseBody()){o.write(bytes);}e.close();}
        Actor actor(String id){return getScreen() instanceof UiScreen ui?ui.stage().getRoot().findActor(id):null;}
        boolean text(String id,String value){return actor(id) instanceof Label l&&l.getText().toString().contains(value);}
        void click(String id){var ui=(UiScreen)getScreen();var a=actor(id);if(a==null){throw new IllegalStateException("Missing "+id);}ui.stage().setKeyboardFocus(a);var p=a.localToStageCoordinates(new Vector2(a.getWidth()/2,a.getHeight()/2));var hit=ui.stage().hit(p.x,p.y,true);if(hit!=a&&(hit==null||!hit.isDescendantOf(a))){throw new IllegalStateException("Unreachable "+id);}ui.stage().stageToScreenCoordinates(p);Gdx.input.getInputProcessor().touchDown(Math.round(p.x),Math.round(p.y),0,Input.Buttons.LEFT);Gdx.input.getInputProcessor().touchUp(Math.round(p.x),Math.round(p.y),0,Input.Buttons.LEFT);}
        void fill(){((TextField)actor("platform-username")).setText("player01");((TextField)actor("platform-password")).setText("test-password-only");}
        boolean enabled(){return actor("platform-register") instanceof Button b&&!b.isDisabled()&&!((Button)actor("platform-login")).isDisabled();}
        void capture(String name){var pixmap=Pixmap.createFromFrameBuffer(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());PixmapIO.writePNG(new FileHandle(out.resolve(name).toFile()),pixmap,6,true);pixmap.dispose();}
        @Override public void render(){super.render();if(System.nanoTime()>deadline){throw new IllegalStateException("Auth feedback timeout "+scenario+" phase="+phase);}if(++frames%4!=0){return;}
            if(phase==0&&getScreen() instanceof PlatformScreen&&(scenario.equals("configured")?text("platform-notice","房间列表已更新"):actor("platform-guest-login")!=null)){click("platform-tab-登录");phase++;}
            else if(phase==1&&!scenario.equals("configured")&&actor("platform-auth-unavailable")!=null){if(actor("platform-password")!=null||actor("platform-register")!=null){throw new IllegalStateException("Unavailable credentials form exposed");}capture("unavailable.png");click("platform-offline");phase=8;}
            else if(phase==1&&scenario.equals("configured")&&actor("platform-register")!=null){click("platform-register");if(!text("platform-auth-feedback","请输入用户名和密码")){throw new IllegalStateException("Missing empty input feedback");}fill();click("platform-register");phase++;}
            else if(phase==2&&text("platform-auth-feedback","正在注册")&&!enabled()){
                if(!((TextField)actor("platform-password")).getText().isEmpty()){throw new IllegalStateException("Password retained");}capture("pending.png");((UiScreen)getScreen()).stage().setKeyboardFocus(actor("platform-register"));for(int i=0;i<3;i++){Gdx.input.getInputProcessor().keyDown(Input.Keys.ENTER);}release.countDown();phase++;
            }
            else if(phase==3&&text("platform-auth-feedback","用户名已存在")&&enabled()){if(registrations.get()!=1){throw new IllegalStateException("Duplicate registration");}capture("conflict.png");fill();click("platform-login");phase++;}
            else if(phase==4&&text("platform-auth-feedback","用户名或密码错误")&&enabled()){capture("wrong-password.png");http.stop(0);http=null;fill();click("platform-register");phase++;}
            else if(phase==5&&text("platform-auth-feedback","平台暂时无法连接")&&enabled()){capture("network-failure.png");click("platform-offline");phase=8;}
            else if(phase==8&&getScreen() instanceof HomeScreen){done=true;Gdx.app.exit();}
        }
    }
}
