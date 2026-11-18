package dev.gamecore.client.fight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.fight.FightMap;
import dev.gamecore.fight.FighterKind;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 两页出战台：原角色大图/元素预演与地图实景/平台情报。所有卡片是真实 Button，图像借用窗口缓存。 */
public final class FightSelectionPanel extends WidgetGroup {
    private static final Color INK=Color.valueOf("050917"),PANEL=Color.valueOf("0b152a"),CYAN=Color.valueOf("59e8ff"),
            PURPLE=Color.valueOf("c48bff"),GOLD=Color.valueOf("ffce7b"),WHITE=Color.valueOf("f0faff"),MUTED=Color.valueOf("91a8c8");
    private final ThemeManager theme;
    private final FightSelection selection;
    private final FightArt art;
    private final FightTypography type;
    private final FightIconPainter icons;
    private final FightSceneryRenderer scenery;
    private final FightCamera previewCamera=new FightCamera();
    private final Color tint=new Color();
    private final TextureRegion crop=new TextureRegion();
    private final List<Choice> choices=new ArrayList<>();
    private FightSelectionLayout frame=FightSelectionLayout.fit(1280,720);
    private float time,pulse;
    private boolean paused;
    private final boolean bot;
    private int onlineSlot=-1;

    public FightSelectionPanel(ThemeManager theme,FightSelection selection,Consumer<Button> register,Runnable next,Runnable back){
        this.theme=theme;this.selection=selection;bot=selection.committed().mode()==dev.gamecore.fight.FightSessionFactory.Mode.BOT;
        art=new FightArt(theme);type=new FightTypography(theme);icons=new FightIconPainter(theme,FightIcon.MAP);scenery=new FightSceneryRenderer(theme,art);setName("fight-selection");
        add(new Choice("select-step-fighters",()->"01  选择斗士",()->selection.page()==FightSelection.Page.FIGHTERS,()->{selection.fighters();sync();},null,null,-1,820,654,196,40,0),register);
        add(new Choice("select-step-maps",()->"02  选择战场",()->selection.page()==FightSelection.Page.MAPS,()->{selection.maps();sync();},null,null,-1,1032,654,216,40,0),register);
        for(int p=0;p<2;p++){final int player=p;
            for(var kind:FighterKind.values()){
                add(new Choice("select-fighter-"+p+"-"+kind.name().toLowerCase(java.util.Locale.ROOT),kind::toString,()->selection.fighter(player)==kind,
                        ()->{selection.choose(player,kind);pulse=1;},kind,null,p,48+p*664+kind.ordinal()*274,124,258,82,1),register);
            }
        }
        for(var map:FightMap.values()){
            var card=FightSelectionLayout.mapCard(map.ordinal(),FightMap.values().length);
            add(new Choice("select-map-"+map.name().toLowerCase(java.util.Locale.ROOT),map::toString,()->selection.map()==map,
                    ()->{selection.choose(map);pulse=1;},null,map,-1,card.x(),card.y(),card.width(),card.height(),2),register);
        }
        add(new Choice("select-back",()->selection.page()==FightSelection.Page.MAPS?"← 返回选择斗士":"← 返回",()->false,back,null,null,-1,32,28,224,50,0),register);
        add(new Choice("select-next",()->selection.page()==FightSelection.Page.MAPS?onlineSlot<0?"出战  /  FIGHT →":"确认并准备 →":"下一步 · 选择战场 →",()->true,next,null,null,-1,906,28,342,50,0),register);
        sync();
    }
    private void add(Choice choice,Consumer<Button> register){choices.add(choice);addActor(choice);register.accept(choice);}
    /** 页面切换同时隐藏无关热区；避免 Tab 落入上一页卡片或隐藏的战斗按钮。 */
    public void sync(){for(var c:choices){c.setVisible(c.page==0||c.page==1&&selection.page()==FightSelection.Page.FIGHTERS||c.page==2&&selection.page()==FightSelection.Page.MAPS);c.setDisabled(onlineSlot>=0&&(c.kind!=null&&c.player!=onlineSlot||c.map!=null&&onlineSlot!=0));}invalidate();}
    public void online(int slot){onlineSlot=slot;sync();}
    public void pausePreview(){paused=true;}
    public void resumePreview(){paused=false;}
    public double previewTime(){return time;}
    @Override public void act(float delta){super.act(delta);if(isVisible()&&!paused&&Float.isFinite(delta)&&delta>0){time+=Math.min(.1f,delta);pulse=Math.max(0,pulse-delta*2);}}
    @Override public void layout(){frame=FightSelectionLayout.fit(getWidth(),getHeight());for(var c:choices){c.setBounds(frame.x(c.dx),frame.y(c.dy),frame.size(c.dw),frame.size(c.dh));}}
    @Override public void draw(Batch batch,float parentAlpha){
        validate();float packed=batch.getPackedColor();float alpha=parentAlpha*getColor().a;
        fill(batch,INK,getX(),getY(),getWidth(),getHeight(),alpha);
        var stage=theme.assets.fightStage(selection.map());if(stage!=null){batch.setColor(1,1,1,.21f*alpha);batch.draw(stage,getX(),getY(),getWidth(),getHeight());}
        for(int i=0;i<30;i++){float px=(i*163+time*(10+i%4*5))%1280,py=(i*97+time*9)%720;
            line(batch,i%2==0?CYAN:PURPLE,px,py,px+24,py+10,1,.12f);}
        for(int i=0;i<10;i++){line(batch,CYAN,0,160+i*16,1280,50+i*30,1,.025f);}
        label(batch,selection.page()==FightSelection.Page.FIGHTERS?"选择你的斗士":"选择你的战场",278,684,36,488,CYAN);
        label(batch,selection.page()==FightSelection.Page.FIGHTERS?"FIGHTER SELECT  /  ULTIMATE ARENA":"STAGE SELECT  /  ULTIMATE ARENA",260,642,12,440,MUTED);
        line(batch,CYAN,32,622,1248,622,1,.6f);
        if(selection.page()==FightSelection.Page.FIGHTERS){fighters(batch);}else{maps(batch);}
        line(batch,CYAN,32,94,1248,94,1,.35f);
        label(batch,paused?"Ⅱ 预览已暂停 · 点击或按键继续选择":"鼠标选择  ·  Tab 切换焦点  ·  Enter 确认  ·  Esc 返回",580,55,12,600,MUTED);
        batch.setPackedColor(packed);super.draw(batch,alpha);batch.setPackedColor(packed);
    }
    private void fighters(Batch batch){
        for(int p=0;p<2;p++){
            float left=32+p*664,cx=left+276;Color accent=p==0?CYAN:PURPLE;var kind=selection.fighter(p);
            rect(batch,PANEL,left,222,552,390,.74f);line(batch,accent,left,610,left+552,610,3,.8f);
            label(batch,p==0?"01 / PLAYER ONE":bot?"02 / CPU RIVAL":"02 / PLAYER TWO",cx,590,14,440,accent);
            label(batch,kind.toString(),cx,552,38,400,accent);
            for(int i=0;i<3;i++){ring(batch,accent,cx,362,114+i*21+3*(float)Math.sin(time*2+i),1,.10f+i*.025f);}
            // 原角色大图在展示台轻浮动；元素预演与真实战斗输入完全独立。
            art.oval(batch,shade(accent,.15f),x(cx),y(281),frame.size(280),frame.size(32),0);
            art.greeting(batch,kind,x(cx),y(284+3*(float)Math.sin(time*2)),frame.size(kind==FighterKind.NAIWA?.78f:.69f),p==0?1:-1,(int)(time/1.2f)%2);
            element(batch,kind,cx,p==0?1:-1,accent);
            label(batch,kind==FighterKind.NAIWA?"雷霆出击 · 闪电波":"蛛丝掌控 · 蜘蛛丝",cx,262,18,490,accent);
            label(batch,"近战出拳  /  "+kind.ranged()+"  /  "+kind.ultimate(),cx,232,13,500,MUTED);
            label(batch,onlineSlot>=0?p==onlineSlot?"你的角色 · A / D 选择 · 可选相同角色":"对手正在选择 · 实时同步":p==0?"A / D 选择 P1  ·  可选相同角色":bot?"← / → 选择电脑角色":"← / → 选择 P2  ·  可选相同角色",cx,110,11,520,MUTED);
        }
        line(batch,GOLD,640,230,640,602,1,.3f);label(batch,"VS",640,442,62,120,GOLD);
        label(batch,onlineSlot>=0?"ONLINE":bot?"HUMAN":"LOCAL",640,360,11,100,MUTED);label(batch,bot?"vs CPU":"DUEL",640,341,11,100,MUTED);
    }
    private void element(Batch batch,FighterKind kind,float cx,int facing,Color accent){
        float bx=cx+facing*122,by=370;
        if(kind==FighterKind.NAIWA){for(int n=0;n<3;n++){for(int i=0;i<6;i++){
            float x1=bx+facing*i*13,y1=by+(float)Math.sin(i*2.9+time*14+n)*14;
            float x2=bx+facing*(i+1)*13,y2=by+(float)Math.sin((i+1)*2.9+time*14+n)*14;
            line(batch,accent,x1,y1,x2,y2,n==0?4:1,n==0?.38f:.65f);
        }}}else{for(int i=0;i<10;i++){double a=i*Math.PI/5+time*.12;line(batch,accent,bx,by,bx+(float)Math.cos(a)*54,by+(float)Math.sin(a)*54,1,.42f);}
            for(int i=1;i<4;i++){ring(batch,accent,bx,by,16*i,1,.42f);}}
        if(pulse>0){ring(batch,Color.WHITE,cx,392,100+(1-pulse)*100,2,pulse*.4f);}
    }
    private void maps(Batch batch){
        var map=selection.map();var style=FightMapStyle.of(map);float left=32,bottom=252,w=858,h=354;
        image(batch,theme.assets.fightStage(map),left,bottom,w,h,.96f);
        previewCamera.viewport(x(left),y(bottom+68),frame.size(w),frame.size(h-68));scenery.draw(batch,map,previewCamera,time,.98f);
        rect(batch,INK,left,bottom,w,66,.74f);line(batch,style.primary,left,bottom+h,left+w,bottom+h,3,.9f);
        label(batch,map.toString(),left+230,bottom+54,30,420,style.primary);
        label(batch,map.feature(),left+250,bottom+21,14,454,style.secondary);
        rect(batch,INK,912,118,336,488,.88f);line(batch,style.primary,912,606,1248,606,3,.9f);
        label(batch,"0"+(map.ordinal()+1)+" / ARENA INTEL",1080,580,14,300,style.primary);
        label(batch,map.toString(),1080,536,29,300,style.primary);
        label(batch,map.feature(),1080,496,17,300,style.secondary);
        label(batch,"场景落脚点 / STAGE ROUTE",1080,452,12,300,MUTED);
        rect(batch,PANEL,936,319,288,112,.9f);
        line(batch,style.secondary,950,334,1210,334,2,.7f);
        for(var platform:map.platforms()){
            float x1=950+(float)platform.left()/2240*260,x2=950+(float)platform.right()/2240*260,py=334+(float)platform.height()/440*80;
            line(batch,style.primary,x1,py,x2,py,3,1);
        }
        label(batch,FightSceneryLayout.levels(map),1080,304,15,280,style.primary);
        label(batch,"本场对阵",1080,270,13,280,MUTED);
        art.greeting(batch,selection.fighter(0),x(995),y(164),frame.size(.36f),1,4);
        art.greeting(batch,selection.fighter(1),x(1160),y(164),frame.size(.36f),-1,4);
        label(batch,"VS",1080,215,22,64,GOLD);
        label(batch,selection.fighter(0)+"  /  "+selection.fighter(1),1080,147,14,300,WHITE);
        label(batch,onlineSlot>0?"地图由房主选择 · 实时同步":"← / → 或 Q / E 切换战场",456,244,10,810,MUTED);
    }
    private float x(float v){return getX()+frame.x(v);}
    private float y(float v){return getY()+frame.y(v);}
    private Color shade(Color c,float alpha){return tint.set(c.r,c.g,c.b,alpha);}
    private void fill(Batch b,Color c,float x,float y,float w,float h,float alpha){b.setColor(c.r,c.g,c.b,alpha);b.draw(theme.assets.pixel,x,y,w,h);}
    private void rect(Batch b,Color c,float x,float y,float w,float h,float alpha){fill(b,c,x(x),y(y),frame.size(w),frame.size(h),alpha);}
    private void line(Batch b,Color c,float x1,float y1,float x2,float y2,float width,float alpha){art.line(b,shade(c,alpha),x(x1),y(y1),x(x2),y(y2),frame.size(width));}
    private void label(Batch b,String text,float cx,float baseline,float height,float width,Color c){type.draw(b,text,x(cx),y(baseline),frame.size(height),frame.size(width),c,1);}
    private void ring(Batch b,Color c,float cx,float cy,float radius,float width,float alpha){for(int i=0;i<42;i++){
        double a=i*Math.PI/21,d=(i+1)*Math.PI/21;
        line(b,c,cx+(float)Math.cos(a)*radius,cy+(float)Math.sin(a)*radius,cx+(float)Math.cos(d)*radius,cy+(float)Math.sin(d)*radius,width,alpha);
    }}
    /** 按中心裁切预览，借用 region 对象，不修改共享纹理或生成额外图片。 */
    private void image(Batch b,Texture texture,float left,float bottom,float width,float height,float alpha){
        if(texture==null){rect(b,PANEL,left,bottom,width,height,1);return;}
        float target=width/height,source=texture.getWidth()/(float)texture.getHeight();
        int cw=source>target?Math.round(texture.getHeight()*target):texture.getWidth(),ch=source>target?texture.getHeight():Math.round(texture.getWidth()/target);
        crop.setTexture(texture);crop.setRegion((texture.getWidth()-cw)/2,(texture.getHeight()-ch)/2,cw,ch);b.setColor(1,1,1,alpha);b.draw(crop,x(left),y(bottom),frame.size(width),frame.size(height));
    }
    /** 自绘真实按钮：高亮同时包含边框、选中文字和焦点括号，不只依赖颜色。 */
    private final class Choice extends Button {
        private final Supplier<String> text;
        private final BooleanSupplier selected;
        private final FighterKind kind;
        private final FightMap map;
        private final int player,page;
        private final float dx,dy,dw,dh;
        Choice(String name,Supplier<String> text,BooleanSupplier selected,Runnable action,FighterKind kind,FightMap map,int player,float x,float y,float w,float h,int page){
            super(new ButtonStyle());setName(name);this.text=text;this.selected=selected;this.kind=kind;this.map=map;this.player=player;dx=x;dy=y;dw=w;dh=h;this.page=page;
            addListener(new ChangeListener(){@Override public void changed(ChangeEvent event,Actor actor){if(isDisabled()){event.cancel();return;}paused=false;action.run();}});
        }
        @Override public void draw(Batch b,float parentAlpha){
            boolean active=selected.getAsBoolean(),focus=getStage()!=null&&getStage().getKeyboardFocus()==this;
            Color accent=map!=null?FightMapStyle.of(map).primary:player==1?PURPLE:CYAN;float packed=b.getPackedColor();
            rect(b,PANEL,dx,dy,dw,dh,.94f);
            if(map!=null){image(b,theme.assets.fightStage(map),dx+1,dy+1,dw-2,dh-2,active?.9f:.47f);rect(b,INK,dx+1,dy+1,dw-2,36,.8f);}
            if(kind!=null){art.greeting(b,kind,x(dx+45),y(dy+6),frame.size(.28f),1,4);}
            if(active||isOver()||focus){rect(b,accent,dx+1,dy+1,dw-2,dh-2,isPressed()?.24f:.09f);}
            line(b,accent,dx,dy+dh,dx+dw,dy+dh,active?3:1,active?1:.36f);
            line(b,accent,dx,dy,dx+dw,dy,active?2:1,active?.8f:.25f);
            if(active){line(b,accent,dx,dy,dx,dy+dh,3,1);line(b,accent,dx+dw,dy,dx+dw,dy+dh,3,1);}
            float center=dx+dw/2+(kind!=null?28:0),baseline=dy+(map!=null?27:dh*.62f);
            label(b,text.get(),center,baseline,map!=null?19:kind!=null?22:18,dw-(kind!=null?106:20),active?accent:WHITE);
            if(kind!=null){label(b,active?"已选定 / SELECTED":kind.ranged(),center,dy+19,10,dw-110,active?accent:MUTED);}
            if(map!=null){label(b,active?"SELECTED":"0"+(map.ordinal()+1),dx+dw-41,dy+dh-13,10,68,active?accent:WHITE);}
            if(map!=null){icons.draw(b,FightIcon.MAP,x(dx+9),y(dy+dh-25),frame.size(15),accent,1);}
            if(kind!=null){icons.draw(b,kind==FighterKind.NAIWA?FightIcon.BOLT:FightIcon.WEB,x(dx+dw-25),y(dy+12),frame.size(14),accent,1);}
            if(focus){line(b,WHITE,dx+6,dy+dh-6,dx+22,dy+dh-6,2,1);line(b,WHITE,dx+6,dy+dh-6,dx+6,dy+dh-20,2,1);line(b,WHITE,dx+dw-6,dy+6,dx+dw-22,dy+6,2,1);}
            b.setPackedColor(packed);
        }
    }
}
