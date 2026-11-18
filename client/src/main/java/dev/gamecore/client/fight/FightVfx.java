package dev.gamecore.client.fight;

import dev.gamecore.fight.FightSnapshot;
import dev.gamecore.fight.FighterKind;

/** 有界粒子池：命中星芒/火花/环/数字、移动尘和二段跳共用固定数组；视觉时钟可在顿帧中继续。 */
public final class FightVfx {
    public static final int CAPACITY = 512;
    public static final int SPARK = 0, RING = 1, NUMBER = 2, DUST = 3, BURST = 4;
    public final float[] x = new float[CAPACITY], y = new float[CAPACITY], vx = new float[CAPACITY], vy = new float[CAPACITY],
            life = new float[CAPACITY], duration = new float[CAPACITY];
    public final int[] type = new int[CAPACITY], element = new int[CAPACITY], value = new int[CAPACITY];
    private int cursor;
    private long lastImpact;
    private final int[] previousJump = new int[2];
    private final boolean[] airborne = new boolean[2];
    private long lastTick;
    public boolean ingest(FightSnapshot snapshot) {
        boolean hit = false;
        for (var impact : snapshot.impacts()) {
            if (impact.id() <= lastImpact) { continue; }
            lastImpact = impact.id(); hit = true;
            int color = impact.blocked() ? 2 : impact.kind() == FighterKind.NAIWA ? 0 : 1;
            float px = (float) impact.x(), py = (float) impact.y();
            for (int i = 0; i < (impact.blocked()?8:impact.ultimate()?36:24); i++) {
                double angle = i * 2.39996 + impact.id(); float speed = 250 + i % 7 * 70;
                emit(SPARK, color, px, py, (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed, .24f + i % 5 * .04f, 0);
            }
            emit(RING, color, px, py, 0, 0, .42f, 0); emit(BURST, color, px, py, impact.direction(), 0, .22f, 0);
            // 数字向受击方向错开，不同连拳不叠在招式文字上；伤害值仍取真实 Impact。
            emit(NUMBER, color, px+impact.direction()*(52+impact.id()%3*20), py+26,
                    impact.direction()*(35+impact.id()%3*18), 65, .55f, impact.damage());
        }
        if (snapshot.tick() != lastTick) {
            for (var f : snapshot.fighters()) {
                int p = f.player();
                int elemental=f.kind()==FighterKind.NAIWA?0:1;
                if (f.jumpCount() > previousJump[p]) { emit(RING, elemental, (float) f.x(), (float) f.y() + 10, 0, 0, .4f, 1); }
                if (airborne[p] && f.grounded()) { emit(RING, elemental, (float) f.x(), (float) f.y() + 5, 0, 0, .35f, 2); }
                if (f.grounded() && Math.abs(f.vx()) > 3 && snapshot.tick() % 4 == 0) {
                    for (int i = 0; i < 3; i++) { emit(DUST, elemental, (float) f.x() - f.facing() * (25 + i * 10), (float) f.y() + 5, -f.facing() * (20 + i * 20), 20 + i * 13, .35f, 0); }
                }
                previousJump[p] = f.jumpCount(); airborne[p] = !f.grounded();
            }
            lastTick = snapshot.tick();
        }
        return hit;
    }
    private void emit(int kind, int color, float px, float py, float dx, float dy, float ttl, int number) {
        int i = cursor++ % CAPACITY; x[i] = px; y[i] = py; vx[i] = dx; vy[i] = dy;
        duration[i] = life[i] = ttl; type[i] = kind; element[i] = color; value[i] = number;
    }
    public void update(float delta) {
        float dt = Math.min(Math.max(delta, 0), .1f);
        for (int i = 0; i < CAPACITY; i++) { if (life[i] > 0) {
            life[i] = Math.max(0, life[i] - dt); x[i] += vx[i] * dt; y[i] += vy[i] * dt;
            if (type[i] == SPARK) { vy[i] -= 460 * dt; }
        } }
    }
    public int count() { int n = 0; for (float ttl : life) { if (ttl > 0) { n++; } } return n; }
    public void clear() { java.util.Arrays.fill(life, 0); lastImpact = lastTick = 0; java.util.Arrays.fill(previousJump, 0); java.util.Arrays.fill(airborne, false); }
}
