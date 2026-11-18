package dev.gamecore.client.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import dev.gamecore.api.session.PlayerInput;
import dev.gamecore.api.session.SessionLoop;
import dev.gamecore.api.session.SessionState;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.fight.FightBoard;
import dev.gamecore.client.fight.FightControls;
import dev.gamecore.client.fight.FightHud;
import dev.gamecore.client.fight.FightOpening;
import dev.gamecore.client.fight.FightDisplayLabel;
import dev.gamecore.client.timing.FixedStepClock;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.fight.FightCommand;
import dev.gamecore.fight.FightSession;
import dev.gamecore.fight.FightSessionFactory;
import dev.gamecore.fight.FightSnapshot;
import dev.gamecore.client.fight.FightVictory;
import dev.gamecore.client.fight.FightSelection;
import dev.gamecore.client.fight.FightSelectionPanel;
import dev.gamecore.client.fight.FightLaughInput;
import dev.gamecore.client.fight.FightChrome;
import dev.gamecore.client.fight.FightIcon;
import dev.gamecore.client.fight.FightIconPainter;
import dev.gamecore.client.fight.FightUiSkin;
import dev.gamecore.client.fight.FightHudModel;

/** 格斗页面装配：两页出战选择确认后建局；Stage 收集输入，固定步长驱动纯 Java 会话。
 * 重选使用草稿，先暂停/清键，取消保留原局；每窗口独立拥有会话、演出及事件订阅。
 */
