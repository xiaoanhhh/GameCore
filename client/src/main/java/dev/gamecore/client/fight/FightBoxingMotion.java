package dev.gamecore.client.fight;

import dev.gamecore.fight.FightPunch;
import dev.gamecore.fight.FightSnapshot.Action;

/** 拳击姿态时间表复用规则命中步；只映射图片/躯干发力，不回写规则或用墙钟推进。 */
public final class FightBoxingMotion {
    private FightBoxingMotion() { }
    /** 八姿态中 1/3/5 蓄势、2/4/6 击出，7 收拳，0 复位拳架。 */
    public static int pose(Action action, int tick) {
        FightPunch punch = FightPunch.from(action);
        if (punch == null) { return -1; }
        if (tick < punch.strikeTick()) { return 1 + punch.ordinal() * 2; }
        if (tick <= punch.strikeTick() + 3) { return 2 + punch.ordinal() * 2; }
        return tick < punch.duration() - 3 ? 7 : 0;
    }
    /** 先后蓄再前探，击出后平滑收回；固定脚底锚点只做小幅转腰和视觉重心变化。 */
    public static float thrust(Action action, int tick) {
        FightPunch punch = FightPunch.from(action);
        if (punch == null || tick < 0 || tick >= punch.duration()) { return 0; }
        if (tick < punch.strikeTick()) { return -.18f * tick / punch.strikeTick(); }
        float progress = Math.min(1, (tick - punch.strikeTick()) / (float) (punch.duration() - punch.strikeTick() - 3));
        return 1 - progress * progress * (3 - 2 * progress);
    }
}
