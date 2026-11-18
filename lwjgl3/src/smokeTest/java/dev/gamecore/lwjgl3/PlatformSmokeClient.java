package dev.gamecore.lwjgl3;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.files.FileHandle;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.screen.*;
import dev.gamecore.client.network.RemoteArenaSession;
import java.nio.file.*;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
/** 两个独立真实桌面JVM，通过实际按钮注册/建房/加入/准备/对战/战绩/离线返回。 */
public final class PlatformSmokeClient {
    private PlatformSmokeClient() { }
    public static void main(String[] args) throws Exception {
        System.setProperty("gamecore.platform.url",args[1]);
        var output=Path.of(args[0]).toAbsolutePath();Files.createDirectories(output);var probe=new Probe(output,args[1],Integer.parseInt(args[2]));var config=Lwjgl3Launcher.configuration(DesktopSettings.defaults());config.disableAudio(true);config.setTitle("GameCore platform acceptance P"+args[2]);
        new Lwjgl3Application(probe,config);if(!probe.done||!probe.isDisposed()){throw new IllegalStateException("Platform acceptance incomplete");}probe.remote.network().closeAsync().get(5,TimeUnit.SECONDS);if(!probe.arena.isDisposed()){throw new IllegalStateException("Game Stage retained");}
        Files.writeString(output.resolve("result.txt"),"PLATFORM_GUI_OK\nRegisterLogin=true\nLobbyReady=true\nAuthoritativeResult=true\nHistory=true\nOfflineReturn=true\nStageDisposed=true\nNetworkTerminated=true\n");
    }
    private static final class Probe extends GameCoreClient {
        final Path out;final String url;final int player;final long end=System.nanoTime()+100_000_000_000L;int phase,frame;long nextHistory;String room="";boolean done;OnlineArenaScreen arena;RemoteArenaSession remote;
        Probe(Path out,String url,int player){super(out.resolve("preferences.properties"));this.out=out;this.url=url;this.player=player;}
        Actor actor(String id){return getScreen() instanceof UiScreen ui?ui.stage().getRoot().findActor(id):null;}
        void set(String id,String value){((TextField)actor(id)).setText(value);}
        void click(String id){var ui=(UiScreen)getScreen();var a=actor(id);if(a==null){throw new IllegalStateException("Missing "+id);}ui.stage().setKeyboardFocus(a);var v=a.localToStageCoordinates(new Vector2(a.getWidth()/2,a.getHeight()/2));Actor hit=ui.stage().hit(v.x,v.y,true);if(hit==null||hit!=a&&!hit.isDescendantOf(a)){throw new IllegalStateException("Unreachable "+id);}ui.stage().stageToScreenCoordinates(v);if(v.x<0||v.y<0||v.x>=Gdx.graphics.getWidth()||v.y>=Gdx.graphics.getHeight()){throw new IllegalStateException("Offscreen "+id);}Gdx.input.getInputProcessor().touchDown(Math.round(v.x),Math.round(v.y),0,Input.Buttons.LEFT);Gdx.input.getInputProcessor().touchUp(Math.round(v.x),Math.round(v.y),0,Input.Buttons.LEFT);}
        void capture(String name){Gdx.gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT,1);var pixmap=Pixmap.createFromFrameBuffer(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());PixmapIO.writePNG(new FileHandle(out.resolve(name).toFile()),pixmap,6,true);pixmap.dispose();}
        boolean history(Actor actor){if(actor instanceof Label label&&label.getName()!=null&&label.getName().startsWith("platform-history-match-")&&label.getText().toString().contains("已结束")){return true;}if(actor instanceof Group group){for(Actor child:group.getChildren()){if(history(child)){return true;}}}return false;}
        @Override public void render(){super.render();if(System.nanoTime()>end){throw new IllegalStateException("Platform GUI timeout phase="+phase);}if(++frame%4!=0){return;}try{
            if(actor("platform-url")!=null){throw new IllegalStateException("Address input exposed");}
            if(phase==0&&getScreen() instanceof PlatformScreen&&actor("platform-guest-login")!=null&&actor("platform-notice") instanceof Label label&&label.getText().toString().contains("房间列表已更新")){
                if(player==1){capture("guest.png");click("platform-guest-login");phase++;}
                else{Path file=out.getParent().resolve("room.txt");if(Files.exists(file)){room=Files.readString(file).strip();if(actor("platform-join-"+room)!=null){capture("guest.png");click("platform-join-"+room);phase++;}}}}
            else if(phase==1&&actor("platform-register")!=null){set("platform-username",System.getenv("GAMECORE_SMOKE_USER"));set("platform-password","wrong_password_123");capture("login.png");click("platform-login");phase++;}
            else if(phase==2&&actor("platform-notice") instanceof Label label&&label.getText().toString().contains("用户名或密码错误")){capture("login-error.png");set("platform-password",System.getenv("GAMECORE_SMOKE_PASSWORD"));click("platform-register");phase++;}
            else if(phase==3&&actor("platform-create-snake")!=null){if(player==1){click("platform-create-snake");phase++;}else if(actor("platform-ready")!=null){phase++;}}
            else if(phase==4&&actor("platform-ready")!=null){room=platform().room();if(player==1){Files.writeString(out.getParent().resolve("room.txt"),room);}capture("room.png");click("platform-ready");phase++;}
            else if(phase==5&&actor("platform-play")!=null){capture("ready.png");click("platform-play");phase++;}
            else if(phase==6&&getScreen() instanceof OnlineArenaScreen screen){arena=screen;remote=screen.remote();if(remote==null){return;}if(remote.status().equals("DISCONNECTED")||remote.status().equals("ERROR")){throw new IllegalStateException(remote.notice());}if(remote.status().equals("RUNNING")){capture("running.png");phase++;}}
            else if(phase==7&&remote.status().equals("FINISHED")){Files.write(out.resolve("final.pb"),remote.snapshot().toByteArray());capture("finished.png");click("online-back");phase++;}
            else if(phase==8&&actor("platform-create-snake")!=null){click("platform-tab-战绩");phase++;}
            else if(phase==9&&actor("platform-rank-snake")!=null&&actor("platform-notice") instanceof Label label&&label.getText().toString().contains("战绩已更新")){click("platform-rank-snake");phase++;}
            else if(phase==10&&getScreen() instanceof PlatformScreen screen&&history(screen.stage().getRoot())){capture("history.png");click("platform-offline");phase++;}
            else if(phase==10&&actor("platform-rank-snake")!=null&&System.nanoTime()>nextHistory){click("platform-rank-snake");nextHistory=System.nanoTime()+1_000_000_000L;}
            else if(phase==11&&getScreen() instanceof HomeScreen){capture("offline.png");done=true;Gdx.app.exit();}
        }catch(java.io.IOException e){throw new IllegalStateException(e);}}
    }
}
