package dev.gamecore.client.fight;

import dev.gamecore.fight.FightSession;
import dev.gamecore.fight.FightSnapshot;
import dev.gamecore.fight.FighterKind;

/** HUD 只读投影与血条残影时钟；按键文字与实际 P1/P2 控制一致，不能据此触发技能或修改能量。 */
public final class FightHudModel {
    private FightHudModel() { }
    public static String blinkLabel(FightSnapshot.Fighter f){
        return blinkLabel(f,f.player());
    }
    /** keyPlayer 为本机控制方案；-1 的远端角色只显示冷却和消耗。 */
    public static String blinkLabel(FightSnapshot.Fighter f,int keyPlayer){
        if(f.kind()!=FighterKind.FEIDUDU){return "";}String key=keyPlayer<0?"":keyPlayer==0?"S+K":"↓+N2";
        return "  闪袭 "+key+" "+(f.teleportCooldown()>0?(f.teleportCooldown()+59)/60+"s":dev.gamecore.fight.FightSpecial.blinkAffordable(f)?"24EN":"能量不足");
    }
    public enum Skill {
        PUNCH("出拳","J","N1"), RANGED("远程","U","N4"), ULTIMATE("奥义","I","N5"), GUARD("格挡","S","↓");
        private final String title,p1,p2;
        Skill(String title,String p1,String p2){this.title=title;this.p1=p1;this.p2=p2;}
        public String title(){return title;}
        public String key(int player){if(player<0||player>1){throw new IllegalArgumentException("Unknown player");}return player==0?p1:p2;}
        public String label(int keyPlayer){return (keyPlayer<0?"":key(keyPlayer)+" ")+title+(cost()>0?" "+cost():"");}
        public int cost(){return this==RANGED?FightSession.RANGED_COST:this==ULTIMATE?FightSession.ULTIMATE_COST:0;}
        public boolean ready(double energy){return Double.isFinite(energy)&&energy>=(this==GUARD?FightSession.GUARD_COST:cost());}
        public FightIcon icon(FighterKind kind){return switch(this){case PUNCH->FightIcon.FIST;case RANGED->kind==FighterKind.NAIWA?FightIcon.BOLT:FightIcon.WEB;case ULTIMATE->FightIcon.ULTIMATE;case GUARD->FightIcon.SHIELD;};}
        public boolean active(FightSnapshot.Action action){return switch(this){
            case PUNCH->action==FightSnapshot.Action.PUNCH||action==FightSnapshot.Action.PUNCH_2||action==FightSnapshot.Action.PUNCH_3||action==FightSnapshot.Action.AIR_PUNCH||action==FightSnapshot.Action.UPPERCUT;
            case RANGED->action==FightSnapshot.Action.RANGED;case ULTIMATE->action==FightSnapshot.Action.ULTIMATE||action==FightSnapshot.Action.ULT_RELEASE;
            case GUARD->action==FightSnapshot.Action.GUARD||action==FightSnapshot.Action.GUARD_HIT;};}
    }
    /** 新局必须 reset，暂停不推进残影；下降立即显示真实值，红色旧值稍后追上。 */
    public static final class Meter {
        private float value,trail,delay;
        public Meter(float initial){reset(initial);}
        public void reset(float v){value=trail=clamp(v);delay=0;}
        public void target(float v){v=clamp(v);if(v<value){delay=.24f;}value=v;trail=Math.max(trail,v);}
        public void advance(float delta,boolean paused){if(paused||!Float.isFinite(delta)||delta<=0){return;}float dt=Math.min(delta,.1f),held=Math.min(delay,dt);delay-=held;trail=Math.max(value,trail-(dt-held)*.65f);}
        public float value(){return value;}public float trail(){return trail;}public float delay(){return delay;}
        private static float clamp(float v){return Float.isFinite(v)?Math.max(0,Math.min(1,v)):0;}
    }
}
