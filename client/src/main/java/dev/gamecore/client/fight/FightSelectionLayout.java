package dev.gamecore.client.fight;

/** 统一 1280×720 设计坐标；绘制与真实按钮使用同一投影，超宽/较高窗口不拉伸角色或错位热区。 */
public record FightSelectionLayout(float left,float bottom,float scale) {
    public static FightSelectionLayout fit(float width,float height){
        if(!Float.isFinite(width)||!Float.isFinite(height)||width<=0||height<=0){throw new IllegalArgumentException("Invalid selection viewport");}
        float scale=Math.min(width/1280,height/720);return new FightSelectionLayout((width-1280*scale)/2,(height-720*scale)/2,scale);
    }
    public float x(float value){return left+value*scale;}
    public float y(float value){return bottom+value*scale;}
    public float size(float value){return value*scale;}
    public record Card(float x,float y,float width,float height){}
    /** 地图卡片在固定预览栏内均分，新增地图不能把最后几张挤出屏幕。 */
    public static Card mapCard(int index,int count){if(count<1||index<0||index>=count){throw new IllegalArgumentException("Invalid map card");}
        float width=(858-(count-1)*12)/count;return new Card(32+index*(width+12),118,width,108);}
}
