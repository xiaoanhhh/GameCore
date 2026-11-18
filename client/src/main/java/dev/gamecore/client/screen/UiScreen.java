package dev.gamecore.client.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.navigation.LobbyNavigation;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.client.ui.BackdropRenderer;
import dev.gamecore.client.ui.LobbyGlyph;
import dev.gamecore.client.ui.LobbyPalette;
import dev.gamecore.client.ui.TopBarDrawable;
import dev.gamecore.client.ui.UiFontRefresh;
import dev.gamecore.client.ui.UiFontScale;
import dev.gamecore.client.ui.ScrollReveal;
import java.util.ArrayList;
import java.util.List;

/** 共享页面外壳和输入所有权；使用固定设计视口缩放，保证小窗口内不会截断控件。 */
public abstract class UiScreen extends ScreenAdapter {
    protected final GameCoreClient app;
    protected final ThemeManager theme;
    protected final Stage stage;
    protected final Table content = new Table();
    private final List<Actor> focusable = new ArrayList<>();
    private final InputMultiplexer input;
    private Input windowInput;
    private boolean disposed;
    private boolean rendered;
    private final BackdropRenderer backdrop;

    protected UiScreen(GameCoreClient app, String breadcrumb) {
        this(app, breadcrumb, "Tab 切换焦点   ·   Enter 确认   ·   Esc 返回");
    }

    protected UiScreen(GameCoreClient app, String breadcrumb, String keyboardHint) {
        this(app, breadcrumb, keyboardHint, false);
    }

