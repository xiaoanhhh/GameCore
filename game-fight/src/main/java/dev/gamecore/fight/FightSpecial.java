package dev.gamecore.fight;

/** 特殊技共享时序/费用。单位为 60Hz 规则步；客户端只读此表，不自行决定束缚或伤害。 */
public final class FightSpecial {
    private FightSpecial() { }
    public static final int CHARGE=36, DURATION=138;
    public static final int BOLT_FIRST=54, BOLT_INTERVAL=18, BOLT_COUNT=4, BOLT_AIM=12, BOLT_DAMAGE=5;
    public static final int WEB_AIM=36, WEB_CAPTURE=66, WEB_BURST=114, WEB_DAMAGE=20;
    public static final int BLINK_COST=24, BLINK_COOLDOWN=180, BLINK_RECOVERY=20, BLINK_DAMAGE=5, BLINK_VISUAL=24;
    public static int boltTick(int index){if(index<0||index>=BOLT_COUNT){throw new IllegalArgumentException("Unknown bolt");}return BOLT_FIRST+index*BOLT_INTERVAL;}
    public enum Phase { CHARGE, STORM, WEAVE, INFLATE, BURST, RECOVER }
    public static Phase phase(FightSnapshot.Ultimate value){
        if(value.age()<CHARGE){return Phase.CHARGE;}
        if(value.kind()==FighterKind.NAIWA){return Phase.STORM;}
        return value.age()<WEB_CAPTURE?Phase.WEAVE:value.age()<WEB_BURST?Phase.INFLATE:value.age()<WEB_BURST+12?Phase.BURST:Phase.RECOVER;
    }
    public static boolean blinkAffordable(FightSnapshot.Fighter value){return value.kind()==FighterKind.FEIDUDU&&value.energy()>=BLINK_COST&&value.teleportCooldown()==0;}
}
