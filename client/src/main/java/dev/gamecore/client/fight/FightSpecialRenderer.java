package dev.gamecore.client.fight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.fight.FightSnapshot;
import dev.gamecore.fight.FightSpecial;
import dev.gamecore.fight.FighterKind;

/** 九天雷引/八方蛛狱/闪袭的分层演出。几何与年龄来自快照，全部借用窗口画笔，暂停无墙钟随机推进。 */
public final class FightSpecialRenderer {
    private static final Color NIGHT=Color.valueOf("020310"),CLOUD=Color.valueOf("11162f"),CYAN=Color.valueOf("59eaff"),BLUE=Color.valueOf("3879ff"),
            PURPLE=Color.valueOf("c681ff"),GREEN=Color.valueOf("c4ffae"),WHITE=Color.valueOf("f4ffff"),SILK=Color.valueOf("cbd9ed"),GOLD=Color.valueOf("ffd18b");
    private final ThemeManager theme;private final FightArt art;private final FightTypography type;private final Color tint=new Color();
    public FightSpecialRenderer(ThemeManager theme,FightArt art){this.theme=theme;this.art=art;type=new FightTypography(theme);}
    /** 遮暗建筑与天幕，人物仍在此层之后绘制，避免整屏滤镜把轮廓一同淹没。 */
    public void background(Batch batch,FightSnapshot view,FightCamera camera,float x,float y,float w,float h){
        float dark=FightSpecialView.darkness(view);if(dark>0){
            batch.setColor(0,.01f,.05f,dark*.70f);batch.draw(theme.assets.pixel,x,y,w,h);
            for(int i=0;i<18;i++){
                float pulse=0;for(var u:view.ultimates()){if(u.kind()==FighterKind.NAIWA){pulse+=u.age()*.17f;}}
                art.oval(batch,color(i%3==0?BLUE:CLOUD,dark*(i%3==0?.12f:.55f)),x+w*(i/17f),y+h*(.95f-.07f*(float)Math.sin(i*3+pulse*.08f)),w*.30f,h*.25f,0);
            }
            for(int i=0;i<6;i++){batch.setColor(.03f,.05f,.15f,dark*(.10f-i*.014f));batch.draw(theme.assets.pixel,x,y+h*(.7f+i*.05f),w,h*.05f);}
        }
        for(var u:view.ultimates()){
            if(u.age()<FightSpecial.CHARGE){
                float progress=u.age()/(float)FightSpecial.CHARGE;Color c=u.kind()==FighterKind.NAIWA?CYAN:PURPLE;
                ring(batch,camera,c,u.casterX(),u.casterY()+4,48+progress*40,.28,3,.9f);
                for(int i=0;i<12;i++){double angle=i*Math.PI/6-u.age()*.11,r=140*(1-progress)+22;
                    double px=u.casterX()+Math.cos(angle)*r,py=u.casterY()+60+Math.sin(angle)*r;
                    line(batch,camera,c,px,py,u.casterX()+Math.cos(angle)*r*.78,u.casterY()+60+Math.sin(angle)*r*.78,4,.7f);
                }
                art.oval(batch,color(WHITE,.18f+progress*.35f),camera.x(u.casterX()),camera.y(u.casterY()+78),27*camera.unit(),27*camera.unit(),0);
            }
            for(var strike:u.strikes()){if(strike.aimed()&&strike.age()<0&&strike.age()>=-FightSpecial.BOLT_AIM){
                ring(batch,camera,CYAN,strike.x(),strike.y()+2,90,.24,2,.75f);ring(batch,camera,WHITE,strike.x(),strike.y()+2,90*(-strike.age()/(float)FightSpecial.BOLT_AIM),.24,1,.65f);
                line(batch,camera,CYAN,strike.x()-15,strike.y()+5,strike.x()+15,strike.y()+5,3,.8f);
            }}
            if(u.kind()==FighterKind.FEIDUDU&&u.captured()&&u.age()<FightSpecial.WEB_BURST){
                float scale=FightSpecialView.cocoonScale(u);double center=u.targetY()+74*scale;art.oval(batch,color(NIGHT,.82f),camera.x(u.targetX()),camera.y(center),100*scale*camera.unit(),148*scale*camera.unit(),0);
                for(int i=3;i>0;i--){art.oval(batch,color(PURPLE,.045f*i),camera.x(u.targetX()),camera.y(center),(120+i*28)*scale*camera.unit(),(148+i*25)*scale*camera.unit(),0);}
            }
        }
    }
    public void front(Batch batch,FightSnapshot view,FightCamera camera){
        for(var u:view.ultimates()){
            if(u.kind()==FighterKind.NAIWA){lightning(batch,camera,u);}else{web(batch,camera,u);}
        }
        for(var t:view.teleports()){float fade=1-t.age()/(float)FightSpecial.BLINK_VISUAL;
            for(int i=0;i<3;i++){ring(batch,camera,i==1?WHITE:PURPLE,t.fromX(),t.fromY()+50,28+i*8+t.age(),1.9,3-i,fade*.8f);ring(batch,camera,i==1?GREEN:PURPLE,t.toX(),t.toY()+50,34+i*9+t.age()*.5,1.7,3-i,fade);}
            for(int i=0;i<14;i++){double a=i*2.399,r=25+t.age()*3;line(batch,camera,i%3==0?WHITE:PURPLE,t.toX()+Math.cos(a)*r,t.toY()+50+Math.sin(a)*r,t.toX()+Math.cos(a)*(r+12),t.toY()+50+Math.sin(a)*(r+12),2,fade);}
            if(t.age()<5){for(int i=0;i<12;i++){double p=i/12.0;line(batch,camera,PURPLE,t.fromX()+(t.toX()-t.fromX())*p,t.fromY()+50+(t.toY()-t.fromY())*p,t.fromX()+(t.toX()-t.fromX())*(p+.03),t.fromY()+50+(t.toY()-t.fromY())*(p+.03),2,fade*.25f);}}
        }
    }
    private void lightning(Batch batch,FightCamera camera,FightSnapshot.Ultimate u){
        int direction=u.targetX()>=u.casterX()?1:-1;
        for(var strike:u.strikes()){
            if(!strike.aimed()||strike.age()<0||strike.age()>13){continue;}
            float fade=1-strike.age()/14f;double tx=strike.x(),ty=strike.y()+48,fromX=tx-direction*(360+strike.index()*35),fromY=Math.max(760,ty+600);
            double px=fromX,py=fromY;
            for(int i=1;i<=14;i++){
                double p=i/14.0,nx=fromX+(tx-fromX)*p+(i==14?0:Math.sin(u.id()*9+strike.index()*21+i*17)*32),ny=fromY+(ty-fromY)*p;
                line(batch,camera,BLUE,px,py,nx,ny,28,fade*.18f);line(batch,camera,CYAN,px,py,nx,ny,10,fade*.85f);line(batch,camera,WHITE,px,py,nx,ny,3.2f,fade);
                if(i%3==0&&i<13){double bx=nx-direction*(70+i*4),by=ny-65;line(batch,camera,CYAN,nx,ny,bx,by,5,fade*.55f);line(batch,camera,WHITE,bx,by,bx+direction*20,by-45,1.7f,fade);}
                px=nx;py=ny;
            }
            ring(batch,camera,CYAN,tx,strike.y(),35+strike.age()*12,.22,4,fade);
            for(int i=0;i<8;i++){double a=i*Math.PI/4,r=30+strike.age()*9;line(batch,camera,CYAN,tx+Math.cos(a)*15,strike.y()+3,tx+Math.cos(a)*r,strike.y()+3+Math.sin(a)*r*.25,2,fade);}
            art.oval(batch,color(WHITE,fade*.7f),camera.x(tx),camera.y(ty),40*camera.unit(),65*camera.unit(),-direction*25);
        }
    }
    private void web(Batch batch,FightCamera camera,FightSnapshot.Ultimate u){
        if(u.age()<FightSpecial.WEB_AIM){return;}
        float scale=FightSpecialView.cocoonScale(u);double cx=u.targetX(),cy=u.targetY()+(u.captured()?74*scale:55);
        if(u.age()<FightSpecial.WEB_BURST&&!u.missed()){
            float progress=Math.min(1,(u.age()-FightSpecial.WEB_AIM)/(float)(FightSpecial.WEB_CAPTURE-FightSpecial.WEB_AIM));
            for(int strand=0;strand<8;strand++){
                double a=strand*Math.PI/4+.18,sx=cx+Math.cos(a)*680,sy=cy+Math.sin(a)*430;
                double ex=u.captured()?cx+Math.cos(a)*48*scale:sx+(cx-sx)*progress,ey=u.captured()?cy+Math.sin(a)*72*scale:sy+(cy-sy)*progress;
                thread(batch,camera,sx,sy,ex,ey,strand,u.age(),1);
            }
        }
        if(u.captured()&&u.age()<FightSpecial.WEB_BURST){
            double rx=49*scale,ry=74*scale;float pulse=.8f+.15f*(float)Math.sin(u.age()*.5);
            float inflation=Math.max(0,Math.min(1,(scale-1)/.85f));
            art.oval(batch,color(SILK,.18f+inflation*.38f),camera.x(cx),camera.y(cy),(float)(rx*2*camera.unit()),(float)(ry*2*camera.unit()),0);
            ring(batch,camera,PURPLE,cx,cy,rx,ry/rx,5,pulse);ring(batch,camera,WHITE,cx,cy,rx-3,ry/rx,1.8f,.9f);
            for(int band=0;band<19;band++){
                double yy=-.9+band*.10,half=rx*Math.sqrt(Math.max(0,1-yy*yy)),previousX=cx-half,previousY=cy+yy*ry;
                for(int i=1;i<=20;i++){double p=i/20.0,nx=cx-half+half*2*p,ny=cy+yy*ry+Math.sin(p*Math.PI)*12*scale;
                    line(batch,camera,band%2==0?WHITE:GREEN,previousX,previousY,nx,ny,2,.82f);previousX=nx;previousY=ny;}
            }
            for(int i=0;i<22;i++){double a=i*Math.PI/11+u.age()*.015,b=a+Math.PI*.8;
                line(batch,camera,i%3==0?WHITE:PURPLE,cx+Math.cos(a)*rx,cy+Math.sin(a)*ry,cx+Math.cos(b)*rx,cy+Math.sin(b)*ry,1.2f,.7f);}
            art.oval(batch,color(WHITE,.08f),camera.x(cx-rx*.32),camera.y(cy+ry*.12),(float)(rx*.3*camera.unit()),(float)(ry*1.2*camera.unit()),-12);
        }
        if(u.age()>=FightSpecial.WEB_BURST&&u.captured()&&u.age()<FightSpecial.WEB_BURST+18){
            float t=(u.age()-FightSpecial.WEB_BURST)/18f,fade=1-t;
            ring(batch,camera,PURPLE,cx,cy,90+t*330,.75,10*fade,fade);ring(batch,camera,GREEN,cx,cy,75+t*240,.8,3,fade);
            for(int i=0;i<32;i++){double a=i*2.399,r=80+t*(220+i%4*35),px=cx+Math.cos(a)*r,py=cy+Math.sin(a)*r*.85;
                line(batch,camera,i%3==0?WHITE:PURPLE,px,py,px+Math.cos(a)*35*fade,py+Math.sin(a)*35*fade,3,fade);
                if(i%2==0){line(batch,camera,GREEN,px,py,px+Math.sin(a)*22,py+Math.cos(a)*22,1,fade*.7f);}
            }
            art.oval(batch,color(WHITE,fade*fade*.6f),camera.x(cx),camera.y(cy),(100+t*90)*camera.unit(),(100+t*50)*camera.unit(),0);
        }
        if(u.missed()&&u.age()<FightSpecial.WEB_CAPTURE+18){
            float fade=1-(u.age()-FightSpecial.WEB_CAPTURE)/18f;ring(batch,camera,PURPLE,cx,cy,50+(1-fade)*100,.8,2,fade*.55f);
        }
    }
    /** 流动珠光和弯曲丝束连续注入茧表面，端点始终对应实际锁定/束缚位置。 */
    private void thread(Batch batch,FightCamera camera,double sx,double sy,double ex,double ey,int seed,int age,float alpha){
        double px=sx,py=sy;
        for(int i=1;i<=24;i++){double p=i/24.0,envelope=Math.sin(p*Math.PI),nx=sx+(ex-sx)*p+Math.sin(seed*2+p*7+age*.1)*16*envelope,ny=sy+(ey-sy)*p+Math.cos(seed+p*8+age*.11)*24*envelope;
            line(batch,camera,PURPLE,px,py,nx,ny,6,alpha*.25f);line(batch,camera,WHITE,px,py,nx,ny,1.5f,alpha*.72f);px=nx;py=ny;}
        for(int bead=0;bead<3;bead++){double p=(age*.028+bead/3.0+seed*.09)%1;
            art.oval(batch,color(GREEN,.85f*alpha),camera.x(sx+(ex-sx)*p),camera.y(sy+(ey-sy)*p),7*camera.unit(),7*camera.unit(),0);}
    }
    public void overlay(Batch batch,FightSnapshot view,FightCamera camera,float x,float y,float w,float h){
        FightSnapshot.Ultimate chosen=null;for(var u:view.ultimates()){if(chosen==null||u.id()>chosen.id()){chosen=u;}}
        if(chosen!=null){var phase=FightSpecial.phase(chosen);Color c=chosen.kind()==FighterKind.NAIWA?CYAN:PURPLE;
            String caption=chosen.missed()?"蛛丝散去 / DISPERSED":phase==FightSpecial.Phase.CHARGE?"奥义觉醒 / AWAKENING":phase==FightSpecial.Phase.STORM?"九天雷落 / THUNDERSTORM":phase==FightSpecial.Phase.WEAVE?"八方聚丝 / CONVERGE":phase==FightSpecial.Phase.INFLATE?"蛛茧膨胀 / OVERLOAD":"蛛狱爆裂 / RUPTURE";
            float s=camera.scale();batch.setColor(.01f,.02f,.07f,.78f);batch.draw(theme.assets.pixel,x+w*.24f,y+h*.83f,w*.52f,h*.15f);
            type.draw(batch,chosen.kind().ultimate(),x+w*.5f,y+h*.95f,40*s,w*.65f,c,1);
            type.draw(batch,caption,x+w*.5f,y+h*.87f,15*s,w*.63f,c,1);
            int target=1-chosen.owner();float screen=camera.x(view.fighters().get(target).x());
            if(screen<x+25||screen>x+w-25){type.draw(batch,screen<x?"← 敌方":"敌方 →",screen<x?x+60:x+w-60,y+h*.48f,18*s,w*.18f,GOLD,1);}
        }
        for(var t:view.teleports()){if(t.age()<18){type.draw(batch,"时空闪袭",x+w*.5f,y+h*.9f,29*camera.scale(),w*.6f,PURPLE,1-t.age()/24f);}}
    }
    private Color color(Color c,float alpha){return tint.set(c.r,c.g,c.b,Math.max(0,Math.min(1,alpha)));}
    private void line(Batch b,FightCamera camera,Color c,double x,double y,double xx,double yy,float width,float alpha){art.line(b,color(c,alpha),camera.x(x),camera.y(y),camera.x(xx),camera.y(yy),Math.max(.6f,width*camera.unit()));}
    private void ring(Batch b,FightCamera camera,Color c,double x,double y,double radius,double ratio,float width,float alpha){for(int i=0;i<40;i++){double a=i*Math.PI/20,next=(i+1)*Math.PI/20;line(b,camera,c,x+Math.cos(a)*radius,y+Math.sin(a)*radius*ratio,x+Math.cos(next)*radius,y+Math.sin(next)*radius*ratio,width,alpha);}}
}
