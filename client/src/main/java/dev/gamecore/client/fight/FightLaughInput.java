package dev.gamecore.client.fight;

import com.badlogic.gdx.Input;
import dev.gamecore.fight.FightSnapshot;
import dev.gamecore.fight.FightSessionFactory;
import dev.gamecore.fight.FighterKind;
import java.util.HashSet;
import java.util.Set;

/** 娱乐键独立锁存：唯一人类奶蛙可用任一键；双奶蛙时 Y/P1、9/P2，不操纵敌方 Bot。 */
public final class FightLaughInput {
    private final Set<Integer> held=new HashSet<>();
    private final boolean[] pressed=new boolean[2];
    public static boolean accepts(int key){return key==Input.Keys.Y||key==Input.Keys.NUM_9||key==Input.Keys.NUMPAD_9;}
    public void down(int key,FightSnapshot view,FightSessionFactory.Mode mode){
        if(!accepts(key)||!held.add(key)){return;}int preferred=key==Input.Keys.Y?0:1;
        int player=eligible(view,mode,preferred)?preferred:eligible(view,mode,1-preferred)?1-preferred:-1;
        if(player>=0){pressed[player]=true;}
    }
    private static boolean eligible(FightSnapshot view,FightSessionFactory.Mode mode,int player){
        return (player==0||mode==FightSessionFactory.Mode.DUEL)&&view.fighters().get(player).kind()==FighterKind.NAIWA;
    }
    public boolean consume(int player){if(player<0||player>1){throw new IllegalArgumentException("Unknown fighter");}boolean value=pressed[player];pressed[player]=false;return value;}
    public void up(int key){held.remove(key);}
    public void clear(){held.clear();java.util.Arrays.fill(pressed,false);}
}
