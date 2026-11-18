package dev.gamecore.client.fight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import dev.gamecore.client.ui.ThemeManager;

/** 用窗口共享白像素绘制可缩放双层描线；Widget 不拦截按钮输入，不拥有纹理释放权。 */
public final class FightIconPainter extends Widget {
    private final ThemeManager theme;
    private final Color tint=new Color();
    private FightIcon icon;
    public FightIconPainter(ThemeManager theme,FightIcon icon) { this.theme=theme;this.icon=icon;setTouchable(Touchable.disabled); }
    public void icon(FightIcon icon) { this.icon=icon; }
    @Override public void draw(Batch batch,float alpha) { draw(batch,icon,getX(),getY(),Math.min(getWidth(),getHeight()),getColor(),alpha); }
    public void draw(Batch batch,FightIcon icon,float x,float y,float size,Color accent,float alpha) {
        float packed=batch.getPackedColor();
        for(int layer=0;layer<2;layer++){
            tint.set(accent);tint.a*=alpha*(layer==0?.17f:1);batch.setColor(tint);
            float thickness=Math.max(.8f,size*(layer==0?.16f:.065f));
            for(int p=0;p<icon.paths();p++){for(int i=1;i<icon.points(p);i++){
                float ax=x+icon.x(p,i-1)*size,ay=y+icon.y(p,i-1)*size;
                float dx=(icon.x(p,i)-icon.x(p,i-1))*size,dy=(icon.y(p,i)-icon.y(p,i-1))*size;
                batch.draw(theme.assets.pixel,ax,ay-thickness/2,0,thickness/2,(float)Math.sqrt(dx*dx+dy*dy),thickness,1,1,
                        (float)Math.toDegrees(Math.atan2(dy,dx)),0,0,1,1,false,false);
            }}
        }
        batch.setPackedColor(packed);
    }
    public static FightIconPainter decorate(ThemeManager theme,TextButton button,FightIcon icon,boolean primary) {
        var painter=new FightIconPainter(theme,icon);painter.setColor(primary?ThemeManager.BACKGROUND:ThemeManager.BLUE);
        button.clearChildren();button.add(painter).size(17).padRight(8);button.add(button.getLabel()).center();return painter;
    }
}
