package dev.gamecore.client.snake;

import com.badlogic.gdx.scenes.scene2d.ui.*;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.snake.SnakeMode;

/** 本地与在线共用的排行榜窗口；调用页决定记录命名空间、暂停和关闭生命周期。 */
public final class SnakeLeaderboard {
    private SnakeLeaderboard() { }
    public static Dialog create(ThemeManager theme,SnakeRecords records,SnakeMode mode,String modeName,String title,TextButton close){
        Dialog dialog;
        Window.WindowStyle rankingStyle = new Window.WindowStyle(theme.assets.heading, ThemeManager.TEXT,
                theme.surface(ThemeManager.PANEL, ThemeManager.BLUE));
        rankingStyle.stageBackground = theme.solid(com.badlogic.gdx.graphics.Color.valueOf("030912bf"));
        dialog = new Dialog(title, rankingStyle);
        dialog.setName("snake-ranking-dialog");
        dialog.setMovable(false);
        dialog.pad(32, 24, 20, 24);
        Table body = dialog.getContentTable();
        body.add(theme.text(modeName + "  ·  个人前五名"))
                .width(440).left().padBottom(14).row();
        var entries = records.leaderboard(mode);
        if (entries.isEmpty()) { body.add(theme.caption("暂无记录，完成一局后自动入榜。")).left().pad(12).row(); }
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            Table entryPanel = new Table();
            entryPanel.setBackground(theme.panel(com.badlogic.gdx.graphics.Color.valueOf("172a40")));
            Label row = theme.label(String.format(java.util.Locale.ROOT, "%02d   /   P%d", i + 1, entry.player()),
                    theme.assets.body, i == 0 ? ThemeManager.GOLD : ThemeManager.TEXT);
            row.setName("snake-rank-" + i);
            entryPanel.add(row).left().expandX();
            Label points = theme.label(entry.score() + " 分", theme.assets.heading, ThemeManager.MINT);
            points.setName("snake-rank-score-" + i);
            entryPanel.add(points).right();
            entryPanel.pad(10, 14, 10, 14);
            body.add(entryPanel).growX().padBottom(6).row();
        }
        body.add(theme.caption("按模式分榜；同分先完成者在前；中途退出不入榜。"))
                .left().padTop(14).row();
        Label saveNotice = theme.caption(records.notice());
        saveNotice.setWrap(true);
        body.add(saveNotice).width(440).left().padTop(8).row();
        body.add(close).growX().height(40).padTop(16);
        return dialog;
    }
}
