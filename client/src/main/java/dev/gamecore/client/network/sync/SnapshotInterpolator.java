package dev.gamecore.client.network.sync;

import dev.gamecore.tank.TankGame;
import dev.gamecore.tank.TankSnapshot;
import dev.gamecore.tank.Vec2;
import java.util.ArrayList;
import java.util.TreeMap;

/** 渲染线程的有界Tick时间轴；不假定等距到达，不外推，缺帧时保持端点，血量/结果仍用最新权威值。 */
public final class SnapshotInterpolator {
    public static final int CAPACITY = 64;
    private record Sample(TankSnapshot snapshot, long arrival) { }
    private final TreeMap<Long, Sample> samples = new TreeMap<>();
    private int bufferMillis = 100, tps;
    private boolean enabled = true;
    private double cursor = Double.NaN;
    private long received, lastArrival, firstArrival, intervals, outOfOrder;
    private double intervalMillis;
    public void enabled(boolean value) { enabled = value; cursor = Double.NaN; }
    public boolean enabled() { return enabled; }
    public void bufferMillis(int value) { if (value < 0 || value > 500) { throw new IllegalArgumentException("Interpolation buffer 0..500ms"); } bufferMillis = value; }
    public int bufferMillis() { return bufferMillis; }
    public void offer(long tick, int serverTps, TankSnapshot snapshot, long arrival) {
        tps = serverTps;
        if (received++ == 0) { firstArrival = arrival; }
        else if (arrival >= lastArrival) { intervalMillis = (arrival - lastArrival) / 1_000_000.0; intervals++; }
        lastArrival = arrival;
        if (!samples.isEmpty() && tick < samples.lastKey()) { outOfOrder++; }
        samples.put(tick, new Sample(snapshot, arrival));
        while (samples.size() > CAPACITY) { samples.pollFirstEntry(); }
    }
    public TankSnapshot display(TankSnapshot authority, int localSlot, TankSnapshot.Tank predicted, long now) {
        if (authority == null) { return null; }
        var tanks = new ArrayList<>(authority.tanks());
        if (predicted != null) { tanks.set(localSlot, predicted); }
        var bullets = authority.bullets();
        if (enabled && !samples.isEmpty() && authority.state() == TankGame.State.RUNNING) {
            var latest = samples.lastEntry();
            double target = latest.getKey() - bufferMillis * tps / 1000.0 + Math.max(0, now - latest.getValue().arrival()) * tps / 1_000_000_000.0;
            target = Math.max(samples.firstKey(), Math.min(latest.getKey(), target));
            cursor = Double.isNaN(cursor) ? target : Math.min(latest.getKey(), Math.max(cursor, target));
            var lower = samples.floorEntry((long) Math.floor(cursor)); var upper = samples.ceilingEntry((long) Math.ceil(cursor));
            if (lower == null) { lower = samples.firstEntry(); } if (upper == null) { upper = samples.lastEntry(); }
            double alpha = upper.getKey().equals(lower.getKey()) ? 0 : (cursor - lower.getKey()) / (upper.getKey() - lower.getKey());
            int remote = 1 - localSlot; var a = lower.getValue().snapshot().tanks().get(remote); var b = upper.getValue().snapshot().tanks().get(remote);
            var metadata = authority.tanks().get(remote);
            tanks.set(remote, new TankSnapshot.Tank(remote, lerp(a.position(), b.position(), alpha),
                    TankGame.normalize(a.heading() + ClientPrediction.shortAngle(b.heading() - a.heading()) * alpha),
                    lerp(a.velocity(), b.velocity(), alpha), metadata.health(), metadata.cooldown()));
            var byId = new java.util.HashMap<Long, TankSnapshot.Bullet>();
            for (var bullet : upper.getValue().snapshot().bullets()) { byId.put(bullet.id(), bullet); }
            var views = new ArrayList<TankSnapshot.Bullet>();
            for (var bullet : lower.getValue().snapshot().bullets()) {
                var next = byId.get(bullet.id());
                views.add(next == null ? bullet : new TankSnapshot.Bullet(bullet.id(), bullet.owner(), lerp(bullet.position(), next.position(), alpha),
                        lerp(bullet.velocity(), next.velocity(), alpha), bullet.remainingTicks()));
            }
            bullets = views;
        }
        return new TankSnapshot(authority.map(), authority.tick(), authority.state(), authority.result(), tanks, bullets, authority.events());
    }
    private static Vec2 lerp(Vec2 a, Vec2 b, double alpha) { return a.add(new Vec2(b.x() - a.x(), b.y() - a.y()).scale(Math.max(0, Math.min(1, alpha)))); }
    public int size() { return samples.size(); }
    public long received() { return received; }
    public long outOfOrder() { return outOfOrder; }
    public double intervalMillis() { return intervalMillis; }
    public double receiveRate() { return intervals == 0 || lastArrival <= firstArrival ? 0 : intervals * 1_000_000_000.0 / (lastArrival - firstArrival); }
    public void clear() { samples.clear(); cursor = Double.NaN; }
}
