package dev.gamecore.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

/** Snake/Tank 本地与联机共用的侧栏排版；状态和可用操作由调用页提供，不拥有规则或资源。 */
public final class ArenaPanels {
    private ArenaPanels() { }
    public static ScrollPane scroll(ThemeManager theme,Table panel){
        var scroll=new ScrollPane(panel,theme.scrollStyle());
        scroll.setScrollingDisabled(true,false);scroll.setFadeScrollBars(false);return scroll;
    }
    private static Table header(ThemeManager theme,String kicker,String title,String mode){
        Table panel=new Table();panel.top().pad(14);panel.setBackground(theme.panel(ThemeManager.PANEL));
        panel.add(theme.label(kicker,theme.assets.small,ThemeManager.BLUE)).left().padBottom(10).row();
        panel.add(theme.label(title,theme.assets.heading,ThemeManager.TEXT)).left().row();
        panel.add(theme.caption(mode)).left().padTop(4).row();return panel;
    }
    public static Table snake(ThemeManager theme,String kicker,String mode,Label score,Label best,Label speed,
                              Label keys,Actor actions,Label state,Label notice){
        Table panel=header(theme,kicker,"Snake Arena",mode+"  /  20 × 20");
        panel.add(score).growX().left().padTop(12).row();panel.add(best).growX().left().padTop(6).row();
        speed.setWrap(true);panel.add(speed).growX().padTop(8).row();
        Table keyPanel=new Table();keyPanel.setBackground(theme.panel(Color.valueOf("0c1727")));
        keyPanel.add(keys).left().growX().pad(9);panel.add(keyPanel).growX().padTop(10).row();
        panel.add(actions).growX().padTop(10).row();
        Table statePanel=new Table();statePanel.setBackground(theme.surface(Color.valueOf("0d2934"),ThemeManager.LINE));
        statePanel.add(state).growX().pad(10);panel.add(statePanel).growX().padTop(12).row();
        panel.add(notice).growX().padTop(10).row();
        panel.add(theme.caption("Tab 切换焦点 · Enter 确认")).left().padTop(14).row();panel.add().expandY();return panel;
    }
    public static Table tank(ThemeManager theme,String kicker,String mode,Label health,Label time,Label state,
                             Actor options,Label keys,Actor actions,Label notice){
        return tank(theme,kicker,mode,null,health,time,state,options,keys,actions,notice);
    }
    /**
     * Tank 侧栏：标题 → 对阵卡（图形生命条可空、倒计时、文字生命读数）→ 状态条 → 选项、按键、操作。
     * 1280×720 下整栏应放得下，不靠滚动才能看到操作按钮。
     * 文字标签仍由调用页创建和更新，这里只统一字号与容器，联机与本地共用同一排版。
     */
    public static Table tank(ThemeManager theme,String kicker,String mode,Actor scoreboard,Label health,Label time,Label state,
                             Actor options,Label keys,Actor actions,Label notice){
        Table panel=header(theme,kicker,"Tank Arena",mode);
        Table score=new Table();score.setBackground(theme.panel(Color.valueOf("0c1727")));score.pad(10,12,8,12);
        if(scoreboard!=null){score.add(scoreboard).growX().height(44).row();}
        // 倒计时用正文字号居中；在线验收要求它保持单行（高度 < 30），故不用标题字号。
        time.setStyle(new Label.LabelStyle(theme.assets.body,ThemeManager.TEXT));time.setAlignment(com.badlogic.gdx.utils.Align.center);
        score.add(time).growX().padTop(scoreboard==null?0:6).row();
        if(scoreboard!=null){health.setStyle(new Label.LabelStyle(theme.assets.small,ThemeManager.MUTED));health.setAlignment(com.badlogic.gdx.utils.Align.center);}
        score.add(health).growX().padTop(2).row();
        panel.add(score).growX().padTop(10).row();
        Table statePanel=new Table();statePanel.setBackground(theme.surface(Color.valueOf("0d2934"),ThemeManager.LINE));
        statePanel.add(state).growX().pad(8,10,8,10);panel.add(statePanel).growX().padTop(8).row();
        panel.add(options).growX().padTop(10).row();
        Table keyPanel=new Table();keyPanel.setBackground(theme.panel(Color.valueOf("0c1727")));
        keyPanel.add(keys).left().growX().pad(4,2,4,2);panel.add(keyPanel).growX().padTop(10).row();
        panel.add(actions).growX().padTop(8).row();
        panel.add(notice).growX().height(0).row();
        panel.add(theme.caption("绿十字：停留 2 秒恢复 1 点生命\n每局 3 分钟，到时比较生命值")).left().padTop(10).row();
        panel.add().expandY();return panel;
    }
}
