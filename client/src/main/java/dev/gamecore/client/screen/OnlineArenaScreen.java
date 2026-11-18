package dev.gamecore.client.screen;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.network.*;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.online.GameRegistry;
import dev.gamecore.api.online.GameFactory;
import dev.gamecore.platform.api.PlatformMessages.Ticket;

/** 多游戏在线页只拥有连接生命周期；游戏使用本地同款布局，连接表单以覆盖层展示，不挤占舞台。 */
public final class OnlineArenaScreen extends UiScreen {
    private final GameFactory<?,?> factory;
    private final OnlineGameView view;
    private final TextField host,port,room;
    private final Label status;
    private final Table connectionLayer=new Table();
    private final Ticket ticket;
    private final TextButton action,select,restart,cancel;
    private final java.util.Set<Integer> menuHeld=new java.util.HashSet<>();
    private Dialog leaderboard;
    private String displayedMatch="";private long inputEpoch;
    private java.util.Map<String,String> nextChoices;
    private String choicesFromMatch="";private boolean restartWasRequested;
    /** 与本地一致：“再来一局”/对局中改选沿用已确认选择直接准备，“重新开始”只建新局等待准备。 */
    private boolean rematchReady,autoReady;
    private RemoteArenaSession remote;
    private long displayedTick=-1;
    private byte[] displayed;
    private boolean presentationFailed;
    private dev.gamecore.protocol.GameProtocol.Snapshot presented;

