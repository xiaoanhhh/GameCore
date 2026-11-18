package dev.gamecore.client.fight;

import dev.gamecore.api.session.SessionState;
import dev.gamecore.fight.FightSnapshot;

/** 结算只读演出：识别实际胜者、视觉落稳和功夫舞步；规则终局快照始终不变，失焦可暂停。 */
public final class FightVictory {
    private static final int[] DANCE = {0,1,2,3,1,4,5,6,3,2,6,7,0,7};
    public static final double KO_SECONDS=.55, BEAT_SECONDS=.32;
    private int winner=-1;
    private boolean finished, paused;
    private double elapsed, originalY, floorY;
    public void observe(FightSnapshot view) {
        if (view.state()!=SessionState.FINISHED || finished) { return; }
        finished=true;
        winner=switch(view.result()){case PLAYER_ONE->0;case PLAYER_TWO->1;default->-1;};
        if(winner>=0){var f=view.fighters().get(winner);originalY=f.y();floorY=0;
            for(var p:view.map().platforms()){if(p.contains(f.x())&&p.height()<=f.y()+.01){floorY=Math.max(floorY,p.height());}}
        }
    }
    public void reset(){winner=-1;finished=paused=false;elapsed=originalY=floorY=0;}
    public void pause(){if(finished){paused=true;}}
    public void resume(){paused=false;}
    public boolean paused(){return paused;}
    public boolean finished(){return finished;}
    public int winner(){return winner;}
    public double elapsed(){return elapsed;}
    public void advance(double delta){if(finished&&!paused&&Double.isFinite(delta)&&delta>0){elapsed+=Math.min(.1,delta);}}
    public int focusedPlayer(){return finished&&winner>=0&&elapsed>=KO_SECONDS?winner:-1;}
    /** 两轮舞步后保持举拳终姿，结算页持续展示；不会在两秒后把动画截断。 */
    public int pose(){if(focusedPlayer()<0){return -1;}int beat=(int)((elapsed-KO_SECONDS)/BEAT_SECONDS);return beat<DANCE.length?DANCE[beat]:7;}
    public double danceTime(){return Math.max(0,elapsed-KO_SECONDS);}
    public double xOffset(){return danceTime()<DANCE.length*BEAT_SECONDS?Math.sin(danceTime()*Math.PI/BEAT_SECONDS)*12:0;}
    public double bob(){return danceTime()<DANCE.length*BEAT_SECONDS?Math.abs(Math.sin(danceTime()*Math.PI/BEAT_SECONDS))*7:0;}
    public float angle(){return pose()==6?(float)Math.sin(danceTime()*18)*13:pose()==2?-5:pose()==3?5:0;}
    /** 只有演出角色落到其下方的有效支撑面，绝不移动真实角色或为胜者凭空加平台。 */
    public double feetY(){double p=Math.min(1,elapsed/.6);return originalY+(floorY-originalY)*p;}
}
