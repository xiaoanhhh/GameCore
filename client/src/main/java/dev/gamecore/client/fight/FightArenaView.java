package dev.gamecore.client.fight;

import com.badlogic.gdx.scenes.scene2d.ui.*;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.fight.FightSnapshot;
import dev.gamecore.fight.FightSession;

/** 本地与在线共用的格斗舞台、镜像 HUD 和操作栏。只读快照；按钮行为由页面注入，不拥有模拟或连接。 */
public final class FightArenaView extends Table {
    private final ThemeManager theme;
    private final boolean bot;
    private final FightBoard board;
    private final FightHud oneHud,twoHud;
    private final Label status,timer,arenaTitle,oneNote,twoNote;
    private final FightChrome crestChrome,railChrome,oneGuideChrome,twoGuideChrome;
    private final java.util.List<TextButton> arenaButtons=new java.util.ArrayList<>();
    private final Label[] guideBadges=new Label[2];
    private FightUiSkin skin;
    private int localPlayer=-1;
    public FightArenaView(ThemeManager theme,String modeLabel,boolean bot,java.util.List<TextButton> buttons) {
        this.theme=theme;this.bot=bot;setName("fight-arena");
        oneHud = new FightHud(theme, 0); twoHud = new FightHud(theme, 1);
        Table top = new Table(); top.pad(3, 8, 3, 8);
        top.add(oneHud).growX().uniformX().minWidth(0);
        Table center = new Table();crestChrome=new FightChrome(theme,ThemeManager.BLUE).shield();center.setBackground(crestChrome);center.pad(13,24,10,24);
        arenaTitle=new FightDisplayLabel(theme, "终极格斗", 22, ThemeManager.BLUE);center.add(arenaTitle).growX().height(24).row();
        var english=theme.caption("ULTIMATE ARENA");english.setFontScale(.82f);center.add(english).height(12).row();
        timer = new FightDisplayLabel(theme, "99", 43, ThemeManager.GOLD); timer.setName("fight-time");
        center.add(timer).growX().height(46).row();
        center.add(theme.caption(modeLabel)).height(15);
        top.add(center).width(176).padLeft(2).padRight(2); top.add(twoHud).growX().uniformX().minWidth(0);
        add(top).growX().height(132).row();
        board = new FightBoard(theme); board.setName("fight-board");
        add(board).grow().row();
        Table bottom = new Table(); bottom.pad(2,8,6,8);
        Table rail=new Table();railChrome=new FightChrome(theme,ThemeManager.BLUE);rail.setBackground(railChrome);rail.pad(7,12,7,12);
        var fightMark=new FightDisplayLabel(theme,"FIGHT!",34,ThemeManager.GOLD);rail.add(fightMark).width(139).height(46).padRight(8);
        status = new FightDisplayLabel(theme, "", 14, ThemeManager.BLUE); status.setName("fight-state");status.setEllipsis(true);
        rail.add(status).growX().minWidth(0).height(42).padRight(9);
        for (int i=0;i<buttons.size();i++) {
            TextButton button=buttons.get(i);
            rail.add(button).width(i==0?110:i==1?142:126).height(38).padRight(i==buttons.size()-1?0:6);
        }
        arenaButtons.addAll(buttons);bottom.add(rail).colspan(2).growX().minWidth(0).height(61).row();
        oneGuideChrome=new FightChrome(theme,ThemeManager.BLUE);twoGuideChrome=new FightChrome(theme,ThemeManager.PLAYER_TWO);
        oneNote=theme.caption("");twoNote=theme.caption("");
        bottom.add(guide(0,oneGuideChrome,oneNote)).growX().uniformX().minWidth(0).height(51).padTop(3).padRight(4);
        bottom.add(guide(1,twoGuideChrome,twoNote)).growX().uniformX().minWidth(0).height(51).padTop(3);
        add(bottom).growX().height(123);
    }
    public FightBoard board(){return board;}
    public Label status(){return status;}
    public void reset(){board.restart();oneHud.reset();twoHud.reset();timer.setText("--");oneNote.setText("");twoNote.setText("");}
    /** 在线双方都用本机 P1 键位；对手技能仅显示名称，不冒充可操作的本地 P2。 */
    public void localPlayer(int player){localPlayer=player;oneHud.controls(player==0?0:-1);twoHud.controls(player==1?0:-1);}
    public void update(FightSnapshot view,boolean frozen){
        applySkin(view);oneHud.paused(frozen);twoHud.paused(frozen);
        oneHud.update(view.fighters().get(0));twoHud.update(view.fighters().get(1));board.update(view);
        long seconds=Math.max(0,(FightSession.MATCH_TICKS-view.tick()+59)/60);
        timer.setText(Long.toString(seconds));timer.setColor(seconds<10?com.badlogic.gdx.graphics.Color.SALMON:ThemeManager.GOLD);
        oneNote.setText("L 冲刺 · Shift+下+跳 穿台 · F1 判定 · 奶蛙 Y/9 大笑"+FightHudModel.blinkLabel(view.fighters().get(0)));
        twoNote.setText("N3 冲刺 · N=小键盘 · P/Esc 暂停"+FightHudModel.blinkLabel(view.fighters().get(1)));
        if(localPlayer>=0){
            for(int p=0;p<2;p++){
                Label keys=findActor("fight-keys-"+(p+1));
                keys.setText(p==localPlayer?"你的操作 · A/D 移动  W/K 跳跃  J 出拳  U 远程  I 奥义  S 格挡":"对手 · 由另一位玩家操作");
                (p==0?oneNote:twoNote).setText((p==localPlayer?"L 冲刺 · Shift+S+K 穿台 · Y/9 大笑":"在线比赛 · 切换窗口后继续")+FightHudModel.blinkLabel(view.fighters().get(p),p==localPlayer?0:-1));
            }
        }
    }
    /** 操作栏与真实按键/冷却共用投影；文字保留 N=小键盘，图标只是辅助识别。 */
    private Table guide(int player,FightChrome chrome,Label note){
        Table guide=new Table();guide.setBackground(chrome);guide.pad(9,15,8,15);
        var badge=new FightDisplayLabel(theme,"P"+(player+1),21,player==0?ThemeManager.BLUE:ThemeManager.PLAYER_TWO);
        guideBadges[player]=badge;
        guide.add(badge).width(38).padRight(8);
        Table copy=new Table();var keys=theme.caption(player==0?"A / D 疾跑   W / K 二段跳   J 出拳   U 远程   I 奥义   S 格挡":
                bot?"BOT 自动操作 · 出拳 / 远程 / 奥义 / 格挡":"← / → 移动   ↑ / N2 跳   N1 拳   N4 远程   N5 奥义   ↓ 格挡");
        keys.setName(player==0?"fight-keys-1":"fight-keys-2");keys.setFontScale(.8f);keys.setEllipsis(true);copy.add(keys).left().growX().row();
        note.setFontScale(.78f);note.setEllipsis(true);copy.add(note).left().growX().padTop(3);guide.add(copy).growX();return guide;
    }
    /** 仅确认地图、创建新局后切换战斗皮肤，草稿浏览不会污染原局。 */
    private void applySkin(FightSnapshot view){
        var next=FightUiSkin.of(view.map());if(skin==next){return;}skin=next;oneHud.skin(skin);twoHud.skin(skin);
        crestChrome.skin(skin,0);railChrome.skin(skin,0);oneGuideChrome.skin(skin,0);twoGuideChrome.skin(skin,1);arenaTitle.setColor(skin.primary);status.setColor(skin.primary);
        guideBadges[0].setColor(skin.primary);guideBadges[1].setColor(skin.secondary);
        for(int i=0;i<arenaButtons.size();i++){var button=arenaButtons.get(i);var style=button.getStyle();
            style.fontColor=ThemeManager.TEXT;boolean home=i==arenaButtons.size()-1;style.up=new FightChrome(theme,skin.primary).skin(skin,home?1:0);
            style.over=theme.surface(skin.panel,ThemeManager.TEXT);style.focused=style.over;style.down=theme.surface(skin.metal,skin.primary);
            button.setStyle(style);button.pad(4,8,4,8);
            for(var child:button.getChildren()){if(child instanceof FightIconPainter painter){painter.setColor(skin.side(home?1:0));}}
        }
    }
}
