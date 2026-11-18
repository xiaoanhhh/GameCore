package dev.gamecore.client.fight;

/** 六格材质图的实测区域，内缩三像素避免线性采样串色；不增加几何或碰撞面。 */
public enum FightSceneryMaterial {
    NEON(0,0),TILE(1,0),STEEL(2,0),BAMBOO(0,1),MOSS(1,1),FROST(2,1);
    public static final String ASSET="scenery/fight-materials-v1.png";
    public static final int WIDTH=1536,HEIGHT=1024,SIZE=506;
    public final int x,y;
    FightSceneryMaterial(int col,int row){x=col*512+3;y=row*512+3;}
}
