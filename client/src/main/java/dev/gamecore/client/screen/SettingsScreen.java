package dev.gamecore.client.screen;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.settings.ClientSettings;
import dev.gamecore.client.settings.KeyBindings;
import dev.gamecore.client.ui.ThemeManager;
import java.util.EnumMap;
import java.util.Map;

/** 设置草稿界面：应用才落盘，返回直接放弃；按键捕获拦截导航以支持 Esc 取消。 */
public final class SettingsScreen extends UiScreen {
    private final Slider volume;
    private final SelectBox<ClientSettings.Resolution> resolution;
    private final TextButton vsync;
    private final Label status;
    private final Map<KeyBindings.Action, TextButton> keyButtons = new EnumMap<>(KeyBindings.Action.class);
    private KeyBindings keys;
    private KeyBindings.Action capturing;

    public SettingsScreen(GameCoreClient app) {
        super(app, "偏好设置");
        keys = app.settings().keys();
        content.add(theme.label("打造你的游玩体验", theme.assets.title, ThemeManager.TEXT)).left().row();
        content.add(theme.caption("声音、画面与操作，按你的习惯调整。")).left().padTop(6).padBottom(14).row();
        Table form = new Table();
        form.top();
        Table sound = section("01  声音");
        volume = focus(theme.volumeSlider(), "volume");
        volume.setValue(app.settings().volume());
        Label value = theme.text(app.settings().volume() + "%");
        volume.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { value.setText(Math.round(volume.getValue()) + "%"); }
        });
        Table audio = new Table();
        audio.add(volume).width(260).padRight(16);
        audio.add(value).width(54);
        TextButton audition = bind(theme.button("试听", false), "audition", () -> app.assets().previewVolume(Math.round(volume.getValue())));
        audition.setDisabled(!app.assets().audioAvailable());
        audio.add(audition).height(36).width(80);
        row(sound, "主音量", "试听提示音，调整到舒适的音量。", audio);
        form.add(sound).growX().padBottom(10).row();
        Table display = section("02  显示");
        resolution = focus(theme.selectBox(), "resolution");
        resolution.setItems(ClientSettings.Resolution.values());
        resolution.setSelected(app.settings().resolution());
        row(display, "窗口尺寸", "保持完整画面，自动适配窗口缩放。", resolution);
        vsync = bind(theme.toggle("垂直同步"), "vsync", this::refreshVsync);
        vsync.setChecked(app.settings().vsync());
        refreshVsync();
        row(display, "垂直同步", "同步屏幕刷新，减少画面撕裂。", vsync);
        form.add(display).growX().padBottom(10).row();
        Table operation = section("03  操作");
        Table controls = new Table();
        status = theme.caption("");
        status.setName("settings-status");
        status.setWrap(true);
        for (KeyBindings.Action action : KeyBindings.Action.values()) {
            Table cell = new Table();
            cell.add(theme.caption(action.label())).padBottom(5).row();
            TextButton button = bind(theme.button(Input.Keys.toString(keys.key(action)), false), "key-" + action.name().toLowerCase(java.util.Locale.ROOT), () -> {
                capturing = action;
                status.setText("按下新的方向按键；Esc 取消。P 保留用于暂停。");
            });
            keyButtons.put(action, button);
            cell.add(button).width(86).height(36);
            controls.add(cell).spaceRight(12);
        }
        row(operation, "方向按键", "点击按键后重新绑定，四个方向不能重复。", controls);
        form.add(operation).growX().row();
        ScrollPane scroll = new ScrollPane(form, theme.scrollStyle());
        scroll.setName("settings-form");
        scroll.setScrollingDisabled(true, false);
        scroll.setFadeScrollBars(false);
        stage.setScrollFocus(scroll);
        content.add(scroll).grow().row();
        content.add(status).growX().height(32).padTop(6).row();
        Table actions = new Table();
        actions.add(bind(theme.button("恢复默认", false), "reset-settings", this::resetDraft)).height(42);
        actions.add().expandX();
        actions.add(bind(theme.button("取消修改", false), "cancel-settings", app::back)).height(42).padRight(12);
        actions.add(bind(theme.button("保存设置", true), "save-settings", this::save)).width(156).height(42);
        content.add(actions).growX().padTop(4).row();
    }

    /** 分组只管理布局；保存仍统一提交同一份设置草稿。 */
    private Table section(String title) {
        Table group = new Table();
        group.setBackground(theme.panel(ThemeManager.PANEL));
        group.pad(10, 20, 10, 20);
        group.add(theme.label(title, theme.assets.small, ThemeManager.BLUE)).left().colspan(2).padBottom(4).row();
        return group;
    }

    private void row(Table form, String title, String description, Actor control) {
        Table copy = new Table();
        copy.add(theme.text(title)).left().row();
        Label detail = theme.caption(description);
        detail.setWrap(true);
        copy.add(detail).growX().left().padTop(5);
        form.add(copy).growX().left().height(60).padRight(16);
        form.add(control).width(420).height(44).left().row();
    }

    private void resetDraft() {
        ClientSettings defaults = ClientSettings.defaults();
        volume.setValue(defaults.volume());
        resolution.setSelected(defaults.resolution());
        vsync.setChecked(defaults.vsync());
        keys = defaults.keys();
        refreshKeys();
        capturing = null;
        status.setText("已恢复默认设置，点击保存后生效。");
    }

    private void refreshKeys() {
        keyButtons.forEach((action, button) -> button.setText(Input.Keys.toString(keys.key(action))));
    }

    private void refreshVsync() {
        vsync.setText(vsync.isChecked() ? "垂直同步 · 开启" : "垂直同步 · 关闭");
    }

    private void save() {
        capturing = null;
        ClientSettings candidate = new ClientSettings(Math.round(volume.getValue()), resolution.getSelected(), vsync.isChecked(), keys);
        String error = app.applySettings(candidate);
        status.setText(error.isEmpty() ? "设置已保存。" : error);
        status.getStyle().fontColor = theme.color(error.isEmpty() ? ThemeManager.MINT : ThemeManager.GOLD);
    }

    @Override protected boolean handleKey(int keycode) {
        if (capturing == null) { return false; }
        if (keycode == Input.Keys.ESCAPE) {
            capturing = null;
            status.setText("已取消按键修改。");
        } else {
            try {
                keys = keys.with(capturing, keycode);
                capturing = null;
                refreshKeys();
                status.setText("按键已修改，点击保存后生效。");
            } catch (IllegalArgumentException invalid) {
                status.setText(invalid.getMessage());
            }
        }
        return true;
    }
}
