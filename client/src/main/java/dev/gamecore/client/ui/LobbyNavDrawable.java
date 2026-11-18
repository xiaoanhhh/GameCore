package dev.gamecore.client.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;

/**
 * 顶栏导航与页内标签的背景：常态透明只靠文字明度区分；悬停给极淡底色；
 * 选中在底沿画一条信号绿指示线；键盘焦点额外描一圈切角细边，保证不依赖颜色也能看清焦点位置。
 * 仅借用窗口共享的切角与白像素纹理，不拥有释放权。
 */
public final class LobbyNavDrawable extends BaseDrawable {
    public enum State { IDLE, HOVER, ACTIVE, FOCUSED, ACTIVE_FOCUSED }
    private final UiInk ink;
    private final NinePatchDrawable shape;
    private final State state;

    public LobbyNavDrawable(ThemeManager theme, State state) {
        ink = new UiInk(theme);
        shape = new NinePatchDrawable(theme.assets.cut);
        this.state = state;
        setLeftWidth(14); setRightWidth(14); setTopHeight(8); setBottomHeight(8);
    }

    @Override public void draw(Batch b, float x, float y, float w, float h) {
        float packed = b.getPackedColor(), a = b.getColor().a;
        boolean active = state == State.ACTIVE || state == State.ACTIVE_FOCUSED;
        boolean focused = state == State.FOCUSED || state == State.ACTIVE_FOCUSED;
        float inset = Math.min(10, h * .16f);
        if (focused) {
            b.setColor(LobbyPalette.SIGNAL.r, LobbyPalette.SIGNAL.g, LobbyPalette.SIGNAL.b, a * .85f);
            shape.draw(b, x, y + inset, w, h - 2 * inset);
            b.setColor(LobbyPalette.INK.r, LobbyPalette.INK.g, LobbyPalette.INK.b, a);
            shape.draw(b, x + 1, y + inset + 1, w - 2, h - 2 * inset - 2);
        }
        float wash = state == State.HOVER || focused ? .06f : active ? .035f : 0;
        if (wash > 0) {
            b.setColor(LobbyPalette.TEXT.r, LobbyPalette.TEXT.g, LobbyPalette.TEXT.b, a * wash);
            shape.draw(b, x, y + inset, w, h - 2 * inset);
        }
        if (active) { ink.rect(b, LobbyPalette.SIGNAL, a, x + 12, y, Math.max(0, w - 24), 2); }
        b.setPackedColor(packed);
    }
}