public final class FightScreen extends UiScreen {
    private final FightSessionFactory.Mode mode;
    private final FightControls controls = new FightControls();
    private final FightLaughInput laughInput=new FightLaughInput();
    private final FightOpening opening = new FightOpening(System.nanoTime());
    private final FixedStepClock clock = new FixedStepClock(FightSession.TICKS_PER_SECOND);
    private final FightBoard board;
    private final dev.gamecore.client.fight.FightArenaView arena;
    private final FightSelection selection;
    private final FightSelectionPanel selectionPanel;
    private final Table battle=new Table();
    private final java.util.Set<Integer> selectionKeys=new java.util.HashSet<>();
    private final Label status;
    private final TextButton action;
    private final FightIconPainter actionIcon;
    private FightSession session;
    private SessionLoop<FightCommand, FightSnapshot> loop;
    private FightSnapshot view;
    private boolean debug,enteredArena;
    public FightScreen(GameCoreClient app, String modeId) {
        super(app, "终极格斗", "J 出拳 · U 远程 · I 大招 · S 格挡", true);
        mode = FightSessionFactory.Mode.fromId(modeId);
        selection=new FightSelection(mode);
        action = bind(theme.button("开始对决",true),"fight-action",this::activate);
        actionIcon=FightIconPainter.decorate(theme,action,FightIcon.PLAY,false);
        var select=bind(theme.button("角色 / 地图",false),"fight-select",this::openSelection);FightIconPainter.decorate(theme,select,FightIcon.MAP,false);
        var again=bind(theme.button("重新开始",false),"fight-restart",this::restart);FightIconPainter.decorate(theme,again,FightIcon.RESTART,false);
        var home=bind(theme.button("返回大厅",false),"return-home",app::home);FightIconPainter.decorate(theme,home,FightIcon.HOME,false);
        arena=new dev.gamecore.client.fight.FightArenaView(theme,mode==FightSessionFactory.Mode.BOT?"人机竞技":"本地双人",mode==FightSessionFactory.Mode.BOT,java.util.List.of(action,select,again,home));
        board=arena.board();board.opening(opening);status=arena.status();battle.add(arena).grow();
        selectionPanel=new FightSelectionPanel(theme,selection,b->focus(b,b.getName()),this::selectionNext,this::selectionBack);
        Stack layers=new Stack();layers.add(battle);layers.add(selectionPanel);content.add(layers).grow();
        restart();showSelection(true);
    }
    public FightSelection selection(){return selection;}
    public FightSelectionPanel selectionPanel(){return selectionPanel;}
    private void showSelection(boolean visible){battle.setVisible(!visible);selectionPanel.setVisible(visible);selectionPanel.sync();
        stage.cancelTouchFocus();stage.setKeyboardFocus(visible?stage.getRoot().findActor("select-next"):action);}
    private void openSelection(){pause();selectionKeys.clear();selection.open();selectionPanel.resumePreview();showSelection(true);}
    private void selectionNext(){if(selection.next()){enteredArena=true;showSelection(false);restart();opening.start(view);refresh();}
        else{selectionPanel.sync();stage.setKeyboardFocus(stage.getRoot().findActor("select-next"));}}
    private void selectionBack(){if(selection.page()==FightSelection.Page.MAPS){selection.fighters();selectionPanel.sync();stage.setKeyboardFocus(stage.getRoot().findActor("select-next"));}
        else if(enteredArena){selection.cancel();showSelection(false);refresh();}else{app.home();}}
    public FightSnapshot snapshot() { return view; }
    public FightControls controls() { return controls; }
    public int particleCount() { return board.particles(); }
    public dev.gamecore.client.fight.FightImpactFeedback impactFeedback(){return board.feedback();}
    public dev.gamecore.client.fight.FightSpecialView.Focus specialFocus(){return board.specialFocus();}
    public float cameraZoom(){return board.cameraZoom();}
    public boolean boxingLoaded() { return board.boxingLoaded(); }
    public boolean greetingsLoaded() { return board.greetingsLoaded(); }
    public FightOpening opening() { return opening; }
    public FightVictory victory(){return board.victory();}
    public boolean victoryLoaded(){return board.victoryLoaded();}
    public boolean laughLoaded(){return board.laughLoaded();}
    public boolean sceneryMaterialsLoaded(){return board.sceneryMaterialsLoaded();}
    public int laughFocus(){return board.laughFocus();}
    public long slowFrames() { return clock.slowFrames(); }
    private void restart() {
        controls.clear();laughInput.clear(); clock.restart(); opening.reset(); arena.reset();
        if (loop != null) { loop.events().clear(); }
        session = new FightSessionFactory().create(selection.committed(), 73);
        loop = new SessionLoop<>(session); refresh();
    }
    private void activate() {
        controls.clear();laughInput.clear(); clock.reset();
        if (opening.active()) { if (opening.paused()) { opening.resume(); } else { opening.pause(); } stage.setKeyboardFocus(action); refresh(); return; }
        switch (session.state()) {
            case READY -> opening.start(view); case RUNNING -> session.pause(); case PAUSED -> session.resume();
            case FINISHED -> { restart(); opening.start(view); }
        }
        stage.setKeyboardFocus(action); refresh();
    }
    private void refresh() {
        view=session.snapshot();
        arena.update(view,view.state()==SessionState.PAUSED||opening.paused()||victory().paused());
        status.setText(dev.gamecore.client.fight.FightStatusText.local(view));
        action.setText(switch (view.state()) { case READY -> "开始对决"; case RUNNING -> "暂停"; case PAUSED -> "继续对决"; case FINISHED -> "再来一局"; });
        actionIcon.icon(view.state()==SessionState.RUNNING||opening.active()&&!opening.paused()?FightIcon.PAUSE:view.state()==SessionState.FINISHED?FightIcon.RESTART:FightIcon.PLAY);
        if (opening.active()) { status.setText(opening.paused() ? "Ⅱ 开场已暂停 · P / Esc 继续" : "登场演出  ·  Enter / Space 跳过开场"); action.setText(opening.paused() ? "继续开场" : "暂停开场"); }
        if(view.state()==SessionState.FINISHED&&victory().paused()){status.setText("Ⅱ 庆祝已暂停 · P / Esc 继续 · 可重新开始");}
    }
    @Override protected boolean handleKey(int key) {
        // 确认键需释放后再生效，防止系统按键重复把地图页和开场一起跳过。
        if(selectionKeys.contains(key)){return true;}
        if(selection.active()){
            selectionPanel.resumePreview();
            if(key==Input.Keys.ENTER||key==Input.Keys.SPACE){selectionKeys.add(key);
                Actor focused=stage.getKeyboardFocus();if(focused instanceof com.badlogic.gdx.scenes.scene2d.ui.Button button&&button!=stage.getRoot().findActor("select-next")){button.setChecked(!button.isChecked());}
                else{selectionNext();}return true;}
            if(key==Input.Keys.ESCAPE){selectionKeys.add(key);selectionBack();return true;}
            if(selection.page()==FightSelection.Page.FIGHTERS){
                if(key==Input.Keys.A||key==Input.Keys.D){selection.cycleFighter(0,key==Input.Keys.D?1:-1);return true;}
                if(key==Input.Keys.LEFT||key==Input.Keys.RIGHT){selection.cycleFighter(1,key==Input.Keys.RIGHT?1:-1);return true;}
            }else{if(key==Input.Keys.LEFT||key==Input.Keys.RIGHT||key==Input.Keys.Q||key==Input.Keys.E){selection.cycleMap(key==Input.Keys.RIGHT||key==Input.Keys.E?1:-1);return true;}}
            return controls.accepts(key)||FightLaughInput.accepts(key)||key==Input.Keys.P||key==Input.Keys.F1;
        }
        if (key == Input.Keys.F1) { debug = !debug; board.debug(debug); return true; }
        if (opening.active() && (key == Input.Keys.ENTER || key == Input.Keys.SPACE)) { if (opening.skip()) { beginCombat(); } return true; }
        if (key == Input.Keys.P || key == Input.Keys.ESCAPE) {
            if(view.state()==SessionState.FINISHED){if(victory().paused()){victory().resume();}else{victory().pause();}refresh();}
            else{activate();}return true;
        }
        if(FightLaughInput.accepts(key)){if(!opening.active()&&view.state()==SessionState.RUNNING){laughInput.down(key,view,mode);}return true;}
        if (opening.active() && controls.accepts(key)) { return true; }
        if (controls.accepts(key) && view.state() == SessionState.RUNNING) { controls.down(key); return true; }
        return false;
    }
    @Override protected boolean handleKeyUp(int key) { boolean held=selectionKeys.remove(key);controls.up(key);laughInput.up(key);return held||controls.accepts(key)||FightLaughInput.accepts(key); }
    @Override public void render(float delta) {
        if(selection.active()){clock.reset();super.render(delta);return;}
        if (opening.active()) {
            clock.reset(); var phase = opening.phase();
            if (opening.advance(delta)) { beginCombat(); }
            else if (phase != opening.phase()) { refresh(); }
            board.advance(delta); super.render(delta); return;
        }
        if (view.state() == SessionState.RUNNING) {
            int count = clock.advance(delta);
            for (int i = 0; i < count; i++) {
                session.submitInput(new PlayerInput<>(session.tickNumber() + 1, 0, 0, controls.command(0).withLaugh(laughInput.consume(0))));
                if (mode == FightSessionFactory.Mode.DUEL) { session.submitInput(new PlayerInput<>(session.tickNumber() + 1, 1, 1, controls.command(1).withLaugh(laughInput.consume(1)))); }
                for (var failure : loop.tick()) { Gdx.app.error("Fight", "Event consumer failed", failure); }
                if (session.isFinished()) { controls.clear();laughInput.clear(); clock.reset(); break; }
            }
            if (count > 0) { refresh(); }
        } else { clock.reset(); }
        board.advance(delta); super.render(delta);
    }
    /** 完整开场/显式跳过后开始规则，不追赶演出耗时，也不继承演出时按下的技能。 */
    private void beginCombat() { controls.clear();laughInput.clear(); clock.reset(); session.start(); refresh(); }
    @Override public void pause() { controls.clear();laughInput.clear();selectionKeys.clear();clock.reset();if(selectionPanel!=null){selectionPanel.pausePreview();}if (session != null) { if (opening.active()) { opening.pause(); } else if(session.isFinished()){victory().pause();}else { session.pause(); } refresh(); } }
    @Override public void hide() { pause(); super.hide(); }
    @Override public void dispose() { if (isDisposed()) { return; } if (loop != null) { loop.events().clear(); } controls.clear();laughInput.clear(); super.dispose(); }
}
