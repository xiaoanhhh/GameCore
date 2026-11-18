package dev.gamecore.client.fight;

import dev.gamecore.fight.FightSnapshot;
import dev.gamecore.fight.FighterKind;

/** 真实 Impact 的有界视觉副本：只读快照、按 ID 去重。暂停由页面冻结 advance；换局/回退清空，不延后补播。 */
public final class FightImpactFeedback {
    public static final int CAPACITY=24;
    public enum Style { JAB, CROSS, HOOK, UPPERCUT, LIGHTNING, WEB, BLOCK, ULTIMATE, TELEPORT }
    public static final class Hit {
        private Style style;private FighterKind kind;private float age,duration,x,y;private int direction,damage,victim=-1;
        public Style style(){return style;}public FighterKind kind(){return kind;}public float age(){return age;}public float progress(){return Math.min(1,age/duration);}
        public float x(){return x;}public float y(){return y;}public int direction(){return direction;}public int damage(){return damage;}public int victim(){return victim;}
        public boolean alive(){return age<duration;}
    }
    private final Hit[] hits=new Hit[CAPACITY];
    private long lastId,lastTick=-1;private int cursor;private float impulse;
    public FightImpactFeedback(){for(int i=0;i<CAPACITY;i++){hits[i]=new Hit();}}
    public boolean ingest(FightSnapshot view){
        if(view.tick()<lastTick){clear();}lastTick=view.tick();impulse=0;boolean added=false;
        for(var impact:view.impacts()){
            if(impact.id()<=lastId){continue;}lastId=impact.id();added=true;
            Hit hit=hits[cursor++%CAPACITY];hit.style=style(view,impact);hit.kind=impact.kind();hit.x=(float)impact.x();hit.y=(float)impact.y();
            hit.direction=impact.direction();hit.damage=impact.damage();hit.age=0;hit.duration=hit.style==Style.ULTIMATE?.72f:hit.style==Style.BLOCK?.30f:.40f;hit.victim=victim(view,impact);
            impulse=Math.max(impulse,hit.style==Style.BLOCK?.13f:hit.style==Style.ULTIMATE?.64f:hit.style==Style.HOOK||hit.style==Style.UPPERCUT?.37f:.23f);
        }
        return added;
    }
    private static Style style(FightSnapshot view,FightSnapshot.Impact hit){
        if(hit.blocked()){return Style.BLOCK;}if(hit.ultimate()){return Style.ULTIMATE;}
        if(hit.attack()!=FightSnapshot.Attack.UNKNOWN){return switch(hit.attack()){
            case JAB->Style.JAB;case CROSS->Style.CROSS;case HOOK->Style.HOOK;case UPPERCUT->Style.UPPERCUT;case TELEPORT->Style.TELEPORT;
            default->hit.kind()==FighterKind.NAIWA?Style.LIGHTNING:Style.WEB;};}
        // 快照未暴露攻击来源 ID；只做视觉推断，按同身份、同朝向、距命中点最近的实际出招者选拳型。
        FightSnapshot.Fighter source=null;double nearest=Double.POSITIVE_INFINITY;
        for(var f:view.fighters()){if(f.kind()!=hit.kind()||f.facing()!=hit.direction()){continue;}
            double distance=Math.abs(f.x()-hit.x());if(distance<nearest){source=f;nearest=distance;}}
        if(source!=null){return switch(source.action()){
            case PUNCH_2->Style.CROSS;case PUNCH_3->Style.HOOK;case UPPERCUT,AIR_PUNCH->Style.UPPERCUT;
            case PUNCH->Style.JAB;default->hit.kind()==FighterKind.NAIWA?Style.LIGHTNING:Style.WEB;};}
        return hit.kind()==FighterKind.NAIWA?Style.LIGHTNING:Style.WEB;
    }
    private static int victim(FightSnapshot view,FightSnapshot.Impact hit){
        if(hit.defender()>=0){return hit.defender();}
        int player=-1;double nearest=Double.POSITIVE_INFINITY;
        for(var f:view.fighters()){if(f.action()!=FightSnapshot.Action.HURT&&f.action()!=FightSnapshot.Action.HURT_HEAVY&&f.action()!=FightSnapshot.Action.GUARD_HIT){continue;}
            double distance=Math.abs(f.x()-hit.x())+Math.abs(f.y()+50-hit.y());if(distance<nearest){nearest=distance;player=f.player();}}
        return player;
    }
    public void advance(float delta){if(!Float.isFinite(delta)||delta<=0){return;}float dt=Math.min(delta,.1f);for(var h:hits){if(h.alive()){h.age=Math.min(h.duration,h.age+dt);}}}
    public Hit hit(int index){return hits[index];}
    public int count(){int result=0;for(var h:hits){if(h.alive()){result++;}}return result;}
    public float impulse(){return impulse;}
    public float hurtFlash(int player){float result=0;for(var h:hits){if(h.alive()&&h.victim==player&&h.style!=Style.BLOCK){result=Math.max(result,Math.max(0,1-h.age/.12f));}}return result;}
    public void clear(){for(var h:hits){h.age=h.duration=0;}lastId=0;lastTick=-1;cursor=0;impulse=0;}
}
