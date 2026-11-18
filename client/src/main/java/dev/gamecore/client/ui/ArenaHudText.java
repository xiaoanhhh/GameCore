package dev.gamecore.client.ui;

import dev.gamecore.snake.SnakeSnapshot;
import dev.gamecore.snake.ScoreSystem;
import dev.gamecore.tank.TankSnapshot;
import dev.gamecore.tank.TankGame;

/** 本地和在线对同一规则快照使用相同数值文案；不读取隐藏会话状态或写回战绩。 */
public final class ArenaHudText {
    private ArenaHudText() { }
    public static String snakeScores(SnakeSnapshot view,boolean multiline){
        String text="P1  "+view.players().getFirst().score();
        return view.players().size()==1?text:text+(multiline?"\n":"    ")+"P2  "+view.players().get(1).score();
    }
    public static String snakeSpeed(SnakeSnapshot view){return "速度 "+ScoreSystem.movementRate(view.players())+" 格/秒  ·  每 3 颗加速，最高 20";}
    public static String tankHealth(TankSnapshot view){return "P1  "+view.tanks().get(0).health()+" / 5     P2  "+view.tanks().get(1).health()+" / 5";}
    /** Tank 地图中部浮层文字；进行中返回 null（不遮挡战场）。只依据快照状态/结果，不读隐藏会话。 */
    public record TankBanner(String title,String detail){}
    public static TankBanner tankBanner(TankSnapshot view,boolean bot){
        return switch(view.state()){
            case READY->new TankBanner("准备出击","按 P / Esc 开始游戏");
            case PAUSED->new TankBanner("已暂停","按 P / Esc 继续");
            case RUNNING->null;
            case FINISHED->new TankBanner(switch(view.result()){
                case PLAYER_ONE->"P1 获胜";case PLAYER_TWO->bot?"电脑获胜":"P2 获胜";case DRAW,NONE->"本局平局";
            },(view.tick()>=TankGame.MATCH_TICKS?"时间到":"对局结束")+" · 按 P / Esc 再来一局");
        };
    }
    public static String tankTime(TankSnapshot view){long seconds=Math.max(0,(TankGame.MATCH_TICKS-view.tick()+59)/60);return String.format(java.util.Locale.ROOT,"剩余 %02d:%02d",seconds/60,seconds%60);}
}
