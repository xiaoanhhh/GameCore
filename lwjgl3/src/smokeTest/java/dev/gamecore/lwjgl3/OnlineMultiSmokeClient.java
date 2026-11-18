package dev.gamecore.lwjgl3;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import dev.gamecore.client.fight.FightHud;
import dev.gamecore.client.fight.FightHudModel;
import dev.gamecore.client.snake.SnakeArenaLayout;
import dev.gamecore.client.tank.TankArenaLayout;
import dev.gamecore.client.tank.TankBoard;
import dev.gamecore.client.ui.ArenaHudText;
import com.badlogic.gdx.files.FileHandle;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.screen.*;
import java.nio.file.*;
import java.nio.file.Files;
import java.util.*;
/** 真正独立桌面进程，从大厅模式进入v2页面，操作键盘并核验终局与异步资源清理。 */
public final class OnlineMultiSmokeClient {
    private OnlineMultiSmokeClient() { }
    public static void main(String[] args) throws Exception{
        var directory=Path.of(args[0]).toAbsolutePath();Files.createDirectories(directory);var app=new Probe(directory,Integer.parseInt(args[1]),args[2]);
        var config=Lwjgl3Launcher.configuration(DesktopSettings.defaults());config.disableAudio(true);config.setTitle("Multi-game acceptance "+args[2]);
        try{new Lwjgl3Application(app,config);}catch(RuntimeException|Error failure){if(app.remote!=null){try{app.remote.network().closeAsync().get(5,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception close){failure.addSuppressed(close);}}throw failure;}
        if(!app.completed||!app.isDisposed()){throw new IllegalStateException("Client did not complete");}
        app.remote.network().closeAsync().get(5,java.util.concurrent.TimeUnit.SECONDS);if(!app.page.isDisposed()){throw new IllegalStateException("Stage retained");}
        Files.writeString(directory.resolve("result.txt"),"MULTI_GUI_OK\nLobbyEntry=true\nInputAndSnapshot=true\nSharedLocalLayout=true\nSnapshotHudValues=true\nBothSlotsUseLocalKeys=true\nConnectionOverlay=true\nOwnRoleAndHostMap=true\nOpeningBeforeRules=true\nSharedPauseResume=true\nTwoRoundsAndMapChange=true\nRealSkillsAndEvents=true\nResize=true\nAuthoritativeResult=true\nStageDisposed=true\nNetworkTerminated=true\n");
    }
    private static final class Probe extends GameCoreClient {
        final Path directory;final int port;final String game;final long deadline=System.nanoTime()+90_000_000_000L;
        int phase,frame,round,check;boolean completed,chosen,skipped,started,secondary,tertiary,blinkSeen,laughSeen,shotSeen;long running,mark,pauseTick;String priorMatch="",logged="";OnlineArenaScreen page;dev.gamecore.client.network.RemoteArenaSession remote;
        Probe(Path directory,int port,String game){super(directory.resolve("run-"+UUID.randomUUID()).resolve("preferences.properties"));this.directory=directory;this.port=port;this.game=game;}
        private void click(String name){var ui=(UiScreen)getScreen();Actor a=ui.stage().getRoot().findActor(name);if(a==null){throw new IllegalStateException("Missing "+name);}ui.stage().setKeyboardFocus(a);ui.stage().act(0);ui.stage().draw();var p=a.localToStageCoordinates(new Vector2(a.getWidth()/2,a.getHeight()/2));Actor hit=ui.stage().hit(p.x,p.y,true);if(hit==null||hit!=a&&!hit.isDescendantOf(a)){throw new IllegalStateException("Unreachable "+name);}ui.stage().stageToScreenCoordinates(p);if(p.x<0||p.y<0||p.x>=Gdx.graphics.getWidth()||p.y>=Gdx.graphics.getHeight()){throw new IllegalStateException("Control outside window: "+name);}Gdx.input.getInputProcessor().touchDown(Math.round(p.x),Math.round(p.y),0,Input.Buttons.LEFT);Gdx.input.getInputProcessor().touchUp(Math.round(p.x),Math.round(p.y),0,Input.Buttons.LEFT);}
        private void capture(String name){page.render(0);Gdx.gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT,1);var p=Pixmap.createFromFrameBuffer(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());PixmapIO.writePNG(new FileHandle(directory.resolve(name).toFile()),p,6,true);p.dispose();}
        private void require(boolean value,String message){if(!value){throw new IllegalStateException(message);}}
        private Actor actor(String name){Actor actor=page.stage().getRoot().findActor(name);require(actor!=null,"Missing "+name);return actor;}
        private String text(String name){return ((Label)actor(name)).getText().toString();}
        private void verifyPresentation(){
            float width=page.stage().getWidth(),height=page.stage().getHeight();Actor board=actor(game+"-board");
            require(board.isVisible(),"World is hidden");require(!actor("online-connection-layer").isVisible(),"Connection form reduced arena");
            var point=board.localToStageCoordinates(new Vector2());
            if(game.equals("snake")){
                var plan=SnakeArenaLayout.calculate(width,height).board();
                require(Math.abs(board.getWidth()-plan.width())<1&&Math.abs(point.x-plan.x())<1,"Snake differs from local board layout");
                var factory=new dev.gamecore.online.snake.SnakeFactory();var snapshot=factory.snapshots().decode(remote.snapshot().getState().toByteArray());
                require(text("snake-score").equals(ArenaHudText.snakeScores(snapshot,true)),"Snake scores are stale");
                require(text("snake-speed").equals(ArenaHudText.snakeSpeed(snapshot)),"Snake speed differs");
                require(actor("snake-best").getWidth()>220&&actor("snake-best").getHeight()<30,"Snake identity wrapped vertically");
            }else if(game.equals("tank")){
                var tank=(TankBoard)board;var plan=TankArenaLayout.calculate(width,height,tank.columns(),tank.rows()).board();
                require(Math.abs(board.getWidth()-plan.width())<1&&Math.abs(point.x-plan.x())<1,"Tank differs from local board layout");
                var factory=new dev.gamecore.online.tank.TankFactory();var snapshot=factory.snapshots().decode(remote.snapshot().getState().toByteArray());
                require(text("tank-health").equals(ArenaHudText.tankHealth(snapshot)),"Tank health differs");
                require(text("tank-time").equals(ArenaHudText.tankTime(snapshot)),"Tank timer differs");
                require(actor("tank-time").getWidth()>220&&actor("tank-time").getHeight()<30,"Tank timer wrapped vertically");
            }else{
                require(Math.abs(board.getWidth()-width)<1&&Math.abs(board.getHeight()-(height-255))<1,"Fight arena differs from local layout");
                int slot=remote.network().joined().getPlayerSlot();
                var own=(FightHud)actor("fight-hud-"+slot);var opponent=(FightHud)actor("fight-hud-"+(1-slot));
                require(own.skill(FightHudModel.Skill.PUNCH).label().equals("J 出拳"),"Own slot has wrong keyboard");
                require(opponent.skill(FightHudModel.Skill.PUNCH).label().equals("出拳"),"Opponent has local controls");
                var factory=new dev.gamecore.online.fight.FightFactory();var snapshot=factory.snapshots().decode(remote.snapshot().getState().toByteArray());
                for(var fighter:snapshot.fighters()){
                    require(text("fight-values-"+fighter.player()).equals("HP "+fighter.health()+" / "+dev.gamecore.fight.FightSession.MAX_HEALTH+"   EN "+(int)fighter.energy()+" / 100"),"Fight HUD not authoritative");
                }
            }
            require(board.getWidth()>500&&board.getHeight()>300,"Arena too small");
        }
        private int slot(){return remote.network().joined().getPlayerSlot();}
        private String desiredMap(){return game.equals("fight")?dev.gamecore.fight.FightMap.values()[round==0?3:4].name():game.equals("tank")?round==0?"ruins":"crossfire":round==0?"obstacles":"clear";}
        private Map<String,String> options(){return dev.gamecore.online.ProtocolValues.config(remote.roomState().getConfig()).options();}
        private void down(int key){Gdx.input.getInputProcessor().keyDown(key);}
        private void up(int key){Gdx.input.getInputProcessor().keyUp(key);}
        private String file(String name){return round==0?name:"round1-"+name;}
        private void selectGame(){
            require(remote.snapshot()!=null,"Missing READY snapshot");require(remote.snapshot().getSimulationTick()==0,"Rules advanced before selection");
            if(game.equals("fight")){
                // 与本地“重新开始”一致：同房新局保留原选择，不自动弹出选择页，需显式打开。
                if(round==1){require(!actor("fight-selection").isVisible()&&actor("fight-arena").isVisible(),"Rematch reopened selection");click("online-select");}
                require(actor("fight-selection").isVisible(),"Local selection UI missing online");
                var kind=round==0&&slot()==0?"feidudu":"naiwa";
                click("select-fighter-"+slot()+"-"+kind);
                require(((com.badlogic.gdx.scenes.scene2d.ui.Button)actor("select-fighter-"+(1-slot())+"-naiwa")).isDisabled(),"Opponent role editable");
                page.stage().setKeyboardFocus(actor("select-next"));down(Input.Keys.ENTER);down(Input.Keys.ENTER);
                require(actor("select-map-"+desiredMap().toLowerCase(Locale.ROOT)).isVisible(),"Held confirmation skipped map page");up(Input.Keys.ENTER);
                if(slot()==0){click("select-map-"+desiredMap().toLowerCase(Locale.ROOT));}
                else{require(((com.badlogic.gdx.scenes.scene2d.ui.Button)actor("select-map-"+desiredMap().toLowerCase(Locale.ROOT))).isDisabled(),"Guest edited map");}
                capture(file("selection.png"));click("select-next");
            }else{
                var box=(com.badlogic.gdx.scenes.scene2d.ui.SelectBox<?>)actor(game+"-map");
                require(box.isDisabled()==(slot()!=0),"Map selector ownership differs");if(slot()==0){box.setSelectedIndex(round==0?1:0);}
            }
        }
        private void observeSkills(){
            if(game.equals("fight")&&remote.snapshot()!=null){
                var snapshot=new dev.gamecore.online.fight.FightFactory().snapshots().decode(remote.snapshot().getState().toByteArray());var own=snapshot.fighters().get(slot());
                blinkSeen|=own.teleportCooldown()>0;laughSeen|=own.action()==dev.gamecore.fight.FightSnapshot.Action.LAUGH;shotSeen|=own.energy()<25;
            }else if(game.equals("tank")){
                shotSeen|=remote.events().stream().anyMatch(e->e instanceof dev.gamecore.api.event.GameEvent.Effect effect&&effect.kind()==dev.gamecore.api.event.GameEvent.Effect.Kind.SHOT&&effect.player()==slot());
            }
        }
        @Override public void render(){
            super.render();if(remote!=null){observeSkills();}
            String progress=game+" round="+round+" phase="+phase+" check="+check+(remote==null?"":" state="+remote.status()+(remote.roomState()==null?"":" ready="+remote.roomState().getSeatsList().stream().filter(dev.gamecore.protocol.GameProtocol.RoomSeat::getReady).count()+" resume="+remote.roomState().getSeatsList().stream().filter(dev.gamecore.protocol.GameProtocol.RoomSeat::getResume).count()));
            if(!progress.equals(logged)){logged=progress;System.out.println("GUI_STATE "+progress);System.out.flush();}
            if(System.nanoTime()>deadline){throw new IllegalStateException("GUI timeout phase="+phase+" round="+round+(remote==null?"":" status="+remote.status()+" "+remote.notice()));}
            if(++frame%3!=0){return;}long now=System.nanoTime();
            if(phase==0){detail(game);phase=1;}
            else if(phase==1&&getScreen() instanceof GameDetailScreen){click("mode-multiplayer");click("start-preview");phase=2;}
            else if(phase==2&&getScreen() instanceof OnlineArenaScreen arena){page=arena;((TextField)actor("online-port")).setText(""+port);click("online-connect");phase=3;}
            else if(phase>=3&&(phase<=10||phase==12)){
                remote=page.remote();if(remote==null){return;}
                if(remote.status().equals("ERROR")||remote.status().equals("DISCONNECTED")){throw new IllegalStateException(remote.notice());}
                if(remote.roomState()==null||remote.network().joined()==null){return;}
                if(phase==3){
                    if(!Set.of("WAITING","SETUP").contains(remote.status())||remote.snapshot()==null){return;}
                    selectGame();phase=4;
                }else if(phase==4){
                    boolean selected=options().getOrDefault("map","clear").equals(desiredMap());
                    if(game.equals("fight")){selected&=options().get("one").equals(round==0?"FEIDUDU":"NAIWA")&&options().get("two").equals("NAIWA");}
                    if(selected){
                        // 回环调试快进可能在终局切换后暂停新局；先用真实双继续解除，再提交准备。
                        // 按钮此时发送RESUME而非READY，不能点击一次便跳到等待开局阶段。
                        if(remote.roomState().getPaused()){
                            boolean resumed=remote.roomState().getSeatsList().stream().anyMatch(s->s.getSlot()==slot()&&s.getResume());
                            if(!resumed){click("online-action");}return;
                        }
                        boolean ready=remote.roomState().getSeatsList().stream().anyMatch(s->s.getSlot()==slot()&&s.getReady());
                        if(Set.of("WAITING","SETUP").contains(remote.status())&&!ready){click("online-action");}phase=5;
                    }
                }else if(phase==5){
                    if(remote.status().equals("OPENING")&&!skipped){
                        require(remote.snapshot().getSimulationTick()==0,"Opening advanced rules");capture(file("opening.png"));down(Input.Keys.ENTER);down(Input.Keys.ENTER);up(Input.Keys.ENTER);skipped=true;
                    }
                    if(remote.status().equals("RUNNING")){phase=6;}
                }else if(phase==6){
                    if(remote.status().equals("RUNNING")&&slot()==0){down(Input.Keys.P);up(Input.Keys.P);}
                    if(remote.status().equals("PAUSED")){mark=now;pauseTick=remote.snapshot().getServerTick();check=0;phase=7;}
                }else if(phase==7){
                    if(check==0&&now-mark>100_000_000L){pauseTick=remote.snapshot().getServerTick();verifyPresentation();capture(file("paused.png"));Gdx.graphics.setWindowedMode(1600,900);mark=now;check=1;}
                    else if(check==1&&now-mark>180_000_000L){require(remote.snapshot().getServerTick()==pauseTick,"Paused world moved");verifyPresentation();capture(file("running-large.png"));Gdx.graphics.setWindowedMode(1280,720);mark=now;check=2;}
                    else if(check==2&&now-mark>180_000_000L){
                        verifyPresentation();click("online-details");require(actor("online-connection-layer").isVisible(),"Connection overlay missing");down(Input.Keys.ESCAPE);up(Input.Keys.ESCAPE);
                        require(!actor("online-connection-layer").isVisible(),"Overlay did not close");
                        if(game.equals("snake")){click("snake-leaderboard");require(actor("snake-ranking-dialog").isVisible(),"Online ranking missing");click("snake-ranking-close");}
                        if(game.equals("fight")){
                            String match=remote.match();byte[] before=remote.snapshot().getState().toByteArray();
                            click("online-select");require(actor("fight-selection").isVisible(),"Paused draft missing");
                            click("select-fighter-"+slot()+"-naiwa");click("select-back");page.render(0);
                            require(!actor("fight-selection").isVisible()&&remote.match().equals(match)&&java.util.Arrays.equals(before,remote.snapshot().getState().toByteArray()),"Cancel changed the original match");
                        }
                        click("online-action");phase=8;started=false;
                    }
                }else if(phase==8){
                    if(remote.status().equals("RUNNING")){
                        if(!started){started=true;running=now;verifyPresentation();capture(file("running.png"));
                            if(round==0&&game.equals("tank")){down(Input.Keys.W);down(Input.Keys.SPACE);}
                            if(game.equals("fight")){if(round==0){down(slot()==0?Input.Keys.S:Input.Keys.Y);}else{down(Input.Keys.U);up(Input.Keys.U);}}
                        }
                        long elapsed=now-running;
                        if(!secondary&&elapsed>(game.equals("fight")?650_000_000L:200_000_000L)){secondary=true;if(round==0&&game.equals("fight")&&slot()==0){down(Input.Keys.K);up(Input.Keys.K);}}
                        if(!tertiary&&elapsed>(game.equals("fight")?900_000_000L:450_000_000L)){tertiary=true;if(round==0&&game.equals("fight")){up(Input.Keys.S);up(Input.Keys.Y);}}
                        if(elapsed>(game.equals("fight")?1_200_000_000L:650_000_000L)){
                            if(round==0&&game.equals("fight")){require(slot()==0?blinkSeen:laughSeen,"Real online blink/laugh missing slot="+slot()+" input="+remote.inputError());}
                            if(round==1&&game.equals("fight")){require(shotSeen,"Real online ranged energy cost missing");}
                            if(round==0&&game.equals("tank")){require(shotSeen,"Authoritative shot effect missing");}
                            up(Input.Keys.W);up(Input.Keys.SPACE);up(Input.Keys.S);up(Input.Keys.Y);
                            System.out.println("GUI_READY_FAST_STEP "+game+" round="+round);System.out.flush();phase=9;
                        }
                    }else if(remote.status().equals("FINISHED")){phase=9;}
                }else if(phase==9&&remote.status().equals("FINISHED")){
                    try{Files.write(directory.resolve("final-"+round+".pb"),remote.snapshot().toByteArray());}catch(java.io.IOException e){throw new IllegalStateException(e);}
                    capture(file("finished.png"));
                    if(round==0){priorMatch=remote.match();click("online-restart");phase=10;}
                    else{
                        if(game.equals("snake")){
                            require(onlineSnakeRecords("clear").leaderboard(dev.gamecore.snake.SnakeMode.DUO).size()==2&&onlineSnakeRecords("obstacles").leaderboard(dev.gamecore.snake.SnakeMode.DUO).size()==2,"Online ranking duplicated or mixed maps");
                            require(snakeRecords().leaderboard(dev.gamecore.snake.SnakeMode.DUO).isEmpty(),"Online polluted local records");
                        }
                        try{Files.write(directory.resolve("final.pb"),remote.snapshot().toByteArray());}catch(java.io.IOException e){throw new IllegalStateException(e);}
                        if(game.equals("fight")){pauseTick=remote.snapshot().getSimulationTick();down(Input.Keys.P);up(Input.Keys.P);phase=12;}else{click("online-back");phase=11;}
                    }
                }else if(phase==10){
                    if(!remote.match().equals(priorMatch)&&remote.status().equals("SETUP")){round=1;skipped=secondary=tertiary=false;blinkSeen=laughSeen=shotSeen=false;phase=3;}
                }else if(phase==12&&remote.roomState().getPaused()){
                    require(remote.status().equals("FINISHED")&&remote.snapshot().getSimulationTick()==pauseTick,"Terminal pause restarted or advanced rules");capture("victory-paused.png");click("online-back");phase=11;
                }
            }else if(phase==11&&getScreen() instanceof HomeScreen){completed=true;Gdx.app.exit();}
        }
    }
}
