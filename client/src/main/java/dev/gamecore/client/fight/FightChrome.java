package dev.gamecore.client.fight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.client.ui.UiInk;

/** 分层金属/光轨面板；五地图绘制不同边饰，只借用白像素，无输入、计时或纹理释放权。 */
public final class FightChrome extends BaseDrawable {
    private static final Color DEEP=Color.valueOf("050a16"),WHITE=Color.valueOf("e3f5ff");
    private final UiInk ink;private final Color accent=new Color();
    private FightUiSkin skin=FightUiSkin.NEON;private boolean reverse,shield;
    public FightChrome(ThemeManager theme,Color accent){ink=new UiInk(theme);this.accent.set(accent);}
    public void accent(Color value){accent.set(value);}
    public FightChrome skin(FightUiSkin value,int player){skin=value;accent.set(value.side(player));reverse=player==1;return this;}
    public FightChrome shield(){shield=true;return this;}
    public FightUiSkin skin(){return skin;}
    @Override public void draw(Batch b,float x,float y,float w,float h){
        float packed=b.getPackedColor(),a=b.getColor().a,cut=Math.min(shield?26:16,h*.26f);
        if(shield){
            // 翼片限制在徽章内，避免遮住两侧实时数值。
            for(int side=0;side<2;side++){float sign=side==0?1:-1,xx=side==0?x:x+w;
                ink.quad(b,skin.metal,a,xx,y+h-10,xx+sign*27,y+h-19,xx+sign*39,y+23,xx+sign*12,y+42);
                ink.line(b,skin.side(side),a*.95f,xx+sign*5,y+h-13,xx+sign*24,y+35,3);
                ink.line(b,WHITE,a*.7f,xx+sign*11,y+h-18,xx+sign*31,y+31,1);
            }
            x+=17;w-=34;
        }
        plate(b,accent,a*.12f,x-3,y-2,w+6,h+4,cut+2);
        plate(b,skin.metal,a,x,y,w,h,cut);plate(b,DEEP,a,x+2,y+2,w-4,h-4,cut-1);
        plate(b,accent,a*.8f,x+4,y+4,w-8,h-8,cut-2);plate(b,skin.panel,a,x+6,y+6,w-12,h-12,cut-3);
        ink.line(b,WHITE,a*.65f,x+cut,y+h-1,x+w-cut,y+h-1,1);
        ink.line(b,accent,a*.95f,x+cut+3,y+h-5,x+w-cut-3,y+h-5,2);
        ink.line(b,accent,a*.8f,x+cut+3,y+5,x+w-cut-3,y+5,2);
        ink.line(b,accent,a*.9f,x+5,y+cut,x+5,y+h-cut,2);
        ink.line(b,accent,a*.9f,x+w-5,y+cut,x+w-5,y+h-cut,2);
        ink.line(b,WHITE,a*.35f,x+6,y+cut,x+cut,y+6,1);
        ink.line(b,WHITE,a*.35f,x+w-cut,y+h-6,x+w-6,y+h-cut,1);
        if(h>65){ink.rect(b,WHITE,a*.025f,x+cut,y+h*.55f,w-cut*2,h*.25f);}
        ornaments(b,x,y,w,h,a);b.setPackedColor(packed);
    }
    private void plate(Batch b,Color c,float a,float x,float y,float w,float h,float cut){
        if(w<=0||h<=0){return;}
        ink.rect(b,c,a,x+cut,y,w-2*cut,h);
        ink.quad(b,c,a,x,y+cut,x+cut,y,x+cut,y+h,x,y+h-cut);
        ink.quad(b,c,a,x+w-cut,y,x+w,y+cut,x+w,y+h-cut,x+w-cut,y+h);
    }
    private void ornaments(Batch b,float x,float y,float w,float h,float a){
        float sign=reverse?-1:1,start=reverse?x+w-28:x+28,yy=y+h-10;
        for(int i=0;i<6;i++){
            float xx=start+sign*i*8;
            switch(skin){
                case NEON -> ink.line(b,accent,a*.8f,xx,yy,xx+sign*4,yy-4,2);
                case TEMPLE -> ink.ring(b,accent,a*.65f,xx,yy-2,3,0,180,1);
                case FORGE -> {ink.rect(b,skin.metal,a,xx,yy-3,3,3);ink.rect(b,WHITE,a*.6f,xx,yy-1,2,1);}
                case BAMBOO -> {ink.line(b,accent,a*.6f,xx,yy-3,xx+sign*6,yy-3,1);ink.line(b,accent,a*.6f,xx,yy-5,xx,yy-1,1);}
                case FROST -> ink.quad(b,accent,a*.8f,xx,yy-4,xx+3,yy-1,xx,yy+2,xx-3,yy-1);
            }
        }
        if(w>250){for(int side=0;side<2;side++){float xx=side==0?x+17:x+w-17,sy=y+Math.min(18,h*.3f);
            if(skin==FightUiSkin.FORGE){ink.ring(b,skin.metal,a,xx,sy,3,0,360,2);}
            else if(skin==FightUiSkin.FROST){ink.line(b,accent,a*.6f,xx-5,sy,xx+5,sy,1);ink.line(b,accent,a*.6f,xx,sy-5,xx,sy+5,1);}
            else{ink.line(b,accent,a*.6f,xx-4,sy-3,xx+4,sy+3,1);}
        }}
    }
}
