package dev.gamecore.client.fight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.fight.FightSnapshot;

/** 胜者特写名片和真实伤害/最大连击；平局不进入本胜者层，素材/字体仍由窗口拥有。 */
public final class FightVictoryOverlay {
    private static final Color GOLD=Color.valueOf("ffd17b"),INK=Color.valueOf("060b1b"),WHITE=Color.valueOf("f8fcff");
    private final ThemeManager theme;
    private final FightTypography type;
    private final FightArt art;
    private String name="",damage="",combo="";
    public FightVictoryOverlay(ThemeManager theme,FightArt art){this.theme=theme;this.art=art;type=new FightTypography(theme);}
    public void update(FightSnapshot view,FightVictory victory){if(victory.winner()>=0){var f=view.fighters().get(victory.winner());name="P"+(f.player()+1)+" "+f.kind()+" 胜利";damage=f.totalDamage()+" DAMAGE";combo="MAX COMBO "+f.maxCombo();}}
    public void draw(Batch batch,FightSnapshot view,FightVictory victory,float x,float y,float w,float h){
        if(victory.focusedPlayer()<0){return;}
        float s=Math.min(w/1920,h/720),cx=x+w*(victory.winner()==0?.74f:.26f),width=w*.43f;
        batch.setColor(INK.r,INK.g,INK.b,.9f);batch.draw(theme.assets.pixel,cx-width/2,y+h*.26f,width,h*.51f);
        art.line(batch,GOLD,cx-width/2,y+h*.77f,cx+width/2,y+h*.77f,3*s);
        type.draw(batch,"VICTORY",cx,y+h*.68f,70*s,width*.92f,GOLD,1);
        type.draw(batch,name,cx,y+h*.53f,38*s,width*.9f,GOLD,1);
        type.draw(batch,damage,cx,y+h*.41f,26*s,width*.8f,WHITE,1);
        type.draw(batch,combo,cx,y+h*.32f,22*s,width*.8f,GOLD,1);
        type.draw(batch,victory.paused()?"Ⅱ 庆祝已暂停 · P / Esc 继续":"功夫庆祝 · P / Esc 暂停 · 再来一局",x+w/2,y+h*.08f,18*s,w*.9f,GOLD,1);
        if(victory.danceTime()<5.5){for(int i=0;i<34;i++){float px=x+(float)((i*137+victory.danceTime()*40)%1920)/1920*w;
            float py=y+h-(float)((i*73+victory.danceTime()*85)%720)/720*h;
            art.line(batch,i%3==0?WHITE:GOLD,px,py,px+5*s,py+11*s,(3+i%3)*s);}}
    }
}
