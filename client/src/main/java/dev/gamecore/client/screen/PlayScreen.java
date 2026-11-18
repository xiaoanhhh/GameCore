package dev.gamecore.client.screen;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import dev.gamecore.api.GameDescriptor;
import dev.gamecore.api.GameMode;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.ui.ArenaArt;
import dev.gamecore.client.ui.ThemeManager;

/** 可操作的场景预览：验证模式传递、方向设置、暂停和返回，不实现 Snake/Tank 规则。 */
public final class PlayScreen extends UiScreen {
    private final ArenaArt art;
    private final Label state;
    private final TextButton pauseButton;
    private boolean paused;

    public PlayScreen(GameCoreClient app, GameDescriptor game, String modeId) {
        super(app, "场景预览");
        GameMode mode = game.requireMode(modeId);
        Table heading = new Table();
        content.pad(24);
        heading.setBackground(theme.panel(ThemeManager.PANEL));
        heading.pad(14, 18, 14, 18);
        Table title = new Table();
        title.add(theme.label(game.name(), theme.assets.title, ThemeManager.TEXT)).left().row();
        title.add(theme.label(mode.name() + "  /  场景预览", theme.assets.body, theme.accent(game.id()))).left().padTop(8);
        heading.add(title).expandX().left();
        pauseButton = bind(theme.button("暂停预览", false), "pause-preview", this::togglePause);
        heading.add(theme.badge("场景预览", ThemeManager.GOLD)).padRight(18);
        heading.add(pauseButton).height(42).padRight(12);
        heading.add(bind(theme.button("返回大厅", false), "return-home", app::home)).height(42);
        content.add(heading).growX().padBottom(20).row();
        art = new ArenaArt(theme, game.id(), modeId);
        art.setName("preview-art");
        art.moveMarker(0, 0);
        content.add(art).grow().minHeight(300).row();
        Table status = new Table();
        status.setBackground(theme.panel(ThemeManager.PANEL));
        status.pad(12, 16, 12, 16);
        state = theme.label("●  预览中", theme.assets.body, ThemeManager.MINT);
        state.setName("preview-state");
        status.add(state).left().expandX();
        status.add(theme.caption("使用已配置的方向键移动白色光标   ·   P / Esc 暂停")).right();
        content.add(status).growX().padTop(16).row();
        Label note = theme.caption("这是场景预览。返回游戏库，选择已支持的玩法即可开始对战。");
        note.setWrap(true);
        content.add(note).growX().padTop(16).row();
    }

    private void togglePause() {
        paused = !paused;
        state.setText(paused ? "●  已暂停" : "●  预览中");
        pauseButton.setText(paused ? "继续预览" : "暂停预览");
    }

    public boolean isPaused() { return paused; }
    public float markerX() { return art.markerX(); }
    public float markerY() { return art.markerY(); }

    @Override protected boolean handleKey(int keycode) {
        if (keycode == Input.Keys.P || keycode == Input.Keys.ESCAPE) { togglePause(); return true; }
        if (paused) { return false; }
        var keys = app.settings().keys();
        if (keycode == keys.left()) { art.moveMarker(-.04f, 0); return true; }
        if (keycode == keys.right()) { art.moveMarker(.04f, 0); return true; }
        if (keycode == keys.up()) { art.moveMarker(0, .06f); return true; }
        if (keycode == keys.down()) { art.moveMarker(0, -.06f); return true; }
        return false;
    }

    @Override public void pause() { if (!paused) { togglePause(); } }
}
