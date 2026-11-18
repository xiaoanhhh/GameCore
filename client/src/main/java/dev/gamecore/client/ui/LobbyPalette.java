package dev.gamecore.client.ui;

import com.badlogic.gdx.graphics.Color;

/**
 * 大厅“夜墨信号”配色：墨黑底 + 中性灰阶表面，唯一的界面强调色为信号绿，三款游戏各持一种识别色。
 * 只供大厅类页面（游戏库、详情、设置、在线平台）只读使用；竞技页面继续使用 ThemeManager 的原常量，不修改 Color 单例。
 */
public final class LobbyPalette {
    public static final Color INK=Color.valueOf("0a0c10"),
            /** 常规卡片：约 95% 不透明，背景光晕只隐约透出。 */
            PANEL=Color.valueOf("12161df2"),
            /** 悬停/选中卡片、输入框等抬升一级的表面。 */
            RAISED=Color.valueOf("1a1f29f5"),
            /** 顶栏：比内容更深，靠底部发丝线与内容分层。 */
            TOPBAR=Color.valueOf("0c0f14eb"),
            /** 1px 发丝边：只做轮廓；STRONG 用于次要按钮等需要被看见的边。 */
            LINE=Color.valueOf("252b36"),LINE_STRONG=Color.valueOf("3a4250"),
            TEXT=Color.valueOf("f3f5f8"),MUTED=Color.valueOf("98a2b3"),DISABLED=Color.valueOf("7d8696"),
            /** 信号绿：主操作、选中指示、键盘焦点；文字压在其上时使用 INK。 */
            SIGNAL=Color.valueOf("d4ff3f"),SIGNAL_HI=Color.valueOf("e4ff86"),SIGNAL_LO=Color.valueOf("aed12a"),
            /** 游戏识别色：Snake 青绿、Tank 琥珀、终极格斗珊瑚红。 */
            TEAL=Color.valueOf("3ee6b0"),AMBER=Color.valueOf("ffb547"),CORAL=Color.valueOf("ff6b7f");
    private LobbyPalette() { }
}
