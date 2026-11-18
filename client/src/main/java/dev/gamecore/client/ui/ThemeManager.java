package dev.gamecore.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import dev.gamecore.client.assets.ClientAssetManager;

/**
 * 统一的深色 UI 配色与组件样式。竞技主题保留原常量；大厅主题由 {@link #lobby()} 派生，
 * 把同一组语义色映射到 {@link LobbyPalette} 并改用切角表面。Drawable 借用共享纹理，不持有释放权。
 */
public final class ThemeManager {
    public static final Color BACKGROUND = Color.valueOf("09111f");
    public static final Color SIDEBAR = Color.valueOf("0c1525f5");
    public static final Color PANEL = Color.valueOf("111e30f5");
    public static final Color LINE = Color.valueOf("31465e");
    public static final Color TEXT = Color.valueOf("f3f7ff");
    public static final Color MUTED = Color.valueOf("afbed2");
    public static final Color BLUE = Color.valueOf("6ddcff");
    public static final Color PLAYER_TWO = Color.valueOf("8ba9ff");
    public static final Color MINT = Color.valueOf("69dfbc");
    public static final Color GOLD = Color.valueOf("ffbd70");
    /** 大厅次要按钮/输入框底色：比面板更深，带少量透明让背景光晕透出。 */
    private static final Color WELL = Color.valueOf("0e1117e6");
    private static final Color HOVER_EDGE = Color.valueOf("5a6476");
    public final ClientAssetManager assets;
    private final boolean lobby;

    public ThemeManager(ClientAssetManager assets) { this(assets, false); }
    private ThemeManager(ClientAssetManager assets, boolean lobby) { this.assets = assets; this.lobby = lobby; }
    /** 大厅类页面使用的“夜墨信号”主题；与竞技主题共享字体和纹理，只替换控件配色与形状。 */
    public ThemeManager lobby() { return lobby ? this : new ThemeManager(assets, true); }
    public boolean isLobby() { return lobby; }
    private Color tone(Color c) {
        if (!lobby) { return c; }
        if (c.equals(TEXT)) { return LobbyPalette.TEXT; } if (c.equals(MUTED)) { return LobbyPalette.MUTED; }
        if (c.equals(BLUE)) { return LobbyPalette.SIGNAL; } if (c.equals(GOLD)) { return LobbyPalette.AMBER; }
        if (c.equals(MINT)) { return LobbyPalette.TEAL; } if (c.equals(PANEL)) { return LobbyPalette.PANEL; }
        if (c.equals(LINE)) { return LobbyPalette.LINE; } if (c.equals(SIDEBAR)) { return LobbyPalette.TOPBAR; }
        return c;
    }
    /** 页面需要直接改 Label 颜色时（如状态提示）先经主题映射，避免大厅里出现竞技主题色。 */
    public Color color(Color semantic) { return tone(semantic); }

    public Drawable panel(Color color) { return surface(tone(color), tone(LINE)); }
    public Drawable surface(Color fill, Color border) {
        if (lobby) { return new LobbySurface(this, tone(fill), tone(border), 1, null); }
        return new SurfaceDrawable(new NinePatchDrawable(assets.rounded).tint(border),
                new NinePatchDrawable(assets.rounded).tint(fill));
    }
    /** 大厅切角表面：可指定描边粗细与左侧强调脊线（横幅/选中项）；竞技主题回退为普通面板。 */
    public Drawable surface(Color fill, Color border, float stroke, Color spine) {
        return lobby ? new LobbySurface(this, fill, border, stroke, spine) : surface(fill, border);
    }
    public Drawable solid(Color color) { return new TextureRegionDrawable(new TextureRegion(assets.pixel)).tint(tone(color)); }
    public Drawable dot(Color color) { return new TextureRegionDrawable(new TextureRegion(assets.circle)).tint(color); }
    public Color accent(String gameId) {
        if (lobby) { return "snake".equals(gameId) ? LobbyPalette.TEAL : "tank".equals(gameId) ? LobbyPalette.AMBER : LobbyPalette.CORAL; }
        return "snake".equals(gameId) ? MINT : "tank".equals(gameId) ? GOLD : BLUE;
    }

    public Label label(String text, BitmapFont font, Color color) {
        return new UiLabel(text, new Label.LabelStyle(font, tone(color)));
    }
    public Label text(String text) { return label(text, assets.body, TEXT); }
    public Label caption(String text) { return label(text, assets.small, MUTED); }

