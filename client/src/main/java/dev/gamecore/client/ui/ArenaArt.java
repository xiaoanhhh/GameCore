package dev.gamecore.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;

/** 原创程序封面和预览场景；所有形状借用 UI 纹理，未使用外部美术素材。 */
public final class ArenaArt extends Widget {
    private final ThemeManager theme;
    private final String gameId;
    private final dev.gamecore.client.fight.FightArt fightArt;
    private String modeId;
    private float markerX = 0.5f;
    private float markerY = 0.45f;
    private boolean markerVisible;
    private float canvasWidth;
    private float canvasHeight;
    private static final Color SNAKE_BG = Color.valueOf("0c2632");
    private static final Color TANK_BG = Color.valueOf("242331");

    public ArenaArt(ThemeManager theme, String gameId, String modeId) {
        this.theme = theme;
        this.gameId = gameId;
        fightArt = "fight".equals(gameId) ? new dev.gamecore.client.fight.FightArt(theme) : null;
        this.modeId = modeId;
    }

    @Override public float getPrefWidth() { return 420; }
    @Override public float getPrefHeight() { return 180; }
    @Override public float getMinWidth() { return 0; }
    @Override public float getMinHeight() { return 0; }

    public void moveMarker(float dx, float dy) {
        markerX = Math.max(.05f, Math.min(.95f, markerX + dx));
        markerY = Math.max(.05f, Math.min(.95f, markerY + dy));
        markerVisible = true;
    }
    public float markerX() { return markerX; }
    public float markerY() { return markerY; }
    public String modeId() { return modeId; }
    public void setMode(String value) { modeId = value; }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color previous = new Color(batch.getColor());
        boolean snake = "snake".equals(gameId);
        canvasHeight = Math.min(getHeight(), getWidth() / 2f);
        canvasWidth = canvasHeight * 2f;
        Color grid;
        if (theme.isLobby()) {
            // 大厅封面：近黑的识别色底 + 中心识别色柔光 + 淡网格，三张卡片在同一墨色体系里靠色相区分。
            Color accent = theme.accent(gameId);
            batch.setColor(accent.r * .09f, accent.g * .09f, accent.b * .09f + .02f, 1);
            batch.draw(theme.assets.pixel, getX(), getY(), getWidth(), getHeight());
            batch.setColor(accent.r, accent.g, accent.b, .22f);
            float radius = Math.max(getWidth(), getHeight()) * .62f;
            batch.draw(theme.assets.glow, getX() + getWidth() / 2 - radius, getY() + getHeight() * .45f - radius, radius * 2, radius * 2);
            grid = new Color(accent.r, accent.g, accent.b, .10f);
        } else {
            batch.setColor(snake ? SNAKE_BG : TANK_BG);
            batch.draw(theme.assets.pixel, getX(), getY(), getWidth(), getHeight());
            batch.setColor(snake ? .05f : .25f, .15f, .23f, .8f);
            batch.draw(theme.assets.shade, getX(), getY(), getWidth(), getHeight());
            grid = snake ? Color.valueOf("1c4445") : Color.valueOf("433d38");
        }
        for (int i = 1; i < 20; i++) { rect(batch, grid, i / 20f, 0, .001f, 1); }
        for (int i = 1; i < 12; i++) { rect(batch, grid, 0, i / 12f, 1, .002f); }
        if (snake) { snake(batch); } else if ("fight".equals(gameId)) { fighters(batch); } else { tanks(batch); }
        batch.setColor(theme.accent(gameId));
        batch.draw(theme.assets.pixel, getX(), getY(), getWidth(), 2);
        if (markerVisible) {
            batch.setColor(ThemeManager.TEXT);
            float size = Math.min(getWidth(), getHeight()) * .045f;
            batch.draw(theme.assets.circle, getX() + getWidth() * markerX - size / 2,
                    getY() + getHeight() * markerY - size / 2, size, size);
        }
        batch.setColor(previous);
    }

    private void snake(Batch batch) {
        float[][] cells = {{.18f,.25f},{.26f,.25f},{.34f,.25f},{.34f,.39f},{.34f,.53f},
                {.42f,.53f},{.50f,.53f},{.58f,.53f},{.58f,.67f},{.66f,.67f},{.74f,.67f}};
        for (int i = 0; i < cells.length; i++) {
            rect(batch, i == cells.length - 1 ? Color.valueOf("c2ffe2") : theme.isLobby() ? theme.accent("snake") : ThemeManager.MINT,
                    cells[i][0], cells[i][1], .072f, .12f);
        }
        Color eye = theme.isLobby() ? Color.valueOf("0b1d1b") : SNAKE_BG;
        rect(batch, eye, .788f, .748f, .012f, .022f);
        rect(batch, eye, .788f, .691f, .012f, .022f);
        rect(batch, ThemeManager.GOLD, .82f, .32f, .032f, .054f);
        if ("obstacles".equals(modeId)) {
            rect(batch, ThemeManager.MUTED, .12f, .72f, .16f, .055f);
            rect(batch, ThemeManager.MUTED, .55f, .18f, .16f, .055f);
        }
        if ("duo".equals(modeId)) {
            rect(batch, ThemeManager.PLAYER_TWO, .50f, .82f, .22f, .06f);
        }
    }

    private void tanks(Batch batch) {
        Color block = Color.valueOf("5e5145");
        rect(batch, block, .12f, .55f, .18f, .12f);
        rect(batch, block, .43f, .18f, .08f, .28f);
        rect(batch, block, .68f, .68f, .20f, .1f);
        tank(batch, ThemeManager.GOLD, .30f, .34f, false);
        tank(batch, ThemeManager.BLUE, .72f, .47f, true);
        if ("bot".equals(modeId)) {
            tank(batch, ThemeManager.GOLD, .64f, .76f, true);
        }
        for (int i = 0; i < 3; i++) {
            rect(batch, ThemeManager.GOLD, .49f + i * .052f, .386f, .015f, .014f);
        }
    }

    private void fighters(Batch batch) {
        float scale = canvasHeight / 270;
        float floor = getY() + (getHeight() - canvasHeight) / 2 + 22 * scale;
        fightArt.fighter(batch, dev.gamecore.fight.FighterKind.NAIWA, getX() + getWidth() * .29f, floor,
                scale, 1, dev.gamecore.fight.FightSnapshot.Action.PUNCH, 0);
        fightArt.fighter(batch, dev.gamecore.fight.FighterKind.FEIDUDU, getX() + getWidth() * .73f, floor,
                scale, -1, dev.gamecore.fight.FightSnapshot.Action.GUARD, 0);
    }

    private void tank(Batch batch, Color color, float x, float y, boolean left) {
        rect(batch, Color.valueOf("171f2c"), x - .015f, y - .035f, .13f, .032f);
        rect(batch, Color.valueOf("171f2c"), x - .015f, y + .104f, .13f, .032f);
        rect(batch, color, x, y, .10f, .10f);
        rect(batch, color, left ? x - .07f : x + .07f, y + .042f, .10f, .023f);
        rect(batch, Color.valueOf("f6dfaf"), x + .025f, y + .025f, .046f, .05f);
    }

    private void rect(Batch batch, Color color, float x, float y, float width, float height) {
        batch.setColor(color);
        batch.draw(theme.assets.pixel, getX() + (getWidth() - canvasWidth) / 2 + x * canvasWidth,
                getY() + (getHeight() - canvasHeight) / 2 + y * canvasHeight,
                width * canvasWidth, height * canvasHeight);
    }
}
