package dev.gamecore.client.fight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import dev.gamecore.client.ui.ThemeManager;
import dev.gamecore.fight.FightMap;
import java.util.EnumMap;

/** 同一规则顶沿绘制五种建筑材质：屋顶/石柱/连廊拥有体积和细节，立面只作背景，不增加墙体碰撞。 */
public final class FightSceneryRenderer {
    private record Palette(Color wall,Color face,Color cap,Color light,Color dark){}
    private static final EnumMap<FightMap,Palette> COLORS=new EnumMap<>(FightMap.class);
    static{
        COLORS.put(FightMap.NEON,palette("102137","23435e","4b7085","77e1ed","071321"));
        COLORS.put(FightMap.TEMPLE,palette("60382b","94684b","3c6264","c6d8be","192e32"));
        COLORS.put(FightMap.FORGE,palette("30282b","655048","8e6b58","f2b36e","151820"));
        COLORS.put(FightMap.BAMBOO,palette("384d42","597065","929b66","c1df8c","172e26"));
        COLORS.put(FightMap.FROST,palette("3c526e","6c879f","9bb8cc","e0f4ff","17283d"));
    }
    private static Palette palette(String a,String b,String c,String d,String e){return new Palette(Color.valueOf(a),Color.valueOf(b),Color.valueOf(c),Color.valueOf(d),Color.valueOf(e));}
    private static final Palette STONE=palette("535f5b","87958b","b0bdad","dbe5cc","253632");
    private final ThemeManager theme;
    private final FightArt art;
    private final float[] vertices=new float[20];
    private final Color tint=new Color();
    private final TextureRegion[] materials=new TextureRegion[6];
    private FightCamera camera;
    private Batch batch;
    private float alpha,time;
    public FightSceneryRenderer(ThemeManager theme,FightArt art){this.theme=theme;this.art=art;
        var texture=theme.assets.fightSceneryMaterials();if(texture!=null&&texture.getWidth()==FightSceneryMaterial.WIDTH&&texture.getHeight()==FightSceneryMaterial.HEIGHT){
            for(var m:FightSceneryMaterial.values()){materials[m.ordinal()]=new TextureRegion(texture,m.x,m.y,FightSceneryMaterial.SIZE,FightSceneryMaterial.SIZE);}}}
    public boolean materialsLoaded(){return materials[0]!=null;}

