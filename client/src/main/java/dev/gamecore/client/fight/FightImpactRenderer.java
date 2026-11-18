package dev.gamecore.client.fight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.fight.FighterKind;

/** 命中局部演出：拳风、盾弧、电枝、蛛网与终结冲击分型。全部借用窗口画笔，不拥有规则、声音或 GL 资源。 */
public final class FightImpactRenderer {
    private static final Color CYAN=Color.valueOf("58eaff"),PURPLE=Color.valueOf("c68aff"),GOLD=Color.valueOf("ffd086"),WHITE=Color.valueOf("f4ffff"),INK=Color.valueOf("050c21");
    private final FightArt art;private final FightTypography type;private final Color tint=new Color();
    public FightImpactRenderer(ThemeManager theme,FightArt art){this.art=art;type=new FightTypography(theme);}
    public void draw(Batch batch,FightImpactFeedback feedback,FightCamera camera){
        for(int index=0;index<FightImpactFeedback.CAPACITY;index++){
            var hit=feedback.hit(index);if(!hit.alive()){continue;}
            float t=hit.progress(),fade=(1-t)*(1-t),radius=hit.style()==FightImpactFeedback.Style.ULTIMATE?215:hit.style()==FightImpactFeedback.Style.HOOK?130:100;
            Color accent=hit.style()==FightImpactFeedback.Style.BLOCK?GOLD:hit.kind()==FighterKind.NAIWA?CYAN:PURPLE;
            float px=camera.x(hit.x()),py=camera.y(hit.y()),unit=camera.unit();
            // 暗色背衬让白芯在明亮场景中仍可读，局部光晕不覆盖整块舞台或 HUD。
            art.oval(batch,color(INK,.42f*fade),px,py,radius*2.1f*unit,radius*1.5f*unit,0);
            for(int layer=3;layer>0;layer--){art.oval(batch,color(accent,.045f*layer*fade),px,py,(35+layer*24+t*90)*unit,(25+layer*19+t*70)*unit,hit.direction()*-20);}
            float expansion=1-(1-t)*(1-t)*(1-t);
            arc(batch,camera,hit.x(),hit.y(),radius*expansion*.92,0,Math.PI*2,accent,3*(1-t),fade*.8f);
            if(hit.style()==FightImpactFeedback.Style.BLOCK){
                arc(batch,camera,hit.x()-hit.direction()*20,hit.y(),65+expansion*35,-1.25,1.25,GOLD,6*(1-t),fade,hit.direction());
                arc(batch,camera,hit.x()-hit.direction()*20,hit.y(),54+expansion*35,-1.25,1.25,WHITE,2,fade,hit.direction());
                for(int i=0;i<5;i++){double a=-1+i*.5;line(batch,camera,WHITE,hit.x()+Math.cos(a)*35*hit.direction(),hit.y()+Math.sin(a)*35,hit.x()+Math.cos(a)*(55+t*50)*hit.direction(),hit.y()+Math.sin(a)*(55+t*50),2,fade);}
            }else if(hit.style()==FightImpactFeedback.Style.LIGHTNING||hit.style()==FightImpactFeedback.Style.ULTIMATE&&hit.kind()==FighterKind.NAIWA){
                for(int branch=0;branch<7;branch++){
                    double a=branch*Math.PI*2/7+.3,pxw=hit.x(),pyw=hit.y();
                    for(int n=1;n<=4;n++){double r=(25+n*radius*.23)*(1+t*.3),nx=hit.x()+Math.cos(a)*r+Math.sin(branch*9+n*7)*12,ny=hit.y()+Math.sin(a)*r;
                        line(batch,camera,accent,pxw,pyw,nx,ny,8*(1-t),fade*.35f);line(batch,camera,WHITE,pxw,pyw,nx,ny,2,fade);pxw=nx;pyw=ny;}
                }
            }else if(hit.style()==FightImpactFeedback.Style.WEB||hit.style()==FightImpactFeedback.Style.ULTIMATE){
                for(int spoke=0;spoke<10;spoke++){
                    double a=spoke*Math.PI/5,b=a+Math.PI/5,r=radius*(.45+expansion*.65);
                    line(batch,camera,WHITE,hit.x(),hit.y(),hit.x()+Math.cos(a)*r,hit.y()+Math.sin(a)*r,1.4f,fade);
                    for(int ring=1;ring<=3;ring++){double rr=r*ring/3;line(batch,camera,accent,hit.x()+Math.cos(a)*rr,hit.y()+Math.sin(a)*rr,hit.x()+Math.cos(b)*rr,hit.y()+Math.sin(b)*rr,3,fade);}
                }
            }else{
                double angle=hit.style()==FightImpactFeedback.Style.CROSS?.38:hit.style()==FightImpactFeedback.Style.UPPERCUT?1.05:-.20;
                for(int ribbon=0;ribbon<3;ribbon++){
                    double r=radius*(.58+ribbon*.15)+expansion*25;
                    arc(batch,camera,hit.x()-hit.direction()*22,hit.y(),r,angle-1.1,angle+1.3,accent,(8-ribbon*2)*(1-t),fade,hit.direction());
                    arc(batch,camera,hit.x()-hit.direction()*22,hit.y(),r-4,angle-1,angle+1,WHITE,2*(1-t),fade,hit.direction());
                }
                for(int ray=0;ray<9;ray++){double a=ray*Math.PI*2/9+.13;double r=(ray%2==0?65:40)*(1+expansion*.8);
                    line(batch,camera,accent,hit.x()+Math.cos(a)*15,hit.y()+Math.sin(a)*15,hit.x()+Math.cos(a)*r,hit.y()+Math.sin(a)*r,7*(1-t),fade);
                    line(batch,camera,WHITE,hit.x()+Math.cos(a)*8,hit.y()+Math.sin(a)*8,hit.x()+Math.cos(a)*r*.9,hit.y()+Math.sin(a)*r*.9,2,fade);}
            }
            // 不同方向的棱形碎片，运动在进度函数内计算，池上限不会随连击增长。
            for(int shard=0;shard<(hit.style()==FightImpactFeedback.Style.BLOCK?5:12);shard++){
                double a=shard*2.399+hit.direction()*.6,r=(24+expansion*(70+shard%4*19)),sx=hit.x()+Math.cos(a)*r,sy=hit.y()+Math.sin(a)*r-t*t*35;
                art.oval(batch,color(shard%3==0?WHITE:accent,fade),camera.x(sx),camera.y(sy),(7+shard%3*3)*(1-t)*unit,3*unit,shard*49+t*140);
            }
            float pop=1+(float)Math.sin(Math.min(1,t*4)*Math.PI)*.22f;
            type.draw(batch,label(hit.style()),px,py+(96+t*30)*unit,(hit.style()==FightImpactFeedback.Style.ULTIMATE?34:25)*unit*pop,250*unit,accent,Math.min(1,fade*2));
        }
    }
    private static String label(FightImpactFeedback.Style style){return switch(style){case JAB->"JAB!";case CROSS->"CROSS!";case HOOK->"HOOK!";case UPPERCUT->"RISE!";case LIGHTNING->"SHOCK!";case WEB->"WEB HIT!";case BLOCK->"BLOCK";case ULTIMATE->"SMASH!";case TELEPORT->"RIFT BREAK!";};}
    private Color color(Color c,float alpha){return tint.set(c.r,c.g,c.b,Math.max(0,Math.min(1,alpha)));}
    private void line(Batch b,FightCamera camera,Color c,double x,double y,double xx,double yy,float width,float alpha){art.line(b,color(c,alpha),camera.x(x),camera.y(y),camera.x(xx),camera.y(yy),Math.max(.5f,width*camera.unit()));}
    private void arc(Batch b,FightCamera c,double x,double y,double r,double from,double to,Color color,float width,float alpha){arc(b,c,x,y,r,from,to,color,width,alpha,1);}
    private void arc(Batch b,FightCamera c,double x,double y,double r,double from,double to,Color color,float width,float alpha,int direction){
        for(int i=0;i<28;i++){double a=from+(to-from)*i/28,beta=from+(to-from)*(i+1)/28;line(b,c,color,x+Math.cos(a)*r*direction,y+Math.sin(a)*r*.7,x+Math.cos(beta)*r*direction,y+Math.sin(beta)*r*.7,width,alpha);}
    }
}
