package dev.gamecore.client.tank;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;

/** 地图与可滚动 HUD 的响应式容器；只管理 Actor 布局，不推进规则或持有纹理。 */
public final class TankArenaPane extends WidgetGroup {
    private final TankBoard board;
    private final Actor controls;
    public TankArenaPane(TankBoard board, Actor controls) { this.board = board; this.controls = controls; addActor(board); addActor(controls); }
    @Override public float getMinWidth() { return 0; }
    @Override public float getMinHeight() { return 0; }
    @Override public void layout() {
        var plan = TankArenaLayout.calculate(getWidth(), getHeight(), board.columns(), board.rows());
        var area = plan.board(); var panel = plan.controls();
        board.setBounds(area.x(), area.y(), area.width(), area.height());
        controls.setBounds(panel.x(), panel.y(), panel.width(), panel.height());
    }
}