    public OnlineArenaScreen(GameCoreClient app,String game){this(app,game,null);}
    public OnlineArenaScreen(GameCoreClient app,String game,Ticket ticket){
        super(app,app.registry().require(game).name()+" 在线对战","P/Esc 暂停 · F3 连接信息 · 双方同意继续或重开",true);
        this.ticket=ticket;factory=GameRegistry.builtIn().require(game);
        var details=bind(theme.button("连接信息",false),"online-details",this::toggleConnection);
        var back=bind(theme.button("返回大厅",false),"online-back",this::onBack);
        action=bind(theme.button("准备",true),"online-action",this::activate);
        select=bind(theme.button(game.equals("fight")?"角色 / 地图":"选择地图",false),"online-select",this::requestSettings);
        restart=bind(theme.button("重新开始",false),"online-restart",()->{rematchReady=false;send(dev.gamecore.protocol.GameProtocol.RoomAction.RESTART);});
        cancel=bind(theme.button("取消请求",false),"online-cancel-restart",this::cancelRestart);
        view=OnlineGameViews.create(game,app,java.util.List.of(action,select,restart,cancel,details,back),new OnlineGameView.Host(){
            @Override public void choose(java.util.Map<String,String> choices,boolean ready){
                releaseInput();if(remote==null){return;}
                if(java.util.Set.of("WAITING","SETUP").contains(remote.status())){remote.select(choices,ready);}
                else{nextChoices=java.util.Map.copyOf(choices);choicesFromMatch=remote.match();rematchReady=true;remote.control(dev.gamecore.protocol.GameProtocol.RoomAction.RESTART);}
            }
            @Override public void register(com.badlogic.gdx.scenes.scene2d.Actor actor){focus(actor,actor.getName());}
            @Override public void ranking(){showLeaderboard();}
        });
        host=field("online-host",System.getProperty("gamecore.multi.host","127.0.0.1"),128);
        port=field("online-port",System.getProperty("gamecore.multi.port","7200"),5);
        port.setTextFieldFilter(new TextField.TextFieldFilter.DigitsOnlyFilter());
        room=field("online-room",game+"-1",40);
        status=theme.caption(ticket==null?"请输入地址并连接":"正在进入比赛，请稍候…");status.setWrap(true);status.setName("online-status");
        Table panel=new Table();panel.top().pad(20);panel.setBackground(theme.panel(ThemeManager.PANEL));
        panel.add(theme.label(app.registry().require(game).name()+" 在线双人",theme.assets.heading,ThemeManager.TEXT)).growX().row();
        if(ticket==null){
            panel.add(theme.caption("服务器地址")).left().padTop(16).row();panel.add(host).growX().height(38).row();
            panel.add(theme.caption("端口")).left().padTop(8).row();panel.add(port).growX().height(38).row();
            panel.add(theme.caption("房间")).left().padTop(8).row();panel.add(room).growX().height(38).row();
            panel.add(bind(theme.button("连接房间",true),"online-connect",this::connect)).growX().height(42).padTop(12).row();
        }
        panel.add(status).growX().padTop(12).row();
        var hint=theme.caption(view.hint());hint.setWrap(true);panel.add(hint).growX().padTop(12).row();
        panel.add(bind(theme.button("返回游戏",false),"online-details-close",this::toggleConnection)).growX().height(38).padTop(12).row();
        panel.add(bind(theme.button("返回大厅",false),"online-connection-back",this::onBack)).growX().height(38).padTop(6);
        var scroll=dev.gamecore.client.ui.ArenaPanels.scroll(theme,panel);
        connectionLayer.setName("online-connection-layer");connectionLayer.setBackground(theme.solid(new com.badlogic.gdx.graphics.Color(0,0,0,.82f)));
        connectionLayer.add(scroll).width(420).maxHeight(530).growY().pad(24);
        Stack layers=new Stack();layers.add(view.actor());if(view.selectionActor()!=null){layers.add(view.selectionActor());}layers.add(connectionLayer);content.add(layers).grow();
        connectionLayer.setVisible(ticket==null);view.connection(status.getText().toString(),-1);
        if(ticket==null){stage.setKeyboardFocus(host);}else{
            host.setText(ticket.host());port.setText(Integer.toString(ticket.port()));room.setText(ticket.roomId());
            host.setDisabled(true);port.setDisabled(true);room.setDisabled(true);connect();
        }
    }
    private TextField field(String id,String value,int max){var field=focus(theme.searchField(),id);field.setText(value);field.setMaxLength(max);field.setFocusTraversal(false);return field;}
    public void connect(){
        if(remote!=null&&remote.network().active()){status.setText("已经连接房间，请使用重新开始或返回大厅");return;}
        try{
            if(remote!=null){remote.close();}view.cancelReconfigure();view.newMatch();nextChoices=null;choicesFromMatch="";restartWasRequested=rematchReady=autoReady=false;displayed=null;displayedTick=-1;displayedMatch="";inputEpoch=0;presentationFailed=false;
            remote=new RemoteArenaSession(factory,host.getText().strip(),Integer.parseInt(port.getText()),room.getText().strip(),ticket==null?"":ticket.credential(),ticket==null?Boolean.getBoolean("gamecore.multi.tls"):ticket.tls(),true,app.trafficObserver());
            connectionLayer.setVisible(false);stage.setKeyboardFocus(null);
        }catch(RuntimeException invalid){status.setText("无法连接，请检查地址、端口和房间名称");view.connection(status.getText().toString(),-1);connectionLayer.setVisible(true);}
    }
    private void toggleConnection(){
        releaseInput();connectionLayer.setVisible(!connectionLayer.isVisible());stage.cancelTouchFocus();
        stage.setKeyboardFocus(connectionLayer.isVisible()&&ticket==null?host:null);
    }
    public RemoteArenaSession remote(){return remote;}
    private void send(dev.gamecore.protocol.GameProtocol.RoomAction value){releaseInput();if(remote!=null&&remote.roomState()!=null){remote.control(value);}}
    private void activate(){
        if(remote==null||remote.roomState()==null){return;}var state=remote.roomState();
        if(state.getPaused()){send(dev.gamecore.protocol.GameProtocol.RoomAction.RESUME);return;}
        switch(state.getPhase()){
            case "WAITING","SETUP" -> {int slot=remote.network().joined().getPlayerSlot();boolean ready=state.getSeatsList().stream().anyMatch(s->s.getSlot()==slot&&s.getReady());send(ready?dev.gamecore.protocol.GameProtocol.RoomAction.UNREADY:dev.gamecore.protocol.GameProtocol.RoomAction.READY);}
            case "RUNNING","OPENING" -> send(dev.gamecore.protocol.GameProtocol.RoomAction.PAUSE);
            case "PAUSED" -> send(dev.gamecore.protocol.GameProtocol.RoomAction.RESUME);
            case "FINISHED","ABORTED","FAILED" -> {rematchReady=true;send(dev.gamecore.protocol.GameProtocol.RoomAction.RESTART);}
            default -> { }
        }
    }
    private void requestSettings(){
        if(remote==null||remote.roomState()==null){return;}
        if(java.util.Set.of("WAITING","SETUP").contains(remote.status())){autoReady=false;send(dev.gamecore.protocol.GameProtocol.RoomAction.UNREADY);view.openSelection();}
        else if(view.selectionActor()!=null){if(java.util.Set.of("RUNNING","OPENING","FINISHED").contains(remote.status())&&!remote.roomState().getPaused()){send(dev.gamecore.protocol.GameProtocol.RoomAction.PAUSE);}view.openSelection();}
        else{send(dev.gamecore.protocol.GameProtocol.RoomAction.RESTART);}
    }
    private void cancelRestart(){nextChoices=null;rematchReady=false;view.cancelReconfigure();send(dev.gamecore.protocol.GameProtocol.RoomAction.CANCEL_RESTART);}
    private void showLeaderboard(){
        if(leaderboard!=null){return;}pause();
        String arena=remote==null||remote.roomState()==null?"clear":dev.gamecore.online.ProtocolValues.config(remote.roomState().getConfig()).options().getOrDefault("map","clear");
        var records=app.onlineSnakeRecords(arena);records.flush();
        var close=bind(theme.button("关闭排行榜",true),"snake-ranking-close",this::closeLeaderboard);
        leaderboard=dev.gamecore.client.snake.SnakeLeaderboard.create(theme,records,dev.gamecore.snake.SnakeMode.DUO,arena.equals("obstacles")?"在线障碍双人":"在线经典双人","在线个人排行榜",close);
        leaderboard.show(stage);stage.setKeyboardFocus(close);
    }
    private void closeLeaderboard(){if(leaderboard!=null){forgetFocus(leaderboard);leaderboard.remove();leaderboard=null;stage.setKeyboardFocus(action);view.frozen(false);}}
    @Override protected void onBack(){if(ticket==null){app.home();}else{app.openPlatform();}}
    @Override public void render(float delta){
        long now=System.nanoTime();
        if(remote!=null&&!presentationFailed){
            remote.update(view::command,now,view::clear);var snapshot=remote.snapshot();
            if(!remote.match().equals(displayedMatch)){
                closeLeaderboard();boolean rematch=!displayedMatch.isEmpty();autoReady=rematch&&rematchReady&&factory.type().id().equals("fight");rematchReady=false;
                if(rematch){view.rematch();}else{view.newMatch();}displayedMatch=remote.match();displayed=null;displayedTick=-1;inputEpoch=0;}
            if(remote.roomState()!=null&&inputEpoch!=remote.roomState().getInputEpoch()){view.clear();inputEpoch=remote.roomState().getInputEpoch();}
            int slot=remote.network().joined()==null?-1:remote.network().joined().getPlayerSlot();
            view.connection(remote.notice(),slot,remote.alerting());
            if(snapshot!=null&&snapshot!=presented){
                var bytes=snapshot.getState().toByteArray();
                if(snapshot.getServerTick()!=displayedTick||!java.util.Arrays.equals(displayed,bytes)){
                    try{view.snapshot(bytes,remote.snapshotArrivalNanos());displayed=bytes;displayedTick=snapshot.getServerTick();}
                    catch(RuntimeException invalid){presentationFailed=true;remote.close();view.newMatch();status.setText("游戏数据显示失败，请返回大厅");view.connection(status.getText().toString(),slot);connectionLayer.setVisible(true);}
                }
                presented=snapshot;
            }
            if(remote.roomState()!=null){
                var state=remote.roomState();boolean requested=state.getSeatsList().stream().anyMatch(dev.gamecore.protocol.GameProtocol.RoomSeat::getRestart);
                if(nextChoices!=null&&!remote.match().equals(choicesFromMatch)&&state.getPhase().equals("SETUP")){remote.select(nextChoices,autoReady);nextChoices=null;}
                else if(nextChoices!=null&&remote.match().equals(choicesFromMatch)&&restartWasRequested&&!requested&&!state.getPhase().equals("RESETTING")){nextChoices=null;view.cancelReconfigure();}
                restartWasRequested=requested;autoReady(state,slot);view.room(state,slot);refreshActions(slot);
            }
            view.events(remote.events());
            if(!remote.status().equals("CLOSED")){status.setText(remote.notice()+(slot<0?"":"\n你是 P"+(slot+1))+(connectionLayer.isVisible()?"\n"+remote.diagnostics(now):""));}
        }
        view.advance(Math.min(Math.max(delta,0),.1f),now);super.render(delta);
    }
    @Override protected boolean handleKey(int key){
        if(key==Input.Keys.F3){toggleConnection();return true;}
        if(key==Input.Keys.ESCAPE&&connectionLayer.isVisible()){toggleConnection();return true;}
        if(connectionLayer.isVisible()){return false;}
        if(leaderboard!=null){if(key==Input.Keys.ESCAPE||key==Input.Keys.ENTER||key==Input.Keys.SPACE){closeLeaderboard();}return true;}
        if(view.selectionKey(key,true)){return true;}
        if(view.selecting()){return false;}
        if(remote!=null&&remote.status().equals("OPENING")&&(key==Input.Keys.ENTER||key==Input.Keys.SPACE)){if(menuHeld.add(key)){send(dev.gamecore.protocol.GameProtocol.RoomAction.SKIP_OPENING);}return true;}
        if(key==Input.Keys.P||key==Input.Keys.ESCAPE||factory.type().id().equals("snake")&&key==Input.Keys.SPACE){
            if(menuHeld.add(key)){
                if(remote!=null&&remote.status().equals("FINISHED")&&factory.type().id().equals("fight")&&!remote.roomState().getPaused()){send(dev.gamecore.protocol.GameProtocol.RoomAction.PAUSE);}
                else if(!(remote!=null&&remote.status().equals("FINISHED")&&factory.type().id().equals("snake")&&key!=Input.Keys.SPACE)){activate();}
            }return true;
        }
        if(key==Input.Keys.F1){return view.key(key,true);}
        return remote!=null&&remote.status().equals("RUNNING")&&view.key(key,true);
    }
    @Override protected boolean handleKeyUp(int key){menuHeld.remove(key);view.selectionKey(key,false);return view.key(key,false);}
    /** 新局沿用服务端已确认配置自动准备；对手改选会以新 revision 清准备，此时补发一次而不重复提交。 */
    private void autoReady(dev.gamecore.protocol.GameProtocol.RoomState state,int slot){
        if(!autoReady){return;}
        if(java.util.Set.of("OPENING","RUNNING","PAUSED","FINISHED").contains(state.getPhase())){autoReady=false;return;}
        boolean ready=state.getSeatsList().stream().anyMatch(s->s.getSlot()==slot&&s.getReady());
        if(state.getPhase().equals("SETUP")&&nextChoices==null&&!ready&&!view.selecting()&&!remote.readyRequested()){remote.select(java.util.Map.of(),true);}
    }
    private void releaseInput(){view.clear();if(remote!=null){remote.release(view.command());}}
    @Override public void pause(){releaseInput();menuHeld.clear();view.frozen(true);if(remote!=null&&remote.roomState()!=null&&!remote.roomState().getPaused()&&java.util.Set.of("RUNNING","OPENING").contains(remote.status())){remote.control(dev.gamecore.protocol.GameProtocol.RoomAction.PAUSE);}}
    @Override public void resume(){view.frozen(false);}
    @Override public void hide(){view.clear();if(remote!=null){remote.close();}super.hide();}
    @Override public void dispose(){if(isDisposed()){return;}hide();view.close();super.dispose();}
    private void refreshActions(int slot){
        var state=remote.roomState();var own=state.getSeatsList().stream().filter(s->s.getSlot()==slot).findFirst().orElse(null);
        action.setDisabled(state.getPhase().equals("RESETTING"));
        action.setText(state.getPaused()?own!=null&&own.getResume()?"等待继续":"继续游戏":switch(state.getPhase()){
            case "WAITING","SETUP" -> own!=null&&own.getReady()?"取消准备":"准备";
            case "OPENING","RUNNING" -> "暂停";case "FINISHED","ABORTED","FAILED" -> "再来一局";default -> "请稍候";
        });
        boolean requested=state.getSeatsList().stream().anyMatch(dev.gamecore.protocol.GameProtocol.RoomSeat::getRestart);
        restart.setText(requested?own!=null&&own.getRestart()?"等待同意":"同意重开":"重新开始");
        restart.setDisabled(state.getSeatsCount()<2||state.getPhase().equals("RESETTING")||own!=null&&own.getRestart());
        cancel.setDisabled(!requested);select.setDisabled(slot<0||state.getPhase().equals("RESETTING")||view.selectionActor()==null&&slot!=0&&java.util.Set.of("WAITING","SETUP").contains(state.getPhase()));
    }
}
