package dev.gamecore.client.fight;

import dev.gamecore.fight.FightSnapshot;
import dev.gamecore.fight.FightSpecial;
import dev.gamecore.fight.FighterKind;

/** 特殊技镜头/阶段投影，不另起模拟时钟；开场、胜者和大笑仍由 Board 统一决定优先级。 */
public final class FightSpecialView {
    public record Focus(int player,float zoom) { }
    private static final Focus NONE=new Focus(-1,1),CAST_ONE=new Focus(0,2.35f),CAST_TWO=new Focus(1,2.35f),BLINK_ONE=new Focus(0,1.20f),BLINK_TWO=new Focus(1,1.20f);
    private FightSpecialView() { }
    public static Focus focus(FightSnapshot view){
        FightSnapshot.Ultimate latest=null;
        for(var u:view.ultimates()){if(u.age()<FightSpecial.CHARGE&&(latest==null||u.id()>latest.id())){latest=u;}}
        if(latest!=null){return latest.owner()==0?CAST_ONE:CAST_TWO;}
        FightSnapshot.Teleport blink=null;for(var t:view.teleports()){if(t.age()<18&&(blink==null||t.id()>blink.id())){blink=t;}}
        return blink==null?NONE:blink.owner()==0?BLINK_ONE:BLINK_TWO;
    }
    public static float darkness(FightSnapshot view){float result=0;for(var u:view.ultimates()){if(u.kind()==FighterKind.NAIWA){result=Math.max(result,Math.min(1,Math.min(u.age()/18f,(FightSpecial.DURATION-u.age())/18f)));}}return Math.max(0,result);}
    public static float cocoonScale(FightSnapshot.Ultimate u){return u.captured()?1+Math.max(0,Math.min(1,(u.age()-FightSpecial.WEB_CAPTURE)/(float)(FightSpecial.WEB_BURST-FightSpecial.WEB_CAPTURE)))*.85f:0;}
}
