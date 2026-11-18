package dev.gamecore.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;

/** 可缩放装饰几何；借用窗口白像素，调用方负责恢复 Batch 色值，不创建额外纹理或 ShapeRenderer。 */
public final class UiInk {
    private final ThemeManager theme;
    private final float[] vertices = new float[20];
    private final Color tint = new Color();
    public UiInk(ThemeManager theme) { this.theme = theme; }
    public void rect(Batch b, Color c, float a, float x, float y, float w, float h) {
        if (w <= 0 || h <= 0) { return; }
        b.setColor(c.r,c.g,c.b,a*c.a); b.draw(theme.assets.pixel,x,y,w,h);
    }
    public void line(Batch b,Color c,float a,float x,float y,float xx,float yy,float thickness) {
        float dx=xx-x,dy=yy-y;b.setColor(c.r,c.g,c.b,a*c.a);
        b.draw(theme.assets.pixel,x,y-thickness/2,0,thickness/2,(float)Math.hypot(dx,dy),thickness,1,1,
                (float)Math.toDegrees(Math.atan2(dy,dx)),0,0,1,1,false,false);
    }
    public void quad(Batch b,Color c,float a,float x,float y,float xx,float yy,float xxx,float yyy,float xxxx,float yyyy) {
        float bits=tint.set(c.r,c.g,c.b,a*c.a).toFloatBits();
        vertices[0]=x;vertices[1]=y;vertices[5]=xx;vertices[6]=yy;vertices[10]=xxx;vertices[11]=yyy;vertices[15]=xxxx;vertices[16]=yyyy;
        for(int i=0;i<4;i++){int p=i*5;vertices[p+2]=bits;vertices[p+3]=vertices[p+4]=0;}
        b.draw(theme.assets.pixel,vertices,0,20);
    }
    public void ring(Batch b,Color c,float a,float cx,float cy,float radius,float start,float sweep,float thickness) {
        int steps=Math.max(2,(int)(Math.abs(sweep)/6));
        for(int i=0;i<steps;i++){double from=Math.toRadians(start+sweep*i/steps),to=Math.toRadians(start+sweep*(i+1)/steps);
            line(b,c,a,cx+radius*(float)Math.cos(from),cy+radius*(float)Math.sin(from),cx+radius*(float)Math.cos(to),cy+radius*(float)Math.sin(to),thickness);}
    }
    public void horizontalFade(Batch b,Color c,float leftAlpha,float rightAlpha,float x,float y,float w,float h){
        vertices[0]=x;vertices[1]=y;vertices[5]=x+w;vertices[6]=y;vertices[10]=x+w;vertices[11]=y+h;vertices[15]=x;vertices[16]=y+h;
        for(int i=0;i<4;i++){int p=i*5;vertices[p+2]=tint.set(c.r,c.g,c.b,(i==0||i==3?leftAlpha:rightAlpha)*c.a).toFloatBits();vertices[p+3]=vertices[p+4]=0;}
        b.draw(theme.assets.pixel,vertices,0,20);
    }
}
