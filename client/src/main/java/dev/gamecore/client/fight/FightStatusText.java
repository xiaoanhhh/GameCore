package dev.gamecore.client.fight;

import dev.gamecore.api.session.SessionState;
import dev.gamecore.fight.FightSession;
import dev.gamecore.fight.FightSnapshot;

/** 格斗状态栏文案的纯投影：本地与在线共用同一套对阵/提示/胜负描述。
 * 在线只额外说明需双方同意的房间控制；只读快照与房间阶段，不改变比赛。
 */
public final class FightStatusText {
    static final String RUNNING_HINT="长按疾跑 / 连按二段跳 / 下+跳穿台 / 连按出拳三段连击";
    private FightStatusText() { }
    public static String matchup(FightSnapshot view){
        return "P1 "+view.fighters().get(0).kind()+" · "+view.fighters().get(0).kind().ranged()
                +"   VS   P2 "+view.fighters().get(1).kind()+" · "+view.fighters().get(1).kind().ranged();
    }
    public static String result(FightSnapshot view){
        return switch(view.result()){
            case PLAYER_ONE -> "P1 "+view.fighters().get(0).kind()+" 获胜";
            case PLAYER_TWO -> "P2 "+view.fighters().get(1).kind()+" 获胜";
            case DRAW -> "本局平局"; case NONE -> "";
        }+(view.tick()>=FightSession.MATCH_TICKS?" · 时间到":" · K.O.");
    }
    public static String local(FightSnapshot view){
        return switch(view.state()){
            case READY -> "选择角色，开始对决  /  "+matchup(view);
            case RUNNING -> RUNNING_HINT;
            case PAUSED -> "Ⅱ 已暂停  ·  P / Esc 或点击继续";
            case FINISHED -> result(view);
        };
    }
    /** 房间阶段优先于快照：开场/暂停属于房间控制；重开请求、复位和中断交给连接提示说明。 */
    public static String online(String phase,boolean paused,boolean ownSkip,boolean ownResume,boolean restartRequested,FightSnapshot view,String notice){
        if(restartRequested){return notice;}
        if(paused||phase.equals("PAUSED")){
            if(ownResume){return "Ⅱ 已暂停 · 等待对手继续";}
            return switch(phase){
                case "OPENING" -> "Ⅱ 开场已暂停 · P / Esc 继续（需双方同意）";
                case "FINISHED" -> "Ⅱ 庆祝已暂停 · P / Esc 继续 · 可再来一局";
                default -> "Ⅱ 已暂停  ·  P / Esc 或点击继续（需双方同意）";
            };
        }
        return switch(phase){
            case "WAITING","SETUP" -> notice+"  /  "+matchup(view);
            case "OPENING" -> ownSkip?"已请求跳过开场 · 等待对手确认":"登场演出  ·  Enter / Space 跳过开场（需双方同意）";
            case "RUNNING" -> RUNNING_HINT;
            case "FINISHED" -> view.state()==SessionState.FINISHED?result(view):notice;
            default -> notice;
        };
    }
}