    /** 主场景与选图预览共用绘制器，投影由调用者提供；不拥有 Texture/Font，不产生新图集。 */
    public void draw(Batch batch,FightMap map,FightCamera camera,float time,float alpha){
        float packed=batch.getPackedColor();this.batch=batch;this.camera=camera;this.alpha=alpha;this.time=time;var c=COLORS.get(map);
        // 柱身先画，低层建筑再遮挡交叠部分，避免柱子穿出屋面并盖住角色。
        for(var prop:FightSceneryLayout.props(map)){if(prop.shape()==FightSceneryLayout.Shape.COLUMN||prop.shape()==FightSceneryLayout.Shape.BRIDGE&&(map==FightMap.BAMBOO||map==FightMap.FROST)){columns(map,prop,c);}}
        for(var prop:FightSceneryLayout.props(map)){
            if(prop.shape()==FightSceneryLayout.Shape.ROOF){roof(map,prop,c);}
            else if(prop.shape()==FightSceneryLayout.Shape.BRIDGE){bridge(map,prop,c);}
        }
        for(var prop:FightSceneryLayout.props(map)){cap(map,prop,c);}
        batch.setPackedColor(packed);
    }
    private void roof(FightMap map,FightSceneryLayout.Prop p,Palette c){
        double l=p.left(),r=p.right(),t=p.top(),b=p.bottom(),w=r-l;
        if(map==FightMap.BAMBOO){
            quad(c.wall,l+18,b,l+32,t-18,r-26,t-18,r-12,b,1);
            quad(c.face,l+10,b,l+36,t-21,l+w*.6,t-18,l+w*.48,b+8,1);
            quad(c.cap,l+w*.48,b+8,l+w*.6,t-18,r-26,t-18,r-12,b,.65f);
            materialRect(FightSceneryMaterial.MOSS,l+45,b+10,w-90,t-b-40,.83f,.9f);
            for(int i=0;i<5;i++){double x=l+40+i*(w-80)/5;line(c.dark,x,t-22,x+12,b+16,2,.6f);}
            for(double x=l+8;x<r-8;x+=23){oval(c.light,x,t-7,20,5,.48f);}
            return;
        }
        if(map==FightMap.TEMPLE){
            rect(c.dark,l+28,b+8,w-56,t-b-35,.8f);
            for(double x=l+45;x<r-30;x+=Math.max(65,w/5)){
                rect(c.wall,x,b+6,16,t-b-36,1);rect(c.face,x+2,b+6,3,t-b-36,.8f);
                rect(c.cap,x-6,b,28,9,1);rect(c.face,x-5,t-38,26,10,1);
            }
            for(double x=l+70;x<r-60;x+=56){rect(c.face,x,b+24,28,32,.7f);for(int k=0;k<3;k++){line(c.dark,x+6+k*8,b+25,x+6+k*8,b+55,2,1);}}
            line(c.face,l+22,t-37,r-22,t-37,9,1);line(c.light,l+22,t-36,r-22,t-36,1,.65f);
            return;
        }
        rect(c.wall,l+12,b,w-24,t-b-17,1);
        if(map==FightMap.NEON){materialRect(FightSceneryMaterial.NEON,l+12,b,w-24,t-b-17,.86f,1);}
        else if(map==FightMap.FORGE){materialRect(FightSceneryMaterial.STEEL,l+12,b,w-24,t-b-17,.86f,1);}
        else if(map==FightMap.FROST){materialRect(FightSceneryMaterial.FROST,l+12,b,w-24,t-b-17,.73f,1);}
        quad(c.face,l+12,b,l+12,t-18,l+35,t-25,l+35,b,.65f);
        quad(c.dark,r-38,b,r-38,t-25,r-12,t-18,r-12,b,.9f);
        for(double y=b+16;y<t-23;y+=22){line(c.face,l+20,y,r-20,y,1,.45f);}
        if(map==FightMap.NEON){
            if(!materialsLoaded()){for(double y=b+22;y<t-38;y+=27){for(double x=l+48;x<r-30;x+=36){
                rect(c.dark,x-3,y-3,23,19,1);rect(c.light,x,y,8,12,.20f+(float)((int)x%3)*.12f);rect(c.cap,x+11,y,5,12,.55f);
            }}}line(c.light,l+22,b+10,l+22,t-20,2,.7f);line(c.light,r-22,b+10,r-22,t-20,2,.7f);
        }else if(map==FightMap.FORGE){
            for(double x=l+40;x<r-28;x+=62){
                rect(c.dark,x,b+18,34,Math.max(22,t-b-57),1);rect(c.light,x+6,b+22,7,Math.max(16,t-b-65),.55f+.12f*(float)Math.sin(time*2+x));
                for(double y=b+30;y<t-40;y+=15){line(c.wall,x,y,x+32,y,3,1);}
            }
            for(double x=l+28;x<r-18;x+=45){oval(c.cap,x,b+8,5,5,1);}
        }else{
            for(double y=b+23;y<t-24;y+=25){for(double x=l+25+(int)y%2*20;x<r-20;x+=50){line(c.dark,x,y,x,y+23,2,.6f);}}
            line(c.dark,l+w*.67,t-20,l+w*.59,t-54,3,.8f);line(c.dark,l+w*.59,t-54,l+w*.65,b+20,2,.8f);
        }
    }
    private void bridge(FightMap map,FightSceneryLayout.Prop p,Palette c){
        double l=p.left(),r=p.right(),t=p.top(),w=r-l;
        if(map==FightMap.BAMBOO||map==FightMap.TEMPLE){
            rect(c.wall,l+4,t-20,w-8,13,1);
            materialRect(FightSceneryMaterial.BAMBOO,l+5,t-20,w-10,13,.8f,.8f);
            for(double x=l+9;x<r-5;x+=15){line(c.face,x,t-20,x+2,t-7,7,1);}
            for(int i=0;i<16;i++){
                double x1=l+w*i/16,x2=l+w*(i+1)/16,y1=t-27-Math.sin(i*Math.PI/16)*16,y2=t-27-Math.sin((i+1)*Math.PI/16)*16;
                line(c.wall,x1,y1,x2,y2,map==FightMap.BAMBOO?5:8,1);
                line(c.light,x1,y1+2,x2,y2+2,1,.65f);
                if(i%3==0){line(c.face,x1,t-18,x1,y1,3,1);}
            }
            if(map==FightMap.BAMBOO){for(double x=l+25;x<r;x+=55){line(c.light,x,t-22,x+7,t-33,2,.7f);}}
        }else if(map==FightMap.FROST){
            materialRect(FightSceneryMaterial.FROST,l,t-22,w,15,.8f,1);
            for(double x=l;x<r-1;x+=34){double width=Math.min(33,r-x);rect(c.face,x,t-20,width,13,1);
                double depth=18+(Math.floorMod((int)x,4))*5;quad(c.wall,x,t-20,x+width,t-20,x+width-3,t-20-depth,x+5,t-20-depth*.65,1);}
            for(int i=0;i<12;i++){double x=l+w*i/12;line(c.cap,x,t-29,x+w/12,t-29-Math.sin(i*Math.PI/12)*15,3,.65f);}
        }else{
            rect(c.wall,l,t-43,w,31,1);line(c.face,l,t-43,r,t-43,5,1);
            materialRect(map==FightMap.NEON?FightSceneryMaterial.NEON:FightSceneryMaterial.STEEL,l+3,t-40,w-6,23,.68f,.8f);
            for(double x=l+10;x<r-10;x+=65){double end=Math.min(x+60,r-8);
                if(map==FightMap.NEON){rect(c.cap,x,t-38,end-x,21,.28f);}
                line(c.face,x,t-41,end,t-14,4,1);line(c.dark,end,t-41,x,t-14,3,.8f);
                oval(c.light,x,t-13,4,4,.7f);
            }
            for(double x=l+20;x<r;x+=90){line(c.cap,x,t-44,x,t-54,2,.8f);}
        }
    }
    private void columns(FightMap map,FightSceneryLayout.Prop p,Palette c){
        if(map==FightMap.TEMPLE){c=STONE;}
        double l=p.left(),r=p.right(),t=p.top(),b=p.bottom(),w=r-l;
        for(int i=0;i<2;i++){
            double x=i==0?l+44:r-44,width=map==FightMap.FORGE?45:map==FightMap.BAMBOO?18:32;
            rect(c.dark,x-width/2+5,b,width,t-b-18,.9f);rect(c.wall,x-width/2,b,width*.76,t-b-18,1);rect(c.face,x-width/2+3,b+7,width*.25,t-b-32,.8f);
            materialRect(map==FightMap.NEON?FightSceneryMaterial.NEON:map==FightMap.TEMPLE?FightSceneryMaterial.MOSS:map==FightMap.FORGE?FightSceneryMaterial.STEEL:map==FightMap.BAMBOO?FightSceneryMaterial.BAMBOO:FightSceneryMaterial.FROST,x-width/2+2,b+10,width-4,t-b-40,.8f,.88f);
            rect(c.face,x-width/2-7,b,width+14,11,1);rect(c.cap,x-width/2-10,t-26,width+20,12,1);
            if(map==FightMap.FORGE){for(double y=b+21;y<t-30;y+=22){rect(c.cap,x-width/2-3,y,width+6,5,1);line(c.light,x-width/2,y+5,x+width/2,y+5,2,.55f);}}
            else if(map==FightMap.BAMBOO){for(double y=b+16;y<t-28;y+=25){rect(c.light,x-width/2-2,y,width+4,3,.8f);}}
            else{for(int k=0;k<3;k++){line(c.dark,x-width/2+7+k*7,b+14,x-width/2+7+k*7,t-29,1,.65f);}}
            if(map==FightMap.NEON){line(c.light,x,b+13,x,t-28,3,.65f);}
            if(map==FightMap.FROST){for(double y=b+32;y<t-30;y+=33){line(c.cap,x-width/2,y,x+width/2,y,1,.5f);}}
        }
        if(map==FightMap.TEMPLE||map==FightMap.FROST){
            for(int i=0;i<14;i++){double x1=l+44+(w-88)*i/14,x2=l+44+(w-88)*(i+1)/14;
                double y1=t-28-Math.sin(i*Math.PI/14)*30,y2=t-28-Math.sin((i+1)*Math.PI/14)*30;line(c.face,x1,y1,x2,y2,9,1);}
        }else if(map==FightMap.FORGE){line(c.face,l+44,b+25,r-44,t-30,5,.85f);line(c.dark,r-44,b+25,l+44,t-30,4,.8f);}
    }
    private void cap(FightMap map,FightSceneryLayout.Prop p,Palette c){
        double l=p.left(),r=p.right(),t=p.top(),w=r-l;
        if(map==FightMap.TEMPLE){
            quad(c.dark,l,t-4,r,t-4,r-24,t-31,l+24,t-31,1);
            quad(c.cap,l,t,r,t,r-16,t-20,l+16,t-20,1);
            materialRect(FightSceneryMaterial.TILE,l+16,t-18,w-32,15,.93f,1);
            for(double x=l+12;x<r-8;x+=17){line(c.light,x,t-2,x+7,t-18,1,.48f);}
            for(int i=1;i<4;i++){double inset=i*5;line(c.light,l+inset,t-i*6,r-inset,t-i*6,1,.32f);}
            line(c.light,l,t,r,t,2,.8f);
        }else if(map==FightMap.BAMBOO){
            rect(c.wall,l,t-10,w,10,1);for(double x=l+4;x<r;x+=11){line(c.cap,x,t-9,x,t-1,6,1);}
            materialRect(FightSceneryMaterial.BAMBOO,l+3,t-9,w-6,7,.9f,.7f);
            line(c.light,l,t,r,t,2,.8f);for(double x=l+28;x<r;x+=68){line(c.dark,x,t-10,x,t,3,.9f);}
        }else{
            rect(c.dark,l,t-16,w,16,1);quad(c.cap,l,t,r,t,r-9,t-10,l+9,t-10,1);
            materialRect(map==FightMap.NEON?FightSceneryMaterial.NEON:map==FightMap.FORGE?FightSceneryMaterial.STEEL:FightSceneryMaterial.FROST,l+9,t-9,w-18,7,.9f,.8f);
            line(c.light,l,t,r,t,map==FightMap.FROST?4:2,.9f);
            for(double x=l+14;x<r-10;x+=30){line(c.face,x,t-13,x+10,t-13,2,.8f);}
            if(map==FightMap.FROST){for(double x=l+13;x<r-8;x+=29){double length=9+Math.floorMod((int)x,19);quad(c.light,x,t-14,x+7,t-14,x+3,t-14-length,x+3,t-14-length,.72f);}}
            if(map==FightMap.FORGE){for(double x=l+20;x<r-10;x+=47){oval(c.light,x,t-7,4,4,.8f);}}
        }
    }
    private void rect(Color c,double x,double y,double w,double h,float opacity){if(w<=0||h<=0){return;}batch.setColor(c.r,c.g,c.b,opacity*alpha);batch.draw(theme.assets.pixel,camera.x(x),camera.y(y),(float)w*camera.unit(),(float)h*camera.unit());}
    /** 128 世界单位一块，裁 UV 保持纹理比例，不把一整张材质拉成长条；复用顶点数组。 */
    private void materialRect(FightSceneryMaterial material,double x,double y,double w,double h,float light,float opacity){
        var r=materials[material.ordinal()];if(r==null||w<=0||h<=0){return;}float color=Color.toFloatBits(light,light,light,opacity*alpha);
        for(double px=x;px<x+w;px+=128){for(double py=y;py<y+h;py+=128){double tw=Math.min(128,x+w-px),th=Math.min(128,y+h-py);
            float u=r.getU(),v=r.getV2(),u2=u+(r.getU2()-u)*(float)(tw/128),v2=v-(v-r.getV())*(float)(th/128);
            vertex(0,px,py,color,u,v);vertex(5,px,py+th,color,u,v2);vertex(10,px+tw,py+th,color,u2,v2);vertex(15,px+tw,py,color,u2,v);batch.draw(r.getTexture(),vertices,0,20);
        }}
    }
    private void line(Color c,double x1,double y1,double x2,double y2,float width,float opacity){art.line(batch,tint.set(c.r,c.g,c.b,opacity*alpha),camera.x(x1),camera.y(y1),camera.x(x2),camera.y(y2),width*camera.unit());}
    private void oval(Color c,double x,double y,float w,float h,float opacity){art.oval(batch,tint.set(c.r,c.g,c.b,opacity*alpha),camera.x(x),camera.y(y),w*camera.unit(),h*camera.unit(),0);}
    private void quad(Color c,double x1,double y1,double x2,double y2,double x3,double y3,double x4,double y4,float opacity){
        float packed=Color.toFloatBits(c.r,c.g,c.b,opacity*alpha);vertex(0,x1,y1,packed,0,0);vertex(5,x2,y2,packed,1,0);vertex(10,x3,y3,packed,1,1);vertex(15,x4,y4,packed,0,1);
        batch.draw(theme.assets.pixel,vertices,0,20);
    }
    private void vertex(int index,double x,double y,float color,float u,float v){vertices[index]=camera.x(x);vertices[index+1]=camera.y(y);vertices[index+2]=color;vertices[index+3]=u;vertices[index+4]=v;}
}
