package dev.gamecore.client.fight;

import dev.gamecore.fight.FightSnapshot;

/** 客户端镜头统一变换与有限震屏；所有对象共享同一投影，不写入模拟。按秒平滑，避免刷新率改变手感。 */
public final class FightCamera {
    public static final float VIEW_W = 1920, VIEW_H = 720, FLOOR = 76;
    private float center = 1120, zoom = .86f, trauma, time, lift;
    private float scale, ox, oy, shakeX, shakeY;
    public void update(FightSnapshot view, float delta) {
        update(view, delta, -1);
    }
    /** 开场允许越过战斗缩放上限聚焦真实角色位置；倒计时前平滑返回双人战斗取景。 */
    public void update(FightSnapshot view, float delta, int focus) {
        update(view,delta,focus,focus>=0?view.fighters().get(focus).y():0);
    }
    /** 胜者可能位于高台/空中，聚焦的高度采用演出脚底，避免放大后角色飞出画面。 */
    public void update(FightSnapshot view, float delta, int focus, double focusY) {
        advance(view,delta,focus,focusY,3.6f,false);
    }
    /** 蓄势近景释放后快速还原，首道落雷前能看清敌人；闪袭只小幅拉近新位置的双方。 */
    public void special(FightSnapshot view,float delta,int focus,double focusY,float zoom){advance(view,delta,focus,focusY,zoom,true);}
    private void advance(FightSnapshot view,float delta,int focus,double focusY,float focusZoom,boolean special){
        if (delta <= 0) { return; }
        time += delta; trauma = Math.max(0, trauma - delta * 1.8f);
        float distance = (float) Math.abs(view.fighters().get(1).x() - view.fighters().get(0).x());
        float desired = Math.max(.86f, Math.min(1, 1600 / Math.max(1600, distance + 300)));
        if (focus >= 0) { desired = focusZoom; }
        zoom += (desired - zoom) * (1 - (float) Math.exp(-delta * (special?12:4)));
        float targetLift=focus>=0?(float)focusY:Math.max(0,(float)Math.max(view.fighters().get(0).y(),view.fighters().get(1).y())-465);
        lift += (targetLift - lift) * (1 - (float) Math.exp(-delta * (special?12:6)));
        float target = (float) (view.fighters().get(0).x() + view.fighters().get(1).x()) / 2;
        float half = VIEW_W / zoom / 2;
        target = Math.max(half, Math.min(2240 - half, target));
        if (focus >= 0&&!(special&&focusZoom<1.3f)) { target = (float) view.fighters().get(focus).x() + (special?view.fighters().get(focus).facing()*70:focus == 0 ? 75 : -75); }
        center += (target - center) * (1 - (float) Math.exp(-delta * (special?12:5)));
        shakeX = (float) Math.sin(time * 143) * trauma * trauma * 15;
        shakeY = (float) Math.cos(time * 117) * trauma * trauma * 9;
    }
    public void impact(float amount) { trauma = Math.min(1, trauma + amount); }
    public void reset() { center = 1120; zoom = .86f; trauma = time = lift = shakeX = shakeY = 0; }
    public float zoom() { return zoom; }
    public void viewport(float x, float y, float width, float height) {
        scale = Math.min(width / VIEW_W, height / VIEW_H); ox = x + (width - VIEW_W * scale) / 2; oy = y + (height - VIEW_H * scale) / 2;
    }
    public float x(double world) { return ox + (VIEW_W / 2 + (float) (world - center) * zoom + shakeX) * scale; }
    public float y(double world) { return oy + (FLOOR + ((float) world - lift) * zoom + shakeY) * scale; }
    public float unit() { return scale * zoom; }
    public float center() { return center; }
    public float scale() { return scale; }
    public float ox() { return ox; }
    public float oy() { return oy; }
}