    public TextButton button(String text, boolean primary) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = assets.body;
        style.fontColor = primary ? BACKGROUND : TEXT;
        style.up = surface(primary ? BLUE : Color.valueOf("17283ef5"), primary ? BLUE : LINE);
        style.over = surface(primary ? Color.valueOf("a1eaff") : Color.valueOf("233c55"), BLUE);
        style.down = surface(primary ? Color.valueOf("47b8dc") : Color.valueOf("102438"), BLUE);
        style.focused = surface(primary ? Color.valueOf("bcefff") : Color.valueOf("20384e"), TEXT);
        style.disabled = surface(Color.valueOf("1d293a"), LINE);
        style.disabledFontColor = MUTED;
        TextButton button = new MotionTextButton(text, style);
        if (lobby) {
            // 主操作是页面上唯一的实心信号绿块；次要操作为描边按钮。键盘焦点统一 2px 描边，不只靠底色变化。
            style.fontColor = primary ? LobbyPalette.INK : LobbyPalette.TEXT;
            style.overFontColor = primary ? LobbyPalette.INK : LobbyPalette.TEXT;
            style.downFontColor = primary ? LobbyPalette.INK : LobbyPalette.SIGNAL;
            style.up = primary ? surface(LobbyPalette.SIGNAL, LobbyPalette.SIGNAL, 1, null) : surface(WELL, LobbyPalette.LINE_STRONG, 1, null);
            style.over = primary ? surface(LobbyPalette.SIGNAL_HI, LobbyPalette.SIGNAL_HI, 1, null) : surface(LobbyPalette.RAISED, HOVER_EDGE, 1, null);
            style.down = primary ? surface(LobbyPalette.SIGNAL_LO, LobbyPalette.SIGNAL_LO, 1, null) : surface(LobbyPalette.INK, LobbyPalette.SIGNAL, 1, null);
            style.focused = primary ? surface(LobbyPalette.SIGNAL_HI, LobbyPalette.TEXT, 2, null) : surface(LobbyPalette.RAISED, LobbyPalette.SIGNAL, 2, null);
            style.disabled = surface(Color.valueOf("12161d99"), Color.valueOf("1f242d"), 1, null);
            style.disabledFontColor = LobbyPalette.DISABLED;
        }
        button.pad(10, 18, 10, 18);
        return button;
    }

    /** 游戏卡片样式：常态中性发丝边，悬停/焦点改用该游戏识别色描边，按下压暗；竞技主题回退普通按钮。 */
    public TextButton.TextButtonStyle cardStyle(Color accent) {
        TextButton.TextButtonStyle style = button("", false).getStyle();
        if (!lobby) { return style; }
        style.up = surface(LobbyPalette.PANEL, LobbyPalette.LINE, 1, null);
        style.over = surface(LobbyPalette.RAISED, new Color(accent.r, accent.g, accent.b, .75f), 1, null);
        style.down = surface(LobbyPalette.INK, accent, 1, null);
        style.focused = surface(LobbyPalette.RAISED, accent, 2, null);
        return style;
    }

    public TextButton toggle(String text) {
        TextButton button = button(text, false);
        TextButton.TextButtonStyle style = button.getStyle();
        style.checked = surface(Color.valueOf("193c50"), BLUE);
        style.checkedOver = surface(Color.valueOf("234e65"), BLUE);
        style.checkedFocused = surface(Color.valueOf("234e65"), TEXT);
        style.checkedFontColor = BLUE;
        if (lobby) {
            // 选中项：信号绿描边 + 左侧脊线；未选中项只留发丝边，避免一列按钮都像可点击的大色块。
            style.up = surface(WELL, LobbyPalette.LINE, 1, null);
            style.checked = surface(Color.valueOf("171d12f5"), LobbyPalette.SIGNAL, 1, LobbyPalette.SIGNAL);
            style.checkedOver = surface(Color.valueOf("1d2416f5"), LobbyPalette.SIGNAL, 1, LobbyPalette.SIGNAL);
            style.checkedFocused = surface(Color.valueOf("1d2416f5"), LobbyPalette.SIGNAL, 2, LobbyPalette.SIGNAL);
            style.checkedFontColor = LobbyPalette.SIGNAL;
        }
        return button;
    }

    /** 导航项：顶栏与页内标签共用。常态只有文字，选中在底沿显示信号绿指示线；竞技主题回退为普通按钮。 */
    public TextButton nav(String text, boolean active) {
        if (!lobby) { return button(text, active); }
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = assets.body;
        style.fontColor = active ? LobbyPalette.TEXT : LobbyPalette.MUTED;
        style.overFontColor = LobbyPalette.TEXT;
        style.disabledFontColor = LobbyPalette.DISABLED;
        style.up = new LobbyNavDrawable(this, active ? LobbyNavDrawable.State.ACTIVE : LobbyNavDrawable.State.IDLE);
        style.over = new LobbyNavDrawable(this, active ? LobbyNavDrawable.State.ACTIVE : LobbyNavDrawable.State.HOVER);
        style.down = style.over;
        style.disabled = style.up;
        style.focused = new LobbyNavDrawable(this, active ? LobbyNavDrawable.State.ACTIVE_FOCUSED : LobbyNavDrawable.State.FOCUSED);
        TextButton button = new MotionTextButton(text, style);
        button.pad(8, 14, 8, 14);
        return button;
    }

    public TextField searchField() {
        TextField.TextFieldStyle style = new TextField.TextFieldStyle();
        style.font = assets.body;
        style.fontColor = TEXT;
        style.messageFontColor = MUTED;
        style.background = panel(PANEL);
        style.focusedBackground = surface(Color.valueOf("142a40"), BLUE);
        style.cursor = solid(BLUE);
        style.cursor.setMinWidth(2);
        style.selection = solid(Color.valueOf("35588a"));
        TextField field = new TextField("", style);
        field.setMessageText("搜索游戏名称");
        if (lobby) {
            style.fontColor = LobbyPalette.TEXT; style.messageFontColor = LobbyPalette.MUTED;
            style.background = surface(WELL, LobbyPalette.LINE_STRONG, 1, null);
            style.focusedBackground = surface(LobbyPalette.RAISED, LobbyPalette.SIGNAL, 2, null);
            style.cursor = solid(LobbyPalette.SIGNAL); style.cursor.setMinWidth(2);
            style.selection = solid(Color.valueOf("4a5a1c"));
        }
        return field;
    }

    public ScrollPane.ScrollPaneStyle scrollStyle() {
        ScrollPane.ScrollPaneStyle style = new ScrollPane.ScrollPaneStyle();
        style.vScroll = solid(SIDEBAR);
        style.vScroll.setMinWidth(5);
        style.vScrollKnob = solid(MUTED);
        style.vScrollKnob.setMinWidth(5);
        style.vScrollKnob.setMinHeight(30);
        if (lobby) {
            // 大厅滚动条更细、轨道几乎透明，只在内容溢出时以低对比度提示可滚动。
            style.vScroll = solid(Color.valueOf("ffffff0a")); style.vScroll.setMinWidth(4);
            style.vScrollKnob = solid(LobbyPalette.LINE_STRONG); style.vScrollKnob.setMinWidth(4); style.vScrollKnob.setMinHeight(30);
        }
        return style;
    }

    public <T> SelectBox<T> selectBox() {
        SelectBox.SelectBoxStyle style = new SelectBox.SelectBoxStyle();
        style.font = assets.body;
        style.fontColor = TEXT;
        style.background = panel(Color.valueOf("1a2d43"));
        style.backgroundOpen = surface(Color.valueOf("1a344b"), BLUE);
        style.backgroundOver = surface(Color.valueOf("1a344b"), BLUE);
        style.listStyle = new List.ListStyle(assets.body, TEXT, TEXT, panel(Color.valueOf("234b65")));
        style.scrollStyle = scrollStyle();
        style.scrollStyle.background = panel(PANEL);
        if (lobby) {
            // 下拉列表的选中行用信号绿实底 + 墨色字，与主按钮同一语义。
            style.fontColor = LobbyPalette.TEXT;
            style.background = surface(WELL, LobbyPalette.LINE_STRONG, 1, null);
            style.backgroundOpen = surface(LobbyPalette.RAISED, LobbyPalette.SIGNAL, 2, null);
            style.backgroundOver = surface(LobbyPalette.RAISED, HOVER_EDGE, 1, null);
            style.listStyle = new List.ListStyle(assets.body, LobbyPalette.INK, LobbyPalette.TEXT, solid(LobbyPalette.SIGNAL));
            style.scrollStyle.background = surface(LobbyPalette.RAISED, LobbyPalette.LINE_STRONG, 1, null);
        }
        return new SelectBox<>(style);
    }

    public Slider volumeSlider() {
        Slider.SliderStyle style = new Slider.SliderStyle();
        style.background = solid(LINE);
        style.background.setMinHeight(4);
        style.knobBefore = solid(BLUE);
        style.knobBefore.setMinHeight(4);
        style.knob = dot(TEXT);
        style.knob.setMinWidth(16);
        style.knob.setMinHeight(16);
        style.knobOver = dot(BLUE);
        style.knobOver.setMinWidth(18); style.knobOver.setMinHeight(18);
        style.knobDown = dot(GOLD);
        style.knobDown.setMinWidth(18); style.knobDown.setMinHeight(18);
        if (lobby) {
            style.background = solid(Color.valueOf("ffffff1f")); style.background.setMinHeight(4);
            style.knobBefore = solid(LobbyPalette.SIGNAL); style.knobBefore.setMinHeight(4);
            style.knob = dot(LobbyPalette.TEXT); style.knob.setMinWidth(16); style.knob.setMinHeight(16);
            style.knobOver = dot(LobbyPalette.SIGNAL); style.knobOver.setMinWidth(18); style.knobOver.setMinHeight(18);
            style.knobDown = dot(LobbyPalette.SIGNAL_HI); style.knobDown.setMinWidth(18); style.knobDown.setMinHeight(18);
        }
        return new Slider(0, 100, 1, false, style);
    }

    /** 小标签仍使用真实文字，避免只依赖颜色表达可玩、预览和按键状态。 */
    public com.badlogic.gdx.scenes.scene2d.ui.Table badge(String text, Color color) {
        var badge = new com.badlogic.gdx.scenes.scene2d.ui.Table();
        badge.setBackground(lobby ? surface(Color.valueOf("0a0c10d9"), new Color(color.r, color.g, color.b, .6f), 1, null)
                : surface(Color.valueOf("101f31"), color));
        badge.pad(0);
        badge.add(label(text, assets.small, color)).pad(5, 10, 5, 10);
        return badge;
    }
}
