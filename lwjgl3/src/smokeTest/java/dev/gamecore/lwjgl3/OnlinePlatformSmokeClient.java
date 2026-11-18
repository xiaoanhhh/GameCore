package dev.gamecore.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.network.RemoteArenaSession;
import dev.gamecore.client.screen.OnlineArenaScreen;
import dev.gamecore.client.screen.PlatformScreen;
import dev.gamecore.client.screen.UiScreen;
import dev.gamecore.online.ProtocolValues;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** 正式HTTPS/TLS平台的双GUI正常操作验收；不使用开发步进、管理接口或客户端伪造世界。 */
public final class OnlinePlatformSmokeClient {
    private OnlinePlatformSmokeClient() { }
    public static void main(String[] args) throws Exception {
        System.setProperty("gamecore.platform.url",args[1]);
        Path out=Path.of(args[0]).toAbsolutePath();Files.createDirectories(out);
        var probe=new Probe(out,Integer.parseInt(args[2]),args[3]);
        var config=Lwjgl3Launcher.configuration(DesktopSettings.defaults());config.disableAudio(true);
        config.setTitle("GameCore TLS acceptance "+args[3]+" P"+args[2]);
        try {new Lwjgl3Application(probe,config);}finally {
            if(probe.remote!=null){probe.remote.network().closeAsync().get(5,TimeUnit.SECONDS);}
        }
        if(!probe.done||!probe.isDisposed()||!probe.arena.isDisposed()){throw new IllegalStateException("Cloud GUI incomplete");}
        Files.writeString(out.resolve("result.txt"),"CLOUD_FULL_GUI_OK\nHTTPSLogin=true\nTLSGame=true\nSelectionAndOpening=true\nSharedPauseResume=true\nRematchNewMatchId=true\nSecondMap=true\nNaturalTerminal=true\nNetworkAndStageReleased=true\n");
    }
    private static final class Probe extends GameCoreClient {
        final Path out;final int player;final String game;
        final long deadline=System.nanoTime()+320_000_000_000L;
        int phase,frame,round;long mark,frozenTick;String oldMatch="",logged="";boolean done,skipped;
        OnlineArenaScreen arena;RemoteArenaSession remote;
        Probe(Path out,int player,String game){super(out.resolve("preferences.properties"));this.out=out;this.player=player;this.game=game;}
        Actor actor(String id){return getScreen() instanceof UiScreen ui?ui.stage().getRoot().findActor(id):null;}
        void require(boolean test,String message){if(!test){throw new IllegalStateException(message);}}
        void set(String id,String value){((TextField)actor(id)).setText(value);}
        void key(int key,boolean down){if(down){Gdx.input.getInputProcessor().keyDown(key);}else{Gdx.input.getInputProcessor().keyUp(key);}}
        void click(String id){
            var ui=(UiScreen)getScreen();var a=actor(id);require(a!=null,"Missing "+id);
            ui.stage().setKeyboardFocus(a);var p=a.localToStageCoordinates(new Vector2(a.getWidth()/2,a.getHeight()/2));
            Actor hit=ui.stage().hit(p.x,p.y,true);require(hit==a||hit!=null&&hit.isDescendantOf(a),"Unreachable "+id);
            ui.stage().stageToScreenCoordinates(p);
            require(p.x>=0&&p.y>=0&&p.x<Gdx.graphics.getWidth()&&p.y<Gdx.graphics.getHeight(),"Offscreen "+id);
            Gdx.input.getInputProcessor().touchDown(Math.round(p.x),Math.round(p.y),0,Input.Buttons.LEFT);
            Gdx.input.getInputProcessor().touchUp(Math.round(p.x),Math.round(p.y),0,Input.Buttons.LEFT);
        }
        void capture(String name){
            Gdx.gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT,1);var p=Pixmap.createFromFrameBuffer(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());
            PixmapIO.writePNG(new FileHandle(out.resolve("round"+round+"-"+name+".png").toFile()),p,6,true);p.dispose();
        }
        int slot(){return remote.network().joined().getPlayerSlot();}
        String map(){return game.equals("fight")?round==0?"BAMBOO":"FROST":game.equals("tank")?round==0?"ruins":"crossfire":round==0?"obstacles":"clear";}
        Map<String,String> options(){return ProtocolValues.config(remote.roomState().getConfig()).options();}
        void select(){
            require(remote.snapshot().getSimulationTick()==0,"Selection advanced rules");
            if(game.equals("fight")){
                click("select-fighter-"+slot()+"-"+(round==0&&slot()==0?"feidudu":"naiwa"));click("select-next");
                if(slot()==0){click("select-map-"+map().toLowerCase(Locale.ROOT));}
                capture("selection");click("select-next");
            }else if(slot()==0){((SelectBox<?>)actor(game+"-map")).setSelectedIndex(round==0?1:0);}
        }
        @Override public void render(){
            super.render();long now=System.nanoTime();String state=phase+":"+round+(remote==null?"":":"+remote.status());
            if(!logged.equals(state)){logged=state;System.out.println("CLOUD_STATE "+game+" P"+player+" "+state);System.out.flush();}
            require(now<deadline,"Cloud GUI timeout "+state+(remote==null?"":" "+remote.notice()));
            if(++frame%4!=0){return;}
            try {
                if(phase==0&&actor("platform-guest-login")!=null&&actor("platform-notice") instanceof Label l&&l.getText().toString().contains("房间列表已更新")){
                    if(player==1){click("platform-guest-login");phase=1;}
                    else{Path room=out.getParent().resolve("room.txt");if(Files.exists(room)&&actor("platform-join-"+Files.readString(room).strip())!=null){click("platform-join-"+Files.readString(room).strip());phase=1;}}
                }else if(phase==1&&actor("platform-register")!=null){set("platform-username",System.getenv("GAMECORE_SMOKE_USER"));set("platform-password",System.getenv("GAMECORE_SMOKE_PASSWORD"));click("platform-register");phase=2;
                }else if(phase==2&&actor("platform-create-"+game)!=null){if(player==1){click("platform-create-"+game);phase=3;}else if(actor("platform-ready")!=null){phase=3;}
                }else if(phase==3&&actor("platform-ready")!=null){if(player==1){Files.writeString(out.getParent().resolve("room.txt"),platform().room());}click("platform-ready");phase=4;
                }else if(phase==4&&actor("platform-play")!=null){click("platform-play");phase=5;
                }else if(phase==5&&getScreen() instanceof OnlineArenaScreen page){arena=page;remote=page.remote();if(remote!=null){phase=10;}
                }else if(phase>=10&&phase<=18){
                    require(!Set.of("ERROR","DISCONNECTED","FAILED").contains(remote.status()),remote.notice());
                    if(remote.roomState()==null||remote.network().joined()==null||remote.snapshot()==null){return;}
                    if(phase==10&&Set.of("WAITING","SETUP").contains(remote.status())){select();phase=11;
                    }else if(phase==11&&map().equals(options().getOrDefault("map","clear"))&&(!game.equals("fight")||(round==0?"FEIDUDU":"NAIWA").equals(options().get("one"))&&"NAIWA".equals(options().get("two")))){
                        if(remote.roomState().getSeatsList().stream().noneMatch(s->s.getSlot()==slot()&&s.getReady())){click("online-action");}phase=12;
                    }else if(phase==12){
                        if(remote.status().equals("OPENING")&&!skipped){require(remote.snapshot().getSimulationTick()==0,"Opening advanced rules");capture("opening");key(Input.Keys.ENTER,true);key(Input.Keys.ENTER,false);skipped=true;}
                        if(remote.status().equals("RUNNING")){phase=13;}
                    }else if(phase==13){
                        if(remote.status().equals("RUNNING")&&slot()==0){key(Input.Keys.P,true);key(Input.Keys.P,false);}
                        if(remote.status().equals("PAUSED")){mark=now;phase=14;}
                    }else if(phase==14&&now-mark>250_000_000L){frozenTick=remote.snapshot().getSimulationTick();mark=now;capture("paused");phase=15;
                    }else if(phase==15&&now-mark>400_000_000L){require(remote.snapshot().getSimulationTick()==frozenTick,"Paused rules advanced");click("online-action");phase=16;
                    }else if(phase==16&&remote.status().equals("RUNNING")){
                        capture("running");mark=now;
                        if(game.equals("tank")){key(Input.Keys.SPACE,true);}
                        if(game.equals("fight")){key(slot()==0?Input.Keys.S:Input.Keys.Y,true);}
                        phase=17;
                    }else if(phase==17&&now-mark>1_200_000_000L){
                        key(Input.Keys.SPACE,false);key(Input.Keys.S,false);key(Input.Keys.Y,false);
                        if(round==0&&(!game.equals("snake")||remote.status().equals("FINISHED"))){oldMatch=remote.match();click("online-restart");phase=18;}
                        else if(round==1&&remote.status().equals("FINISHED")){capture("finished");Files.write(out.resolve("final.pb"),remote.snapshot().toByteArray());click("online-back");phase=19;}
                    }else if(phase==18&&!oldMatch.equals(remote.match())&&remote.status().equals("SETUP")){Files.writeString(out.resolve("matches.txt"),oldMatch+"\n"+remote.match()+"\n");round=1;skipped=false;phase=10;}
                }else if(phase==19&&getScreen() instanceof PlatformScreen){done=true;Gdx.app.exit();}
            }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        }
    }
}
