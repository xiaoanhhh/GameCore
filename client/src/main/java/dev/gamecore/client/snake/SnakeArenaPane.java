package dev.gamecore.client.snake;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;

/** 独立游戏窗口的布局容器，窗口 resize 时重算真实可用空间；不推进规则或持有资源。 */
public final class SnakeArenaPane extends WidgetGroup {
    private final Actor board;
    private final Actor controls;
    public SnakeArenaPane(Actor board, Actor controls) {
        this.board = board; this.controls = controls;
        addActor(board); addActor(controls);
    }
    @Override public float getMinWidth() { return 0; }
    @Override public float getMinHeight() { return 0; }
    @Override public void layout() {
        var plan = SnakeArenaLayout.calculate(getWidth(), getHeight());
        var grid = plan.board(); var panel = plan.controls();
        board.setBounds(grid.x(), grid.y(), grid.width(), grid.height());
        controls.setBounds(panel.x(), panel.y(), panel.width(), panel.height());
    }
}
