package dev.gamecore.client.fight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.fight.FightSnapshot;
import dev.gamecore.fight.FighterKind;

/** 街机开场的屏幕空间演出：角色名片、倒计时冲击和开战切片，不修改世界或持有纹理所有权。 */
public final class FightOpeningOverlay {
    private static final Color INK = Color.valueOf("03091b"), CYAN = Color.valueOf("55eaff"), PURPLE = Color.valueOf("d58aff"), GOLD = Color.valueOf("ffce70");
    private static final String[] WORDS = {"见面礼", "请多指教", "接得住吗", "来痛快打吧"};
    private final ThemeManager theme;
    private final FightArt art;
    private final FightTypography type;
    private final Color tint = new Color();
    public FightOpeningOverlay(ThemeManager theme, FightArt art) { this.theme = theme; this.art = art; type = new FightTypography(theme); }
    public void draw(Batch batch, FightOpening opening, FightSnapshot view, float x, float y, float w, float h) {
        if (!opening.active()) { return; }
        float s = Math.min(w / 1920, h / 720), cx = x + w / 2, cy = y + h * .52f;
        rect(batch, INK, x, y, w, h * .075f, .94f); rect(batch, INK, x, y + h * .91f, w, h * .09f, .94f);
        int player = opening.focusedPlayer();
        if (player >= 0) {
            var f = view.fighters().get(player); Color c = f.kind() == FighterKind.NAIWA ? CYAN : PURPLE;
            float center = x + w * (player == 0 ? .74f : .26f), width = w * .40f;
            rect(batch, INK, center - width / 2, y + h * .31f, width, h * .42f, .85f);
            line(batch, c, center - width / 2, y + h * .73f, center + width / 2, y + h * .73f, 3 * s, 1);
            type.draw(batch, player == 0 ? "PLAYER 01" : "PLAYER 02", center, y + h * .68f, 22 * s, width * .8f, c, 1);
            type.draw(batch, f.kind() == FighterKind.NAIWA ? "奶蛙" : "肥嘟嘟", center, y + h * .57f, 68 * s, width * .85f, c, 1);
            type.draw(batch, f.kind() == FighterKind.NAIWA ? "ELECTRIC SOUL" : "WEB MASTER", center, y + h * .43f, 17 * s, width * .85f, c, 1);
            type.draw(batch, WORDS[opening.style(player)], center, y + h * .36f, 24 * s, width * .85f, GOLD, 1);
            for (int i = 0; i < 5; i++) { line(batch, c, center - width / 2 + i * 16 * s, y + h * .29f, center - width / 2 + (i * 16 + 9) * s, y + h * .31f, 3 * s, .8f); }
        } else {
            float age = (float) opening.phaseTime(), progress = Math.min(1, Math.max(0, age));
            Color c = opening.number() == 2 ? PURPLE : opening.number() == 1 ? GOLD : CYAN;
            rect(batch, INK, x, y, w, h, .28f);
            float radius = (95 + progress * 320) * s;
            for (int ring = 0; ring < 3; ring++) {
                float r = radius + ring * 30 * s;
                for (int n = 0; n < 64; n++) { double a = n * Math.PI / 32, b = a + Math.PI / 32;
                    line(batch, c, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r * .75f,
                            cx + (float) Math.cos(b) * r, cy + (float) Math.sin(b) * r * .75f, (ring == 0 ? 4 : 1) * s, (1 - progress) * (.8f - ring * .2f)); }
            }
            for (int i = 0; i < 48; i++) {
                double a = i * Math.PI / 24 + .08; float start = (130 + progress * 180) * s, end = start + (80 + i % 5 * 34) * s;
                line(batch, i % 3 == 0 ? GOLD : c, cx + (float) Math.cos(a) * start, cy + (float) Math.sin(a) * start * .8f,
                        cx + (float) Math.cos(a) * end, cy + (float) Math.sin(a) * end * .8f, (i % 7 == 0 ? 8 : i % 3 + 1) * s, .8f * (1 - progress));
            }
            for (int i = 0; i < 24; i++) {
                double a = i * 2.399 + .3; float r = (170 + (float) Math.sqrt(progress) * 360 + i % 3 * 22) * s;
                float px = cx + (float) Math.cos(a) * r, py = cy + (float) Math.sin(a) * r * .7f;
                line(batch, i % 3 == 0 ? Color.WHITE : GOLD, px, py, px + (float) Math.cos(a + .5) * 19 * s, py + (float) Math.sin(a + .5) * 19 * s,
                        (4 + i % 4) * s, 1 - progress);
            }
            if (opening.number() > 0) {
                float slam = 1 + .7f * (float) Math.exp(-age * 16);
                digit(batch, opening.number(), cx, cy, 190 * s * slam, c);
                type.draw(batch, "GET READY", cx, cy - 155 * s, 23 * s, w * .8f, c, 1);
                if (age < .09f) { rect(batch, c, x, cy - 4 * s, w, 8 * s, (.09f - age) * 5); }
            } else {
                String label = opening.phase() == FightOpening.Phase.VERSUS ? "VERSUS" : "FIGHT!";
                rect(batch, INK, x, cy - 110 * s, w, 215 * s, .86f);
                line(batch, CYAN, x, cy + 105 * s, cx + 120 * s, cy + 105 * s, 5 * s, 1);
                line(batch, PURPLE, cx - 120 * s, cy - 110 * s, x + w, cy - 110 * s, 5 * s, 1);
                type.draw(batch, label, cx, cy + 58 * s, (opening.phase() == FightOpening.Phase.FIGHT ? 105 : 72) * s, w * .78f, GOLD, 1);
                type.draw(batch, opening.phase() == FightOpening.Phase.FIGHT ? "战斗开始" : "终极格斗", cx, cy - 45 * s, 23 * s, w * .7f, CYAN, 1);
            }
        }
        type.draw(batch, opening.paused() ? "Ⅱ 已暂停 · P / Esc 继续" : "ENTER / SPACE · 跳过开场", cx, y + h * .045f, 16 * s, w * .9f, GOLD, 1);
    }
    /** 倒计时采用代码绘制的粗切角数字，避免将小字号字体放大到模糊。 */
    private void digit(Batch b, int number, float x, float y, float size, Color c) {
        float w = size * .54f, top = y + size / 2, bottom = y - size / 2;
        if (number == 1) { segment(b, c, x - w / 3, top - size * .18f, x, top, size); segment(b, c, x, top, x, bottom, size); segment(b, c, x - w / 2, bottom, x + w / 2, bottom, size); return; }
        segment(b,c,x-w/2,top,x+w/2,top,size); segment(b,c,x+w/2,top,x+w/2,y,size);
        segment(b,c,x-w/2,y,x+w/2,y,size); segment(b,c,number==2?x-w/2:x+w/2,y,number==2?x-w/2:x+w/2,bottom,size);
        segment(b,c,x-w/2,bottom,x+w/2,bottom,size);
    }
    private void segment(Batch b, Color c, float x, float y, float xx, float yy, float size) {
        line(b,c,x,y,xx,yy,size*.20f,.12f); line(b,INK,x+size*.035f,y-size*.045f,xx+size*.035f,yy-size*.045f,size*.13f,1);
        line(b,c,x,y,xx,yy,size*.13f,1); line(b,Color.WHITE,x-size*.007f,y+size*.012f,xx-size*.007f,yy+size*.012f,size*.08f,1);
    }
    private void line(Batch b, Color c, float x, float y, float xx, float yy, float width, float alpha) { art.line(b,tint.set(c.r,c.g,c.b,Math.max(0,alpha)),x,y,xx,yy,width); }
    private void rect(Batch b, Color c, float x, float y, float w, float h, float alpha) { b.setColor(c.r,c.g,c.b,alpha); b.draw(theme.assets.pixel,x,y,w,h); }
}
