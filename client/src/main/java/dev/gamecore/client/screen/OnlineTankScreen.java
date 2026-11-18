package dev.gamecore.client.screen;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.Actor;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.network.ConnectionState;
import dev.gamecore.client.network.RemoteGameSession;
import dev.gamecore.client.network.ServerEndpoint;
import dev.gamecore.client.tank.TankArenaPane;
import dev.gamecore.client.tank.TankBoard;
import dev.gamecore.client.tank.TankControls;
import dev.gamecore.client.tank.TankMapAsset;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.tank.TankMapFingerprint;
import dev.gamecore.tank.TankSnapshot;
import dev.gamecore.network.experiment.NetworkProfile;
import java.io.IOException;
import java.util.Locale;

/**
 * 在线页面独占连接与地图纹理。所有Stage/Board操作都在render或Scene2D回调；网络没有页面引用。
 * 不创建本地TankSession、不暂停服务器、不切换服务器地图；失焦发空输入，离页关闭连接。
 */
public final class OnlineTankScreen extends UiScreen {
    private final TankControls controls;
    private final TankBoard board;
    private final Label status, health, time, identity;
    private final TextField host, port, name;
    private final TextButton connect;
    private final Table debugPanel = new Table();
    private final Cell<Table> debugCell;
    private final Table connectionForm=new Table();
    private final Cell<Table> connectionCell;
    private final Label debugMetrics;
    private final TextButton predictionToggle, interpolationToggle;
    private final SelectBox<Integer> simulationRtt, simulationJitter, simulationDrop, interpolationDelay;
    private boolean debugVisible, predictionSetting = true, interpolationSetting = true;
    private RemoteGameSession remote;
    private TankMapAsset mapAsset;
    private boolean mapFailed;
    private String localNotice = "输入服务器地址，开始在线双人游戏";
    public OnlineTankScreen(GameCoreClient app) {
        super(app, "TANK ARENA / ONLINE", "Esc 返回大厅 · 联机比赛不可暂停", true);
        controls = new TankControls(app.settings().keys()); board = new TankBoard(theme); board.setName("tank-board");
        status = theme.text(localNotice); status.setWrap(true); status.setName("online-state");
        health = theme.label("P1  -- / 5     P2  -- / 5", theme.assets.heading, ThemeManager.MINT); health.setName("online-health");
        time = theme.caption("等待比赛开始"); time.setName("online-time");
        identity = theme.caption("连接后由服务器分配玩家身份"); identity.setWrap(true); identity.setName("online-identity");
        host = field("online-host", System.getProperty("gamecore.serverHost", "127.0.0.1"), 253);
        port = field("online-port", System.getProperty("gamecore.serverPort", "7100"), 5);
        port.setTextFieldFilter(new TextField.TextFieldFilter.DigitsOnlyFilter());
        name = field("online-name", System.getProperty("gamecore.playerName", "player"), 64);
        connect = bind(theme.button("连接服务器", true), "online-connect", this::connect);
        debugMetrics = theme.caption("RTT --\nTick -- / Ack --"); debugMetrics.setWrap(true); debugMetrics.setName("online-debug-metrics");
        predictionToggle = bind(theme.button("Prediction ON", false), "online-prediction", () -> {
            predictionSetting = !predictionSetting; if (remote != null) { remote.predictionEnabled(predictionSetting); } refreshDebug();
        });
        interpolationToggle = bind(theme.button("Interpolation ON", false), "online-interpolation", () -> {
            interpolationSetting = !interpolationSetting; if (remote != null) { remote.interpolationEnabled(interpolationSetting); } refreshDebug();
        });
        simulationRtt = selection("online-sim-rtt", 0, 50, 100, 200);
        simulationJitter = selection("online-sim-jitter", 0, 20, 50);
        simulationDrop = selection("online-sim-drop", 0, 5, 10);
        simulationRtt.setSelected(Integer.getInteger("gamecore.network.rttMs", 0));
        simulationJitter.setSelected(Integer.getInteger("gamecore.network.jitterMs", 0));
        simulationDrop.setSelected(Integer.getInteger("gamecore.network.dropPercent", 0));
        interpolationDelay = selection("online-interpolation-delay", 0, 50, 100, 150, 200, 300);
        interpolationDelay.setSelected(100);
        interpolationDelay.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (remote != null) { remote.interpolationBufferMillis(interpolationDelay.getSelected()); }
            }
        });
        debugPanel.top(); debugPanel.defaults().growX().padTop(4);
        debugPanel.add(debugMetrics).row(); debugPanel.add(predictionToggle).height(34).row(); debugPanel.add(interpolationToggle).height(34).row();
        debugPanel.add(theme.caption("Interpolation buffer / ms")).row(); debugPanel.add(interpolationDelay).height(32).row();
        if (Boolean.getBoolean("gamecore.network.dev")) {
            debugPanel.add(theme.caption("LOCAL DEV ONLY / next connection")).row();
            debugPanel.add(theme.caption("RTT / ms")).row(); debugPanel.add(simulationRtt).height(32).row();
            debugPanel.add(theme.caption("Jitter / ms")).row(); debugPanel.add(simulationJitter).height(32).row();
            debugPanel.add(theme.caption("Application drop / %")).row(); debugPanel.add(simulationDrop).height(32).row();
        }
        Table options=new Table();options.add(identity).growX().row();
        options.add(bind(theme.button("F3 / 网络详情",false),"online-debug-toggle",this::toggleDebug)).growX().height(34).padTop(8).row();
        debugCell=options.add(debugPanel).growX().height(0);debugPanel.setVisible(false);
        connectionForm.add(theme.caption("服务器地址")).left().row();connectionForm.add(host).growX().height(36).row();
        connectionForm.add(theme.caption("端口")).left().padTop(8).row();connectionForm.add(port).growX().height(36).row();
        connectionForm.add(theme.caption("玩家名称")).left().padTop(8).row();connectionForm.add(name).growX().height(36).row();
        connectionForm.add(connect).growX().height(40).padTop(12);
        Table actions=new Table();connectionCell=actions.add(connectionForm).growX();actions.row();
        actions.add(bind(theme.button("返回大厅",false),"return-home",app::home)).growX().height(38).padTop(6);
        var keys=theme.caption("你的操作\n"+controls.label(0));
        var hint=theme.caption("切换窗口后比赛仍会继续\n连接中断后，请返回大厅重新开始");hint.setWrap(true);
        Table panel=dev.gamecore.client.ui.ArenaPanels.tank(theme,"在线竞技","在线双人",new dev.gamecore.client.tank.TankScoreboard(theme,board,"P1","P2"),health,time,status,options,keys,actions,hint);
        panel.getCell(hint).height(48).padTop(12);
        var scroll=dev.gamecore.client.ui.ArenaPanels.scroll(theme,panel);
        content.add(new TankArenaPane(board, scroll)).grow(); stage.setKeyboardFocus(host);
    }
    private SelectBox<Integer> selection(String id, Integer... values) {
        SelectBox<Integer> box = focus(theme.selectBox(), id); box.setItems(values); return box;
    }
    private void toggleDebug() {
        debugVisible = !debugVisible; debugPanel.setVisible(debugVisible);
        if (debugVisible) { debugCell.height(debugPanel.getPrefHeight()); } else { debugCell.height(0); }
        updateConnectionForm();content.invalidateHierarchy();
    }
    private void updateConnectionForm(){
        boolean visible=remote==null||remote.state().terminal()||debugVisible;
        float height=visible?connectionForm.getPrefHeight():0;
        if(connectionForm.isVisible()!=visible||Math.abs(connectionCell.getPrefHeight()-height)>.5f){
            connectionForm.setVisible(visible);connectionCell.height(height);content.invalidateHierarchy();
        }
    }
    private TextField field(String id, String text, int maximum) {
        var field = focus(theme.searchField(), id); field.setMessageText(""); field.setMaxLength(maximum);
        field.setFocusTraversal(false); field.setText(text); return field;
    }
    private void connect() {
        if (remote != null && !remote.state().terminal()) { return; }
        try {
            var endpoint = new ServerEndpoint(host.getText(), Integer.parseInt(port.getText()));
            if (name.getText().isBlank()) { throw new IllegalArgumentException("请输入玩家名称"); }
            if (remote != null) { remote.close(); }
            if (mapAsset != null) { mapAsset.dispose(); mapAsset = null; board.update(null, null); }
            controls.clear(); localNotice = ""; mapFailed = false;
            var profile = new NetworkProfile(simulationRtt.getSelected(), simulationJitter.getSelected(), simulationDrop.getSelected(),
                    Long.getLong("gamecore.network.seed", 42), Integer.getInteger("gamecore.network.blackoutStartMs", 2500),
                    Integer.getInteger("gamecore.network.blackoutMs", 0));
            remote = new RemoteGameSession(profile, app.trafficObserver()); remote.predictionEnabled(predictionSetting); remote.interpolationEnabled(interpolationSetting);
            remote.interpolationBufferMillis(interpolationDelay.getSelected()); remote.connect(endpoint, name.getText());
            stage.setKeyboardFocus(null); refresh();
        } catch (IllegalArgumentException failure) { localNotice = failure instanceof NumberFormatException ? "请输入1到65535的端口" : failure.getMessage(); status.setText(localNotice); }
        catch (RuntimeException failure) { if (remote != null) { remote.close(); } localNotice = "无法建立连接，请检查客户端安装后重试"; status.setText(localNotice); }
    }
    public RemoteGameSession remote() { return remote; }
    public TankSnapshot snapshot() { return remote == null ? null : remote.snapshot(); }
    public TankMapAsset mapAsset() { return mapAsset; }
    public TankControls controls() { return controls; }
    @Override public void render(float delta) {
        if (remote != null) {
            remote.update(controls.command(0), System.nanoTime());
            var view = remote.snapshot();
            if (view != null && mapAsset == null && !mapFailed && !remote.state().equals(ConnectionState.CLOSED)) {
                try {
                    var candidate = new TankMapAsset(TankMapAsset.Arena.fromId(view.map().id()));
                    if (!TankMapFingerprint.digest(candidate.logic()).equals(TankMapFingerprint.digest(view.map()))) {
                        candidate.dispose(); throw new IllegalArgumentException("地图版本不一致，请更新客户端");
                    }
                    mapAsset = candidate; content.invalidateHierarchy();
                } catch (IOException | RuntimeException failure) {
                    mapFailed = true; localNotice = "无法显示服务器地图，请检查客户端安装";
                    remote.fail(ConnectionState.FAILED, localNotice);
                }
            }
            if (view != null && mapAsset != null) { board.update(mapAsset, view); }
            refresh();
        }
        super.render(delta);
    }
    private void refresh() {
        if (remote == null) { return; }
        boolean busy = !remote.state().terminal();
        connect.setDisabled(busy); connect.setText(busy ? remote.slot() < 0 ? "连接中" : "已连接" : "重新连接");
        host.setDisabled(busy); port.setDisabled(busy); name.setDisabled(busy);
        status.setText(localNotice.isEmpty() ? remote.message() : localNotice);
        identity.setText(remote.slot() < 0 ? "连接后由服务器分配玩家身份" : "你是 P" + (remote.slot() + 1) + " · 使用下方按键操作");
        var view = remote.snapshot();
        if (view != null) {
            health.setText(dev.gamecore.client.ui.ArenaHudText.tankHealth(view));
            time.setText(dev.gamecore.client.ui.ArenaHudText.tankTime(view));
        }
        updateConnectionForm();
        if (remote.state().terminal()) { controls.clear(); }
        simulationRtt.setDisabled(busy); simulationJitter.setDisabled(busy); simulationDrop.setDisabled(busy); refreshDebug();
    }
    private void refreshDebug() {
        predictionToggle.setText("Prediction " + (predictionSetting ? "ON" : "OFF"));
        interpolationToggle.setText("Interpolation " + (interpolationSetting ? "ON" : "OFF"));
        if (remote == null || !debugVisible) { return; }
        var net = remote.network().metrics(); var prediction = remote.predictionMetrics();
        debugMetrics.setText(String.format(Locale.ROOT,
                "RTT %.1f ms%nTick %d / Ack %d%nSnapshot %.1f ms / %.1f Hz%nPrediction error %.4f%nCorrections %d%nPending input %d / 128%nNet writes %d / 32%nReplay %d / 60%nServer prediction: %s",
                net.rttNanos() / 1_000_000.0, remote.serverTick(), remote.acknowledgedSequence(), net.snapshotIntervalMillis(), net.snapshotsPerSecond(),
                prediction.error(), prediction.corrections(), prediction.pendingInputs(), remote.network().pendingWrites(), prediction.replayTicks(), remote.predictionSupported() ? "YES" : "NO"));
        float needed = debugPanel.getPrefHeight();
        if (Math.abs(debugPanel.getHeight() - needed) > .5f) { debugCell.height(needed); content.invalidateHierarchy(); }
    }
    private boolean controlKey(int key) {
        var keys = controls.keys(0);
        return key == Input.Keys.SPACE || key == keys.up() || key == keys.down() || key == keys.left() || key == keys.right();
    }
    @Override protected boolean handleKey(int key) {
        if (key == Input.Keys.F3) { toggleDebug(); return true; }
        if (key == Input.Keys.ESCAPE) { controls.clear(); app.home(); return true; }
        if (remote != null && remote.state() == ConnectionState.RUNNING && controlKey(key)) { controls.down(key); return true; }
        return false;
    }
    @Override protected boolean handleKeyUp(int key) { controls.up(key); return remote != null && remote.state() == ConnectionState.RUNNING && controlKey(key); }
    @Override public void pause() { controls.clear(); if (remote != null) { remote.releaseInput(); } }
    @Override public void hide() { controls.clear(); if (remote != null) { remote.close(); } super.hide(); }
    @Override public void dispose() { if (isDisposed()) { return; } hide(); if (mapAsset != null) { mapAsset.dispose(); } super.dispose(); }
}