    protected UiScreen(GameCoreClient app, String breadcrumb, String keyboardHint, boolean arena) {
        this(app,breadcrumb,keyboardHint,arena,!arena);
    }
    /** 大厅皮肤与布局是否独立是两个维度；内嵌 Snake/Tank 仍保留原壳，但不继承大厅控件色。 */
    protected UiScreen(GameCoreClient app,String breadcrumb,String keyboardHint,boolean arena,boolean platform){
        this.app = app;
        theme = platform&&!app.isGameWindow()?app.theme().lobby():app.theme();
        boolean standalone = arena || app.isGameWindow();
        // 外壳（顶栏、页脚、背景）始终是大厅主题，内嵌 Snake 等竞技内容仍用 theme 的竞技配色。
        ThemeManager chrome = app.theme().lobby();
        backdrop = new BackdropRenderer(standalone ? theme : chrome);
        stage = new Stage(standalone ? new ExtendViewport(UiFontScale.DESIGN_WIDTH, UiFontScale.DESIGN_HEIGHT)
                : new FitViewport(UiFontScale.DESIGN_WIDTH, UiFontScale.DESIGN_HEIGHT));
        if (standalone) {
            content.setFillParent(true);
            stage.addActor(content);
        } else {
            // 大厅外壳：顶栏导航 → 内容区 → 页脚快捷键。左侧栏取消，横向空间全部留给内容卡片。
            Table shell = new Table();
            shell.setFillParent(true);
            stage.addActor(shell);
            shell.add(topBar(chrome, breadcrumb.equals("在线平台"))).growX().height(64).row();
            Table main = new Table();
            main.pad(22, 40, 12, 40);
            main.add(content).grow().top().row();
            Table footer = new Table();
            footer.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(chrome.solid(ThemeManager.LINE))).growX().height(1).colspan(2).padBottom(8).row();
            footer.add(chrome.caption(keyboardHint)).left().expandX();
            footer.add(chrome.caption("GameCore 0.1   /   " + breadcrumb)).right();
            main.add(footer).growX().padTop(12);
            shell.add(main).grow();
        }
        // 交互记录面板排在最前：只吃落在面板上的点击和 F9，其余事件照常进入页面。
        input = new InputMultiplexer(app.trafficOverlay().input(), new InputAdapter() {
            @Override public boolean keyUp(int keycode) { return handleKeyUp(keycode); }
            @Override public boolean keyDown(int keycode) {
                if (handleKey(keycode)) { return true; }
                if (keycode == Input.Keys.TAB) { cycleFocus(); return true; }
                if (keycode == Input.Keys.ESCAPE) {
                    for (Actor control : focusable) {
                        if (control instanceof SelectBox<?> box && box.getScrollPane().getStage() != null) {
                            box.hideScrollPane();
                            return true;
                        }
                    }
                }
                if (keycode == Input.Keys.ESCAPE) { onBack(); return true; }
                Actor actor = stage.getKeyboardFocus();
                if ((keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) && actor instanceof Button button) {
                    // 禁用态也必须覆盖键盘确认，避免满房/请求中的操作被 Enter 绕过。
                    if (button.isDisabled()) { return true; }
                    button.setChecked(!button.isChecked());
                    return true;
                }
                int direction = keycode == Input.Keys.RIGHT ? 1 : keycode == Input.Keys.LEFT ? -1 : 0;
                if (direction != 0 && actor instanceof Slider slider) {
                    slider.setValue(slider.getValue() + direction * 5);
                    return true;
                }
                if (direction != 0 && actor instanceof SelectBox<?> box) {
                    box.setSelectedIndex(Math.floorMod(box.getSelectedIndex() + direction, box.getItems().size));
                    return true;
                }
                return false;
            }
        }, stage);
    }

    /**
     * 大厅顶栏：品牌标与副标题 → 三个导航项（选中项底沿信号绿指示线）→ 状态与退出。
     * 导航项占满顶栏高度，指示线恰好压在顶栏底边上。
     */
    private Table topBar(ThemeManager theme, boolean online) {
        Table bar = new Table();
        bar.setName("lobby-topbar");
        bar.setBackground(new TopBarDrawable(theme));
        bar.pad(0, 32, 0, 24);
        Table brand = new Table();
        brand.setName("lobby-brand");
        brand.add(new LobbyGlyph(theme, LobbyGlyph.Kind.LOGO)).size(26).padRight(12);
        Table words = new Table();
        words.add(theme.label("GameCore", theme.assets.heading, ThemeManager.TEXT)).left().row();
        Label signature = theme.caption("本地对战  ·  在线联机");
        signature.setFontScale(.86f);
        signature.setName("lobby-signature");
        words.add(signature).left().padTop(-2);
        brand.add(words).left();
        bar.add(brand).left().padRight(40);
        // 详情、预览与内嵌对局都属于“游戏库”分区，顶栏指示保持在游戏库上，让玩家知道自己在哪个分区。
        boolean settings = !online && app.navigation().current().page() == LobbyNavigation.Page.SETTINGS;
        boolean home = !online && !settings;
        bar.add(navItem(theme, "游戏库", "nav-home", home, LobbyGlyph.Kind.LIBRARY, app::home)).growY().spaceRight(4);
        bar.add(navItem(theme, "在线平台", "nav-platform", online, LobbyGlyph.Kind.ONLINE, app::openPlatform)).growY().spaceRight(4);
        bar.add(navItem(theme, "偏好设置", "nav-settings", settings, LobbyGlyph.Kind.SETTINGS, app::openSettings)).growY();
        bar.add().expandX();
        Table status = new Table();
        status.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(theme.dot(LobbyPalette.SIGNAL))).size(7).padRight(8);
        status.add(theme.caption("离线也能玩"));
        bar.add(status).padRight(20);
        TextButton exit = bind(theme.button("退出", false), "exit", app::exit);
        LobbyGlyph.decorate(theme, exit, LobbyGlyph.Kind.EXIT, LobbyPalette.MUTED);
        exit.pad(6, 14, 6, 14);
        bar.add(exit).height(36);
        return bar;
    }

    private TextButton navItem(ThemeManager theme, String text, String name, boolean active, LobbyGlyph.Kind icon, Runnable action) {
        TextButton item = bind(theme.nav(text, active), name, action);
        LobbyGlyph.decorate(theme, item, icon, active ? LobbyPalette.SIGNAL : LobbyPalette.MUTED);
        return item;
    }

    protected final <T extends Actor> T focus(T actor, String name) {
        actor.setName(name);
        focusable.add(actor);
        actor.addListener(new FocusListener() {
            @Override public void keyboardFocusChanged(FocusEvent event, Actor target, boolean focused) {
                if (!focused || !rendered) { return; }
                // 滚动表单中的键盘焦点必须进入可视区，不能只更新不可见的焦点状态。
                for (Actor ancestor = actor.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
                    if (ancestor instanceof ScrollPane scroll) {
                        if (scroll.getWidth() <= 0 || scroll.getHeight() <= 0) { break; }
                        scroll.validate();
                        Vector2 point = actor.localToAscendantCoordinates(scroll.getActor(), new Vector2());
                        scroll.setScrollY(ScrollReveal.offset(scroll.getScrollY(), scroll.getMaxY(),
                                scroll.getScrollHeight(), point.y, actor.getHeight()));
                        scroll.updateVisualScroll();
                        stage.setScrollFocus(scroll);
                        break;
                    }
                }
            }
        });
        return actor;
    }

    protected final TextButton bind(TextButton button, String name, Runnable action) {
        focus(button, name);
        button.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { action.run(); }
        });
        return button;
    }

    /** 搜索重建卡片前解除焦点登记，避免旧 Actor 随多次输入积存在列表中。 */
    protected final void forgetFocus(Actor parent) {
        focusable.removeIf(actor -> actor.isDescendantOf(parent));
    }

    protected void onBack() { app.back(); }
    /** Tank 等战场页面可改用程序“战术”背景；默认沿用主题背景。 */
    protected boolean tacticalBackdrop() { return false; }
    protected boolean handleKey(int keycode) { return false; }
    /** 连续动作页面可记录按键释放；普通大厅页面继续交给 Stage 处理。 */
    protected boolean handleKeyUp(int keycode) { return false; }

    private void cycleFocus() {
        List<Actor> available = focusable.stream().filter(actor -> actor.getStage() == stage
                && visibleInHierarchy(actor) && (!(actor instanceof Button button) || !button.isDisabled())).toList();
        if (available.isEmpty()) { return; }
        int delta = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT) ? -1 : 1;
        int current = available.indexOf(stage.getKeyboardFocus());
        stage.setKeyboardFocus(available.get(Math.floorMod(current + delta, available.size())));
    }
    private static boolean visibleInHierarchy(Actor actor){for(Actor p=actor;p!=null;p=p.getParent()){if(!p.isVisible()){return false;}}return true;}

    public Stage stage() { return stage; }
    public boolean lobbyUi(){return theme.isLobby();}
    public boolean isDisposed() { return disposed; }

    @Override public void show() {
        windowInput = Gdx.input; windowInput.setInputProcessor(input);
        content.getColor().a = 0;
        content.addAction(Actions.fadeIn(.16f));
    }
    @Override public void hide() {
        stage.cancelTouchFocus();
        if (windowInput != null && windowInput.getInputProcessor() == input) { windowInput.setInputProcessor(null); }
    }
    @Override public void render(float delta) {
        try (var change = app.assets().refreshFonts(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight(), delta)) {
            if (change != null) { UiFontRefresh.apply(stage.getRoot(), change); }
        }
        ScreenUtils.clear(backdrop.lobby()?LobbyPalette.INK:ThemeManager.BACKGROUND);
        backdrop.draw(stage.getBatch(), tacticalBackdrop());
        stage.getViewport().apply();
        stage.act(Math.min(delta, .1f));
        stage.draw();
        // 面板有自己的视口，画完恢复页面视口，子类在 super.render 之后的绘制不受影响。
        if (app.trafficOverlay().render()) { stage.getViewport().apply(); }
        rendered = true;
    }
    @Override public void resize(int width, int height) { stage.getViewport().update(width, height, true); }
    @Override public void dispose() {
        if (!disposed) { hide(); stage.dispose(); disposed = true; }
    }
}
