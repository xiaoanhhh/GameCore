package dev.gamecore.server.core;

/** 网络意图的纯Java值；绝对瞄准只转成有限转向输入，不可改位置/速度/生命或瞬改朝向。 */
public record InputIntent(long requestId, String matchId, long sequence, long targetTick,
                          int move, int turn, Double aimDegrees, boolean fire) {
    public boolean valid() {
        return requestId > 0 && matchId != null && matchId.length() <= 80 && sequence > 0 && targetTick > 0
                && move >= -1 && move <= 1 && turn >= -1 && turn <= 1
                && (aimDegrees == null || Double.isFinite(aimDegrees) && aimDegrees >= 0 && aimDegrees < 360 && turn == 0);
    }
}
