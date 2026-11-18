package dev.gamecore.client.snake;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.snake.Cell;
import dev.gamecore.snake.Direction;
import dev.gamecore.snake.SnakeGame;
import dev.gamecore.snake.SnakeSnapshot;

/** 原创网格渲染器只读快照；按可用区域居中画正方形，不触发 Tick，也不创建独占 GPU 资源。 */
public final class SnakeBoard extends Widget {
    private static final Color BOARD = Color.valueOf("0b202b");
    private static final Color GRID = Color.valueOf("173542");
    private static final Color WALL = Color.valueOf("7c91aa");
    private static final Color DEAD = Color.valueOf("d67686");
    private final ThemeManager theme;
    private SnakeSnapshot snapshot;

    public SnakeBoard(ThemeManager theme, SnakeSnapshot snapshot) { this.theme = theme; this.snapshot = snapshot; }
    public void update(SnakeSnapshot value) { snapshot = value; }
    @Override public float getMinWidth() { return 0; }
    @Override public float getMinHeight() { return 0; }
    @Override public float getPrefWidth() { return 440; }
    @Override public float getPrefHeight() { return 440; }

    @Override public void draw(Batch batch, float parentAlpha) {
        float oldColor = batch.getPackedColor();
        float size = Math.min(getWidth(), getHeight());
        float x = getX() + (getWidth() - size) / 2;
        float y = getY() + (getHeight() - size) / 2;
        float cell = size / SnakeGame.SIZE;
        rect(batch, BOARD, x, y, size, size);
        for (int i = 0; i <= SnakeGame.SIZE; i++) {
            rect(batch, GRID, x + i * cell, y, 1, size);
            rect(batch, GRID, x, y + i * cell, size, 1);
        }
        rect(batch, ThemeManager.LINE, x, y, size, 2);
        rect(batch, ThemeManager.LINE, x, y + size - 2, size, 2);
        rect(batch, ThemeManager.LINE, x, y, 2, size);
        rect(batch, ThemeManager.LINE, x + size - 2, y, 2, size);
        for (Cell obstacle : snapshot.obstacles()) {
            tile(batch, WALL, obstacle, x, y, cell, 2);
            rect(batch, GRID, x + obstacle.x() * cell + 5, y + obstacle.y() * cell + cell / 2, cell - 10, 2);
        }
        if (snapshot.food() != null) {
            Cell food = snapshot.food();
            float fx = x + food.x() * cell, fy = y + food.y() * cell;
            batch.setColor(ThemeManager.GOLD);
            batch.draw(theme.assets.circle, fx + cell * .22f, fy + cell * .18f, cell * .6f, cell * .6f);
            rect(batch, ThemeManager.MINT, fx + cell * .5f, fy + cell * .72f, cell * .12f, cell * .16f);
        }
        for (int p = 0; p < snapshot.players().size(); p++) {
            SnakeSnapshot.Player player = snapshot.players().get(p);
            Color color = !player.alive() ? DEAD : p == 0 ? ThemeManager.MINT : ThemeManager.PLAYER_TWO;
            for (Cell part : player.body()) { tile(batch, color, part, x, y, cell, 1.5f); }
            Cell head = player.head();
            if (!inside(head)) { continue; }
            Direction direction = player.direction();
            float cx = x + (head.x() + .5f) * cell, cy = y + (head.y() + .5f) * cell;
            for (int side : new int[]{-1, 1}) {
                float ex = cx + direction.dx() * cell * .22f + direction.dy() * side * cell * .2f;
                float ey = cy + direction.dy() * cell * .22f + direction.dx() * side * cell * .2f;
                rect(batch, BOARD, ex - 1.5f, ey - 1.5f, 3, 3);
            }
        }
        batch.setPackedColor(oldColor);
    }
    private static boolean inside(Cell cell) {
        return cell.x() >= 0 && cell.x() < SnakeGame.SIZE && cell.y() >= 0 && cell.y() < SnakeGame.SIZE;
    }
    private void tile(Batch batch, Color color, Cell point, float x, float y, float size, float pad) {
        if (inside(point)) {
            float tx = x + point.x() * size + pad, ty = y + point.y() * size + pad;
            batch.setColor(color);
            theme.assets.tileRounded.draw(batch, tx, ty, size - pad * 2, size - pad * 2);
            batch.setColor(1, 1, 1, .16f);
            batch.draw(theme.assets.pixel, tx + size * .14f, ty + size * .72f, size * .64f, Math.max(1, size * .035f));
        }
    }
    private void rect(Batch batch, Color color, float x, float y, float w, float h) {
        batch.setColor(color); batch.draw(theme.assets.pixel, x, y, w, h);
    }
}
