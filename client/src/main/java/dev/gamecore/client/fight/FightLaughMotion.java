package dev.gamecore.client.fight;

import dev.gamecore.fight.FightSnapshot;

/** 笑姿按规则 poseTick 编排，暂停/顿帧自然冻结；镜头只聚焦真实 LAUGH，动作取消即平滑回战斗。 */
public final class FightLaughMotion {
    private static final int[] POSES={0,1,2,3,4,5,2,3,4,5,6,1,7,7};
    private FightLaughMotion(){}
    public static int pose(int tick){return POSES[Math.min(POSES.length-1,Math.max(0,tick)/12)];}
    public static float angle(int tick){return (float)Math.sin(tick*.38)*2.5f;}
    public static float squash(int tick){return 1+(float)Math.sin(tick*.75)*.018f;}
    public static int focusedPlayer(FightSnapshot view){
        int player=-1,age=Integer.MAX_VALUE;for(var f:view.fighters()){if(f.action()==FightSnapshot.Action.LAUGH&&f.poseTick()<age){age=f.poseTick();player=f.player();}}return player;
    }
}
