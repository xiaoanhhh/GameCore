package dev.gamecore.client.fight;

import dev.gamecore.fight.FighterKind;
import dev.gamecore.fight.FightSnapshot;
import java.util.SplittableRandom;

/** 仅客户端的开场时间轴；会话保持 READY，完成/跳过后才由页面调用 start，暂停不走时间。 */
public final class FightOpening {
    public enum Phase { IDLE, FIRST, SECOND, VERSUS, THREE, TWO, ONE, FIGHT, DONE }
    public static final double GREETING_SECONDS = 1.8, VERSUS_SECONDS = .5, FIGHT_SECONDS = .55;
    public static final double TOTAL_SECONDS = GREETING_SECONDS * 2 + VERSUS_SECONDS + 3 + FIGHT_SECONDS;
    private final SplittableRandom random;
    private final int[] styles = {-1, -1};
    private final int[] order = {0, 1};
    private double time;
    private boolean paused;
    private Phase phase = Phase.IDLE;
    public FightOpening(long seed) { random = new SplittableRandom(seed); }
    /** 奶蛙优先，换边仍按身份聚焦；同角色对战则两个玩家依次登场，绝不替换选角。 */
    public void start(FightSnapshot view) {
        int first = view.fighters().get(1).kind() == FighterKind.NAIWA && view.fighters().get(0).kind() != FighterKind.NAIWA ? 1 : 0;
        order[0] = first; order[1] = 1 - first;
        for (int p = 0; p < 2; p++) { styles[p] = styles[p] < 0 ? random.nextInt(4) : (styles[p] + 1 + random.nextInt(3)) % 4; }
        time = 0; paused = false; phase = Phase.FIRST;
    }
    public void reset() { time = 0; paused = false; phase = Phase.IDLE; }
    public boolean active() { return phase != Phase.IDLE && phase != Phase.DONE; }
    public boolean paused() { return paused; }
    public void pause() { if (active()) { paused = true; } }
    public void resume() { paused = false; }
    public Phase phase() { return phase; }
    public double elapsed() { return time; }
    public int style(int player) { return styles[player]; }
    public int focusedPlayer() { return phase == Phase.FIRST ? order[0] : phase == Phase.SECOND ? order[1] : -1; }
    public int number() { return phase == Phase.THREE ? 3 : phase == Phase.TWO ? 2 : phase == Phase.ONE ? 1 : 0; }
    public double phaseTime() {
        return switch (phase) {
            case FIRST -> time; case SECOND -> time - GREETING_SECONDS; case VERSUS -> time - GREETING_SECONDS * 2;
            case THREE -> time - 4.1; case TWO -> time - 5.1; case ONE -> time - 6.1; case FIGHT -> time - 7.1; default -> 0;
        };
    }
    /** 限制单帧视觉跨度，卡顿不瞬间掠过整段演出；完成当帧不补跑战斗 Tick。 */
    public boolean advance(double delta) {
        if (!active() || paused || !Double.isFinite(delta) || delta <= 0) { return false; }
        time = Math.min(TOTAL_SECONDS, time + Math.min(delta, .1));
        double t = time + 1e-7;
        phase = t < 1.8 ? Phase.FIRST : t < 3.6 ? Phase.SECOND : t < 4.1 ? Phase.VERSUS
                : t < 5.1 ? Phase.THREE : t < 6.1 ? Phase.TWO : t < 7.1 ? Phase.ONE : t < TOTAL_SECONDS ? Phase.FIGHT : Phase.DONE;
        return phase == Phase.DONE;
    }
    public boolean skip() { if (!active() || paused) { return false; } time = TOTAL_SECONDS; phase = Phase.DONE; return true; }
    /** 在线只读房间演出时钟；本地仍使用 advance，不据此推进任何规则。 */
    public void synchronize(double elapsed,boolean frozen){
        if(!Double.isFinite(elapsed)){return;}time=Math.max(0,Math.min(TOTAL_SECONDS,elapsed));paused=frozen;
        double t=time+1e-7;phase=t<1.8?Phase.FIRST:t<3.6?Phase.SECOND:t<4.1?Phase.VERSUS:t<5.1?Phase.THREE:t<6.1?Phase.TWO:t<7.1?Phase.ONE:t<TOTAL_SECONDS?Phase.FIGHT:Phase.DONE;
    }
    /** 四种双姿态问候：挥手、鞠躬、点赞、敬礼；只让当前聚焦的人做动作。 */
    public int greetingPose(int player) {
        if (focusedPlayer() != player) { return -1; }
        double t = phaseTime();
        return switch (styles[player]) {
            case 0 -> (int) (t / .18) % 2;
            case 1 -> t > .3 && t < 1.3 ? 3 : 2;
            case 2 -> 4 + (int) (t / .42) % 2;
            case 3 -> t < 1.15 ? 6 : 7;
            default -> throw new IllegalStateException("Unknown greeting");
        };
    }
}
