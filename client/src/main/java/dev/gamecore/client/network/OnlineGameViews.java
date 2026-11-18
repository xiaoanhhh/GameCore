package dev.gamecore.client.network;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.fight.*;
import dev.gamecore.client.snake.SnakeArenaPane;
import dev.gamecore.client.snake.SnakeBoard;
import dev.gamecore.client.tank.*;
import dev.gamecore.client.ui.ArenaPanels;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.online.snake.SnakeFactory;
import dev.gamecore.online.tank.TankFactory;
import dev.gamecore.snake.*;
import java.util.List;
import java.util.Map;

/** 在线只适配输入和只读快照；完整布局/HUD 与本地共用，连接和规则均不进入展示组件。 */
public final class OnlineGameViews {
    private interface Builder { OnlineGameView create(GameCoreClient app,List<TextButton> buttons,OnlineGameView.Host host); }
    private static final Map<String,Builder> VIEWS=Map.of("snake",OnlineGameViews::snake,"tank",OnlineGameViews::tank,"fight",OnlineGameViews::fight);
    private OnlineGameViews() { }
    /** 装配检查入口：只回答是否登记了在线展示，不创建任何图形对象。 */
    public static boolean supports(String id){return VIEWS.containsKey(id);}
    public static OnlineGameView create(String id,GameCoreClient app,List<TextButton> buttons,OnlineGameView.Host host){
        var builder=VIEWS.get(id);if(builder==null){throw new IllegalArgumentException("No online view");}return builder.create(app,buttons,host);
    }
    private static Table actions(List<TextButton> buttons){
        Table table=new Table();for(int i=0;i<buttons.size();i++){table.add(buttons.get(i)).growX().uniformX().height(38).pad(3);if(i%2==1){table.row();}}return table;
    }
    private static Label label(GameCoreClient app,String name){var label=app.theme().caption("");label.setName(name);label.setWrap(true);return label;}
    private static OnlineGameView fight(GameCoreClient app,List<TextButton> buttons,OnlineGameView.Host host){return new FightView(app,buttons,host);}
    private static OnlineGameView snake(GameCoreClient app,List<TextButton> buttons,OnlineGameView.Host host){
        var factory=new SnakeFactory();var initial=factory.sessions().create(factory.defaults(),42).snapshot();
        var board=new SnakeBoard(app.theme(),initial);board.setName("snake-board");board.setVisible(false);
        var keys=app.settings().keys();var score=app.theme().label("P1  --\nP2  --",app.theme().assets.title,ThemeManager.MINT);score.setName("snake-score");score.setWrap(true);
        var best=label(app,"snake-best");best.setText("在线双人 · 本局成绩");var speed=label(app,"snake-speed");
        var status=app.theme().label("",app.theme().assets.body,ThemeManager.MINT);status.setName("snake-state");status.setWrap(true);
        var notice=label(app,"snake-notice");notice.setText("P / Space / Esc 暂停 · 双方继续后恢复");
        var guide=app.theme().label("你的操作\n"+new dev.gamecore.client.snake.SnakeControls(keys).label(0),app.theme().assets.small,ThemeManager.TEXT);guide.setName("snake-keys");
        var maps=app.theme().<String>selectBox();maps.setName("snake-map");maps.setItems("经典双人","障碍双人");host.register(maps);
        Table options=new Table();options.add(maps).colspan(2).growX().height(34).row();options.add(actions(buttons)).colspan(2).growX().row();
        var ranking=app.theme().button("排行榜",false);ranking.setName("snake-leaderboard");host.register(ranking);ranking.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener(){@Override public void changed(ChangeEvent event,Actor actor){host.ranking();}});options.add(ranking).colspan(2).growX().height(38).padTop(4);
        var panel=ArenaPanels.snake(app.theme(),"LIVE / 在线竞技","在线双人",score,best,speed,guide,options,status,notice);
        var scroll=ArenaPanels.scroll(app.theme(),panel);scroll.setName("snake-controls");var arena=new SnakeArenaPane(board,scroll);
        return new OnlineGameView(){
            Direction direction;boolean updating,recorded;String mapId="clear";int peak;
            {maps.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener(){@Override public void changed(ChangeEvent event,Actor actor){if(!updating){host.choose(Map.of("map",maps.getSelectedIndex()==0?"clear":"obstacles"),false);}}});}
            @Override public Actor actor(){return arena;}
            @Override public boolean key(int key,boolean down){Direction d=key==keys.up()?Direction.UP:key==keys.down()?Direction.DOWN:key==keys.left()?Direction.LEFT:key==keys.right()?Direction.RIGHT:null;if(d==null){return false;}if(down){direction=d;}return true;}
            @Override public byte[] command(){return direction==null?null:factory.inputs().encode(direction);}
            @Override public void snapshot(byte[] state){var next=factory.snapshots().decode(state);board.update(next);board.setVisible(true);score.setText(dev.gamecore.client.ui.ArenaHudText.snakeScores(next,true));speed.setText(dev.gamecore.client.ui.ArenaHudText.snakeSpeed(next));status.getStyle().fontColor=next.state()==SnakeGame.State.FINISHED?ThemeManager.GOLD:ThemeManager.MINT;peak=next.players().stream().mapToInt(SnakeSnapshot.Player::score).max().orElse(0);if(next.state()==SnakeGame.State.FINISHED&&!recorded){recorded=true;app.onlineSnakeRecords(mapId).recordFinishedRound(next);}}
            @Override public void connection(String value,int slot){status.setText(value);best.setText(slot<0?"等待分配玩家":"你是 P"+(slot+1)+" · 最佳 "+Math.max(peak,app.onlineSnakeRecords(mapId).best(SnakeMode.DUO)));}
            @Override public void room(dev.gamecore.protocol.GameProtocol.RoomState state,int slot){mapId=dev.gamecore.online.ProtocolValues.config(state.getConfig()).options().getOrDefault("map","clear");updating=true;maps.setSelectedIndex(mapId.equals("obstacles")?1:0);updating=false;maps.setDisabled(slot!=0||!java.util.Set.of("WAITING","SETUP").contains(state.getPhase()));}
            @Override public void openSelection(){if(!maps.isDisabled()){maps.showScrollPane();}}
            @Override public void events(List<dev.gamecore.api.event.GameEvent> events){for(var event:events){if(event instanceof dev.gamecore.api.event.GameEvent.ScoreChanged){app.assets().previewVolume(app.settings().volume());}}}
            @Override public void clear(){direction=null;}
            @Override public void newMatch(){clear();recorded=false;peak=0;board.setVisible(false);score.setText("P1  --\nP2  --");speed.setText("");}
            @Override public String hint(){return "房主可选经典/障碍场景。双方准备，P/Space/Esc 暂停；双方继续，重开需双方同意。";}
        };
    }
    private static OnlineGameView tank(GameCoreClient app,List<TextButton> buttons,OnlineGameView.Host host){
        var factory=new TankFactory();var board=new TankBoard(app.theme());board.setName("tank-board");var controls=new TankControls(app.settings().keys());
        var health=app.theme().label("P1  -- / 5     P2  -- / 5",app.theme().assets.heading,ThemeManager.MINT);health.setName("tank-health");
        var time=label(app,"tank-time");var status=app.theme().text("");status.setName("tank-state");status.setWrap(true);var identity=label(app,"tank-identity");
        var guide=label(app,"tank-keys");guide.setText("你的操作\n"+controls.label(0));var notice=label(app,"tank-notice");
        var maps=app.theme().<TankMapAsset.Arena>selectBox();maps.setName("tank-map");maps.setItems(TankMapAsset.Arena.values());host.register(maps);
        Table options=new Table();options.add(maps).growX().height(34).row();options.add(identity).growX().padTop(6);
        var panel=ArenaPanels.tank(app.theme(),"在线竞技","在线双人",new dev.gamecore.client.tank.TankScoreboard(app.theme(),board,"P1","P2"),health,time,status,options,guide,actions(buttons),notice);
        var arena=new TankArenaPane(board,ArenaPanels.scroll(app.theme(),panel));
        return new OnlineGameView(){
            TankMapAsset map;boolean updating,frozen,paused;
            dev.gamecore.tank.TankSnapshot authority;long epoch;
            final dev.gamecore.client.network.sync.ArenaSnapshotTimeline<dev.gamecore.tank.TankSnapshot> timeline=new dev.gamecore.client.network.sync.ArenaSnapshotTimeline<>();
            final dev.gamecore.client.effects.EffectWorld effects=new dev.gamecore.client.effects.EffectWorld();
            {maps.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener(){@Override public void changed(ChangeEvent event,Actor actor){if(!updating){host.choose(Map.of("map",maps.getSelected().name().toLowerCase(java.util.Locale.ROOT)),false);}}});}
            @Override public Actor actor(){return arena;}
            @Override public boolean key(int key,boolean down){if(!controls.accepts(key)){return false;}if(down){controls.down(key);}else{controls.up(key);}return true;}
            @Override public byte[] command(){return factory.inputs().encode(controls.command(0));}
            @Override public void snapshot(byte[] state){
                var next=factory.snapshots().decode(state);
                if(map!=null&&!map.logic().id().equals(next.map().id())){close();}
                if(map==null){try{map=new TankMapAsset(TankMapAsset.Arena.fromId(next.map().id()));if(!dev.gamecore.tank.TankMapFingerprint.digest(map.logic()).equals(dev.gamecore.tank.TankMapFingerprint.digest(next.map()))){close();throw new IllegalStateException("Map mismatch");}}catch(java.io.IOException e){throw new IllegalStateException(e);}}
                board.update(map,next);health.setText(dev.gamecore.client.ui.ArenaHudText.tankHealth(next));
                time.setText(dev.gamecore.client.ui.ArenaHudText.tankTime(next));
                status.getStyle().fontColor=next.state()==dev.gamecore.tank.TankGame.State.FINISHED?ThemeManager.GOLD:ThemeManager.TEXT;
                authority=next;
            }
            @Override public void snapshot(byte[] state,long arrival){snapshot(state);timeline.offer(authority.tick(),authority,arrival);}
            @Override public void connection(String value,int slot){status.setText(value);identity.setText(slot<0?"等待分配玩家":"你是 P"+(slot+1)+" · 地图由房间配置");}
            @Override public void room(dev.gamecore.protocol.GameProtocol.RoomState state,int slot){paused=state.getPaused();if(epoch!=state.getInputEpoch()||!state.getPhase().equals("RUNNING")){timeline.clear();epoch=state.getInputEpoch();}updating=true;maps.setSelected(TankMapAsset.Arena.fromId(dev.gamecore.online.ProtocolValues.config(state.getConfig()).options().get("map")));updating=false;maps.setDisabled(slot!=0||!java.util.Set.of("WAITING","SETUP").contains(state.getPhase()));}
            @Override public void frozen(boolean value){frozen=value;timeline.clear();}
            @Override public void advance(float delta){if(!frozen&&!paused){effects.update(delta);}board.updateEffects(effects.snapshot());}
            @Override public void advance(float delta,long now){if(authority!=null&&map!=null){board.update(map,!frozen&&!paused&&authority.state()==dev.gamecore.tank.TankGame.State.RUNNING?dev.gamecore.client.network.sync.ArenaMotion.tank(authority,timeline.frame(now)):authority);}advance(delta);}
            @Override public void events(List<dev.gamecore.api.event.GameEvent> events){for(var event:events){effects.accept(event);if(event instanceof dev.gamecore.api.event.GameEvent.Effect effect&&effect.kind()==dev.gamecore.api.event.GameEvent.Effect.Kind.HIT){app.assets().previewVolume(app.settings().volume());}}}
            @Override public void openSelection(){if(!maps.isDisabled()){maps.showScrollPane();}}
            @Override public void clear(){controls.clear();}
            @Override public String hint(){return "房主可选两张本地地图。双方准备，P/Esc 暂停；双方继续，重开需双方同意。";}
            @Override public void close(){if(map!=null){map.dispose();map=null;}authority=null;timeline.clear();effects.clear();board.updateEffects(List.of());board.update(null,null);}
            @Override public void newMatch(){clear();close();health.setText("P1  -- / 5     P2  -- / 5");time.setText("等待比赛开始");}
        };
    }
    /** 在线复用本地两页选择/开场/舞台；草稿确认只发送本人角色与房主地图。 */
    private static final class FightView implements OnlineGameView {
        private final dev.gamecore.online.fight.FightFactory factory=new dev.gamecore.online.fight.FightFactory();
        private final FightArenaView arena;
        private final FightControls controls=new FightControls();
        private final FightSelection selection=new FightSelection(dev.gamecore.fight.FightSessionFactory.Mode.DUEL);
        private final FightSelectionPanel panel;
        private final OnlineGameView.Host host;
        private final java.util.Set<Integer> laughHeld=new java.util.HashSet<>(),confirmHeld=new java.util.HashSet<>();
        private FightOpening opening=new FightOpening(0);
        private dev.gamecore.fight.FightSnapshot last;
        private final dev.gamecore.client.network.sync.ArenaSnapshotTimeline<dev.gamecore.fight.FightSnapshot> timeline=new dev.gamecore.client.network.sync.ArenaSnapshotTimeline<>();
        private long epoch;
        private dev.gamecore.protocol.GameProtocol.RoomState room;
        private int slot=-1;private boolean laugh,frozen,debug,entered,draftDuringMatch;
        private Map<String,String> staged=Map.of();
        private final FightIconPainter actionIcon;
        private String notice="";private boolean alert=true;
        FightView(GameCoreClient app,List<TextButton> buttons,OnlineGameView.Host host){
            this.host=host;actionIcon=decorate(app,buttons);arena=new FightArenaView(app.theme(),"在线双人",false,buttons);arena.board().setVisible(false);arena.board().opening(opening);
            panel=new FightSelectionPanel(app.theme(),selection,host::register,this::next,this::back);panel.setVisible(false);selection.cancel();
        }
        /** 与本地操作栏相同的线稿图标；取消请求/连接信息为在线专属按钮，保持纯文字。 */
        private static FightIconPainter decorate(GameCoreClient app,List<TextButton> buttons){
            FightIconPainter action=null;
            for(var button:buttons){
                var icon=switch(String.valueOf(button.getName())){case "online-action"->FightIcon.PLAY;case "online-select"->FightIcon.MAP;case "online-restart"->FightIcon.RESTART;case "online-back"->FightIcon.HOME;default->null;};
                if(icon!=null){var painter=FightIconPainter.decorate(app.theme(),button,icon,false);if(icon==FightIcon.PLAY){action=painter;}}
            }
            return action;
        }
        @Override public Actor actor(){return arena;}
        @Override public Actor selectionActor(){return panel;}
        @Override public boolean selecting(){return panel.isVisible();}
        @Override public void openSelection(){
            if(slot<0||room==null||room.getPhase().equals("RESETTING")){return;}
            draftDuringMatch=!java.util.Set.of("WAITING","SETUP").contains(room.getPhase());
            clear();selection.open();panel.online(slot);panel.resumePreview();panel.setVisible(true);focusNext();
        }
        private void focusNext(){if(panel.getStage()!=null){panel.getStage().setKeyboardFocus(panel.findActor("select-next"));}}
        private void next(){
            if(!selection.active()){return;}
            if(!selection.next()){panel.sync();focusNext();return;}
            var config=selection.committed();
            var choices=slot==0?Map.of("one",config.one().name(),"map",config.map().name()):Map.of("two",config.two().name());
            if(draftDuringMatch){staged=choices;}host.choose(choices,true);draftDuringMatch=false;
            panel.setVisible(false);if(panel.getStage()!=null){panel.getStage().setKeyboardFocus(null);}
        }
        private void back(){if(selection.page()==FightSelection.Page.MAPS){selection.fighters();panel.sync();focusNext();}else{selection.cancel();draftDuringMatch=false;panel.setVisible(false);if(panel.getStage()!=null){panel.getStage().setKeyboardFocus(null);}}}
        @Override public void cancelReconfigure(){staged=Map.of();}
        @Override public boolean selectionKey(int key,boolean down){
            if(!down){confirmHeld.remove(key);return false;}
            if(confirmHeld.contains(key)){return true;}if(!selecting()){return false;}
            panel.resumePreview();
            if(key==Input.Keys.ENTER||key==Input.Keys.SPACE){
                confirmHeld.add(key);var focus=panel.getStage().getKeyboardFocus();
                if(focus instanceof Button button&&button!=panel.findActor("select-next")){if(!button.isDisabled()){button.setChecked(!button.isChecked());}}
                else{next();}return true;
            }
            if(key==Input.Keys.ESCAPE){back();return true;}if(key==Input.Keys.TAB){return false;}
            int delta=key==Input.Keys.A||key==Input.Keys.LEFT||key==Input.Keys.Q?-1:key==Input.Keys.D||key==Input.Keys.RIGHT||key==Input.Keys.E?1:0;
            if(delta!=0){if(selection.page()==FightSelection.Page.FIGHTERS){selection.cycleFighter(slot,delta);}else if(slot==0){selection.cycleMap(delta);}panel.sync();}
            return true;
        }
        @Override public boolean key(int key,boolean down){
            if(key==Input.Keys.F1){if(down){debug=!debug;arena.board().debug(debug);}return true;}
            if(FightLaughInput.accepts(key)){if(down){if(laughHeld.add(key)){laugh=true;}}else{laughHeld.remove(key);}return true;}
            if(!controls.accepts(key)){return false;}if(down){controls.down(key);}else{controls.up(key);}return true;
        }
        @Override public byte[] command(){var value=controls.command(0).withLaugh(laugh);laugh=false;return factory.inputs().encode(value);}
        @Override public void snapshot(byte[] state){last=factory.snapshots().decode(state);arena.update(last,frozen||room!=null&&room.getPaused());arena.board().setVisible(true);}
        @Override public void snapshot(byte[] state,long arrival){snapshot(state);timeline.offer(last.tick(),last,arrival);}
        @Override public void connection(String value,int player){connection(value,player,true);}
        @Override public void connection(String value,int player,boolean alert){notice=value;this.alert=alert;slot=player;if(player>=0){arena.localPlayer(player);}status();}
        /** 正常阶段沿用本地状态栏文案；错误、重开请求、复位和中断显示连接提示。 */
        private void status(){
            String text=null;
            if(!alert&&room!=null&&last!=null){
                boolean skip=false,resume=false,restart=false;
                for(var seat:room.getSeatsList()){restart|=seat.getRestart();if(seat.getSlot()==slot){skip=seat.getSkip();resume=seat.getResume();}}
                text=FightStatusText.online(room.getPhase(),room.getPaused(),skip,resume,restart,last,notice);
            }
            if(text==null||text.equals(notice)){text=notice+(slot<0?"":" · 你是 P"+(slot+1));}
            arena.status().setText(text);
        }
        @Override public void room(dev.gamecore.protocol.GameProtocol.RoomState state,int player){
            room=state;slot=player;var options=dev.gamecore.online.ProtocolValues.config(state.getConfig()).options();
            if(epoch!=state.getInputEpoch()||!state.getPhase().equals("RUNNING")){timeline.clear();epoch=state.getInputEpoch();}
            var config=new dev.gamecore.fight.FightSessionFactory.Config(dev.gamecore.fight.FighterKind.valueOf(options.get("one")),dev.gamecore.fight.FighterKind.valueOf(options.get("two")),dev.gamecore.fight.FightSessionFactory.Mode.DUEL,dev.gamecore.fight.FightMap.valueOf(options.get("map")));
            selection.observe(config,slot);panel.online(slot);
            if(!entered&&slot>=0&&java.util.Set.of("WAITING","SETUP").contains(state.getPhase())){
                entered=true;openSelection();
                if(!staged.isEmpty()){
                    if(staged.containsKey("one")){selection.choose(0,dev.gamecore.fight.FighterKind.valueOf(staged.get("one")));}
                    if(staged.containsKey("two")){selection.choose(1,dev.gamecore.fight.FighterKind.valueOf(staged.get("two")));}
                    if(staged.containsKey("map")){selection.choose(dev.gamecore.fight.FightMap.valueOf(staged.get("map")));}staged=Map.of();panel.sync();
                }
            }
            if(state.getPhase().equals("RESETTING")||!draftDuringMatch&&!java.util.Set.of("WAITING","SETUP").contains(state.getPhase())){panel.setVisible(false);}
            if(state.getPhase().equals("OPENING")&&last!=null&&last.map()==config.map()&&last.fighters().get(0).kind()==config.one()&&last.fighters().get(1).kind()==config.two()){
                if(opening.phase()==FightOpening.Phase.IDLE){opening=new FightOpening(state.getOpeningSeed()^state.getMatchId().hashCode());opening.start(last);arena.board().opening(opening);}
                opening.synchronize(state.getOpeningMillis()/1000d,frozen||state.getPaused());
            }else if(!java.util.Set.of("WAITING","SETUP").contains(state.getPhase())&&opening.active()&&!state.getPhase().equals("OPENING")){opening.synchronize(FightOpening.TOTAL_SECONDS,false);}
            boolean pause=frozen||state.getPaused();for(int p=0;p<2;p++){((FightHud)arena.findActor("fight-hud-"+p)).paused(pause);}
            if(pause){arena.board().victory().pause();}else{arena.board().victory().resume();}
            arena.setVisible(!panel.isVisible());
            boolean held=state.getPaused();String phase=state.getPhase();
            if(actionIcon!=null){actionIcon.icon(!held&&java.util.Set.of("RUNNING","OPENING").contains(phase)?FightIcon.PAUSE:!held&&java.util.Set.of("FINISHED","ABORTED","FAILED").contains(phase)?FightIcon.RESTART:FightIcon.PLAY);}
            status();
        }
        @Override public void clear(){controls.clear();laugh=false;laughHeld.clear();}
        @Override public void newMatch(){clear();confirmHeld.clear();timeline.clear();last=null;room=null;entered=draftDuringMatch=false;selection.cancel();panel.setVisible(false);arena.reset();arena.board().setVisible(false);opening.reset();}
        /** 同房重开沿用已确认角色/地图，不再弹出选择页；需要改选时由“角色 / 地图”按钮显式打开。 */
        @Override public void rematch(){newMatch();entered=true;staged=Map.of();}
        @Override public void frozen(boolean value){frozen=value;timeline.clear();if(value){confirmHeld.clear();panel.pausePreview();}else{panel.resumePreview();}}
        @Override public void advance(float delta){if(!frozen&&(room==null||!room.getPaused())){arena.board().advance(delta);}}
        @Override public void advance(float delta,long now){if(last!=null){arena.board().displayMotion(!frozen&&room!=null&&room.getPhase().equals("RUNNING")&&!room.getPaused()?dev.gamecore.client.network.sync.ArenaMotion.fight(last,timeline.frame(now)):last);}advance(delta);}
        @Override public String hint(){return "选择自己的斗士，房主选择战场；双方准备后登场。P/Esc 暂停，双方继续；重开需双方同意。";}
    }
}
