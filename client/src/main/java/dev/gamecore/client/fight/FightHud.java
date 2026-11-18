package dev.gamecore.client.fight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.fight.FightSession;
import dev.gamecore.fight.FightSnapshot;
import dev.gamecore.fight.FighterKind;
import dev.gamecore.client.ui.UiInk;

/** 战斗信息卡：裁角头像、连续血条/红色追踪残影、能量与四枚技能图标。只读状态且保留数字/文字，不只靠颜色。 */
public final class FightHud extends Table {
    private static final Color PURPLE=Color.valueOf("c08cff"),RED=Color.valueOf("ed6684"),EMPTY=Color.valueOf("071021");
    private final Label name,values,combo;
    private final Meter health,energy;
    private final Portrait portrait;
    private final SkillSlot[] skills=new SkillSlot[FightHudModel.Skill.values().length];
    private final FightChrome chrome;
    private final int player;
    private FightUiSkin skin=FightUiSkin.NEON;
    public FightHud(ThemeManager theme,int player){
        this.player=player;
        setName("fight-hud-"+player);
        chrome=new FightChrome(theme,player==0?ThemeManager.BLUE:PURPLE);setBackground(chrome);pad(13,14,11,14);
        name=new FightDisplayLabel(theme,"",23,Color.WHITE);values=theme.caption("");values.setName("fight-values-"+player);values.setFontScale(.88f);
        combo=theme.caption("MAX 0");portrait=new Portrait(theme,player);
        health=new Meter(theme,false,player==1);energy=new Meter(theme,true,player==1);
        Table labels=new Table();
        if(player==0){labels.add(name).left().expandX();labels.add(combo).right().row();}
        else{labels.add(combo).left();labels.add(name).right().expandX().row();}
        labels.add(health).colspan(2).growX().height(20).padTop(3).row();
        labels.add(energy).colspan(2).growX().height(8).padTop(3).row();
        labels.add(values).colspan(2).growX().height(15).padTop(2);
        values.setAlignment(player==0?com.badlogic.gdx.utils.Align.left:com.badlogic.gdx.utils.Align.right);
        if(player==0){add(portrait).size(72).padRight(9);add(labels).growX().row();}
        else{add(labels).growX();add(portrait).size(72).padLeft(9).row();}
        Table rail=new Table();
        for(var skill:FightHudModel.Skill.values()){var slot=new SkillSlot(theme,skill,player);skills[skill.ordinal()]=slot;rail.add(slot).growX().uniformX().height(25).padRight(skill==FightHudModel.Skill.GUARD?0:5);}
        add(rail).colspan(2).growX().padTop(5);skin(skin);
    }
    public void skin(FightUiSkin value){skin=value;chrome.skin(value,player);portrait.skin=value;health.skin=energy.skin=value;for(var slot:skills){slot.skin=value;slot.player=player;}}
    public FightUiSkin skin(){return skin;}
    /** 按实际输入来源显示技能键；远端对手只显示技能名。 */
    public void controls(int keyPlayer){for(var slot:skills){slot.label=slot.skill.label(keyPlayer);}}
    public void paused(boolean value){health.paused=energy.paused=value;}
    public void reset(){health.model.reset(1);energy.model.reset(.3f);health.time=energy.time=0;name.setText("");values.setText("");combo.setText("MAX 0");for(var slot:skills){slot.ready=slot.active=false;}}
    public void update(FightSnapshot.Fighter fighter){
        Color accent=skin.side(player);
        name.setText("P"+(fighter.player()+1)+"  "+fighter.kind());name.setColor(accent);
        values.setText("HP "+fighter.health()+" / "+FightSession.MAX_HEALTH+"   EN "+(int)fighter.energy()+" / 100");
        health.model.target(fighter.health()/(float)FightSession.MAX_HEALTH);energy.model.target((float)fighter.energy()/100);
        portrait.kind=fighter.kind();portrait.hurt=fighter.action()==FightSnapshot.Action.HURT||fighter.action()==FightSnapshot.Action.HURT_HEAVY;
        combo.setText(fighter.comboCount()>=2?fighter.comboCount()+" HIT":"MAX "+fighter.maxCombo());combo.setColor(fighter.comboCount()>=2?ThemeManager.GOLD:ThemeManager.MUTED);
        for(var slot:skills){slot.update(fighter);}
    }
    public SkillSlot skill(FightHudModel.Skill skill){return skills[skill.ordinal()];}
    /** 独立技能格保留完整文字；N 表示小键盘，Bot 模式的自动操作仍由底栏说明。 */
    public static final class SkillSlot extends Widget {
        private final ThemeManager theme;private final FightHudModel.Skill skill;private final FightIconPainter icons;
        private final com.badlogic.gdx.graphics.g2d.GlyphLayout text=new com.badlogic.gdx.graphics.g2d.GlyphLayout();
        private String label;private final Color tint=new Color();
        private FightUiSkin skin=FightUiSkin.NEON;private int player;
        private FighterKind kind=FighterKind.NAIWA;private boolean ready,active;
        private SkillSlot(ThemeManager theme,FightHudModel.Skill skill,int player){this.theme=theme;this.skill=skill;icons=new FightIconPainter(theme,FightIcon.FIST);
            label=skill.key(player)+" "+skill.title()+(skill.cost()>0?" "+skill.cost():"");setName("fight-skill-"+player+"-"+skill.name().toLowerCase(java.util.Locale.ROOT));}
        private void update(FightSnapshot.Fighter f){kind=f.kind();ready=skill.ready(f.energy());active=skill.active(f.action());}
        public boolean ready(){return ready;}public boolean active(){return active;}public String label(){return label;}public FightIcon icon(){return skill.icon(kind);}
        @Override public void draw(Batch batch,float alpha){
            float packed=batch.getPackedColor(),x=getX(),y=getY(),w=getWidth(),h=getHeight();
            Color accent=skill==FightHudModel.Skill.ULTIMATE?ThemeManager.GOLD:skin.side(player);
            batch.setColor(accent.r,accent.g,accent.b,alpha*(active?1:.42f));batch.draw(theme.assets.pixel,x,y,w,h);
            batch.setColor(skin.panel);batch.draw(theme.assets.pixel,x+1,y+1,w-2,h-2);
            batch.setColor(accent.r,accent.g,accent.b,alpha*(active?.35f:.07f));batch.draw(theme.assets.pixel,x+2,y+2,w-4,h-4);
            batch.setColor(accent.r,accent.g,accent.b,alpha*(ready?.8f:.2f));batch.draw(theme.assets.pixel,x+6,y,Math.min(w-12,30),1);
            tint.set(ready?accent:ThemeManager.MUTED);icons.draw(batch,icon(),x+5,y+5,15,tint,ready?alpha:alpha*.4f);
            var font=theme.assets.small;float sx=font.getData().scaleX,sy=font.getData().scaleY;
            try{font.getData().setScale(sx*.83f,sy*.83f);text.setText(font,label);float fit=Math.min(1,(w-29)/Math.max(1,text.width));font.getData().setScale(sx*.83f*fit,sy*.83f*fit);
                font.setColor(ready?ThemeManager.TEXT:ThemeManager.MUTED);font.draw(batch,label,x+25,y+h/2+font.getCapHeight()/2);
            }finally{font.getData().setScale(sx,sy);font.setColor(Color.WHITE);batch.setPackedColor(packed);}
        }
    }
    private static final class Portrait extends Widget {
        private final ThemeManager theme;private final TextureRegion frog,bunny;private final FightIconPainter icons;private final int player;
        private FighterKind kind=FighterKind.NAIWA;private boolean hurt;private FightUiSkin skin=FightUiSkin.NEON;private final UiInk ink;
        private Portrait(ThemeManager theme,int player){this.theme=theme;this.player=player;ink=new UiInk(theme);icons=new FightIconPainter(theme,FightIcon.BOLT);frog=new TextureRegion(theme.assets.fighters,185,18,145,120);bunny=new TextureRegion(theme.assets.fighters,218,704,145,135);}
        @Override public void draw(Batch batch,float alpha){
            float packed=batch.getPackedColor(),x=getX(),y=getY(),w=getWidth(),cx=x+w/2,cy=y+w/2;Color accent=hurt?RED:skin.side(player);
            batch.setColor(accent.r,accent.g,accent.b,alpha*.13f);batch.draw(theme.assets.circle,x-5,y-5,w+10,w+10);
            batch.setColor(skin.metal);batch.draw(theme.assets.circle,x,y,w,w);batch.setColor(EMPTY);
            batch.draw(theme.assets.circle,x+3,y+3,w-6,w-6);
            ink.ring(batch,accent,alpha,cx,cy,w/2-1,0,360,2);
            ink.ring(batch,accent,alpha*.6f,cx,cy,w/2+3,player==0?25:205,130,1);
            for(int i=0;i<12;i++){ink.ring(batch,Color.WHITE,alpha*.8f,cx,cy,w/2-4,i*30,9,1);}
            batch.setColor(1,1,1,alpha);batch.draw(kind==FighterKind.NAIWA?frog:bunny,x+8,y+8,w-16,w-16);
            icons.draw(batch,kind==FighterKind.NAIWA?FightIcon.BOLT:FightIcon.WEB,x+(player==0?w-15:-2),y-2,20,accent,alpha);batch.setPackedColor(packed);
        }
    }
    private static final class Meter extends Widget {
        private final ThemeManager theme;private final boolean energy,reverse;private final FightHudModel.Meter model;
        private float time;private boolean paused;
        private FightUiSkin skin=FightUiSkin.NEON;
        private Meter(ThemeManager theme,boolean energy,boolean reverse){this.theme=theme;this.energy=energy;this.reverse=reverse;model=new FightHudModel.Meter(energy?.3f:1);}
        @Override public void act(float delta){super.act(delta);if(paused||!Float.isFinite(delta)||delta<=0){return;}time+=Math.min(delta,.1f);model.advance(delta,false);}
        @Override public void draw(Batch batch,float alpha){
            float packed=batch.getPackedColor(),x=getX(),y=getY(),w=getWidth(),h=getHeight(),inner=w-4;
            batch.setColor(skin.metal);batch.draw(theme.assets.pixel,x,y,w,h);batch.setColor(EMPTY);batch.draw(theme.assets.pixel,x+1,y+1,w-2,h-2);
            if(!energy){bar(batch,RED,inner*model.trail(),x,y,inner,h);bar(batch,model.value()<.25f?RED:skin.health,inner*model.value(),x,y,inner,h);
                batch.setColor(0,0,0,alpha*.14f);batch.draw(theme.assets.pixel,x+2,y+2,inner,h*.35f);
                batch.setColor(1,1,1,alpha*(model.delay()>0?.65f:.18f));batch.draw(theme.assets.pixel,x+2+(reverse?inner*(1-model.value()):0),y+h-4,inner*model.value(),2);
                for(int i=1;i<10;i++){batch.setColor(EMPTY.r,EMPTY.g,EMPTY.b,.5f);batch.draw(theme.assets.pixel,x+2+i*inner/10,y+2,1,h-4);}
            }else{
                for(int i=0;i<20;i++){float segment=Math.max(0,Math.min(1,model.value()*20-i)),cell=inner/20;float xx=x+2+(reverse?19-i:i)*cell;
                    batch.setColor(i<14?skin.side(reverse?1:0):ThemeManager.GOLD);batch.draw(theme.assets.pixel,xx,y+2,(cell-2)*segment,h-4);}
                if(model.value()>=.999f){batch.setColor(1,.85f,.5f,alpha*(.5f+.3f*(float)Math.sin(time*6)));batch.draw(theme.assets.pixel,x+1,y+h-1,w-2,1);}
            }batch.setPackedColor(packed);
        }
        private void bar(Batch b,Color c,float length,float x,float y,float inner,float h){b.setColor(c);b.draw(theme.assets.pixel,x+2+(reverse?inner-length:0),y+2,length,h-4);}
    }
}
