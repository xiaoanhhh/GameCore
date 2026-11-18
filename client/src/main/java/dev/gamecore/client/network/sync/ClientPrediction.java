package dev.gamecore.client.network.sync;

import dev.gamecore.protocol.TankProtocol;
import dev.gamecore.tank.TankCommand;
import dev.gamecore.tank.TankGame;
import dev.gamecore.tank.TankMovement;
import dev.gamecore.tank.TankSnapshot;
import dev.gamecore.tank.Vec2;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

/**
 * 只预测移动的有界重放器，不恢复完整TankGame、不预测子弹/血量/结果。
 * 权威基线与显示Motion分离；未知远端新输入只能在下一份权威快照时校正。
 */
public final class ClientPrediction {
    public static final int MAX_REPLAY_TICKS = 60;
    public record Metrics(double error, double meanError, double maxError, double p95Error, long corrections,
                          int pendingInputs, int replayTicks, long overflows, long expiredInputs) { }
    private final InputHistory history = new InputHistory();
    private final TreeMap<Long, TankMovement.Motion> projected = new TreeMap<>();
    private final double[] errors = new double[256];
    private TankSnapshot authority;
    private TankProtocol.WorldSnapshot wire;
    private TankMovement.Motion predicted;
    private Vec2 offset = new Vec2(0, 0);
    private double angleOffset, error, sumError, maxError;
    private long samples, corrections, arrival, rttNanos, lastVisualNanos;
    private int slot, replayTicks;
    private boolean enabled = true, exhausted;
    public void enabled(boolean value) { enabled = value; offset = new Vec2(0, 0); angleOffset = 0; projected.clear(); }
    public boolean enabled() { return enabled; }
    public void rttNanos(long value) { rttNanos = Math.max(0, Math.min(2_000_000_000L, value)); }
    public void input(TankProtocol.PlayerInput value) {
        long before = history.overflows(); history.add(value);
        if (history.overflows() != before) { exhausted = true; projected.clear(); offset = new Vec2(0, 0); angleOffset = 0; }
    }
    public void reject(long sequence) { history.reject(sequence); }
    public long estimatedServerTick(long now) {
        if (wire == null) { return 0; }
        long age = Math.min(1_000_000_000L, Math.max(0, now - arrival));
        return wire.getServerTick() + age * wire.getServerTps() / 1_000_000_000L
                + (rttNanos * wire.getServerTps() + 1_999_999_999L) / 2_000_000_000L;
    }
    public int leadTicks(int tps) {
        return Math.min(Math.max(2, tps / 2), Math.max(2, tps / 10) + (int) ((rttNanos * tps + 1_999_999_999L) / 2_000_000_000L));
    }
    public void reconcile(TankSnapshot snapshot, TankProtocol.WorldSnapshot value, int playerSlot, long arrived, long now) {
        TankMovement.Motion old = null;
        boolean correction = false;
        if (authority != null && enabled && !exhausted) {
            forecast(now); old = predicted;
            var expected = projected.get(value.getServerTick());
            if (expected != null && value.getServerTick() > wire.getServerTick()) {
                var actual = snapshot.tanks().get(playerSlot);
                error = StrictMath.sqrt(expected.position().distanceSquared(actual.position()));
                sumError += error; maxError = Math.max(maxError, error); errors[(int) (samples++ % errors.length)] = error;
                correction = error > 1e-6 || Math.abs(shortAngle(expected.heading() - actual.heading())) > 1e-6;
                if (correction) { corrections++; }
            }
        }
        authority = snapshot; wire = value; slot = playerSlot; arrival = arrived; exhausted = false;
        history.acknowledge(value.getInputAcks(slot).getSequence(), value.getServerTick());
        forecast(now);
        if (correction && old != null && predicted != null) {
            offset = new Vec2(old.position().x() + offset.x() - predicted.position().x(), old.position().y() + offset.y() - predicted.position().y());
            angleOffset = shortAngle(old.heading() + angleOffset - predicted.heading());
            if (offset.distanceSquared(new Vec2(0, 0)) > 4) { offset = new Vec2(0, 0); angleOffset = 0; }
        }
        if (snapshot.state() != TankGame.State.RUNNING) { clearPending(); }
    }
    /** 最多60服务器步（默认120、10TPS时最多360核心步）；同Tick只应用最高sequence，不按消息条数多走时间。 */
    private void forecast(long now) {
        if (authority == null) { return; }
        predicted = TankMovement.Motion.of(authority.tanks().get(slot)); replayTicks = 0; projected.clear();
        if (!enabled || exhausted || authority.state() != TankGame.State.RUNNING || now - arrival > 1_000_000_000L) { return; }
        int tps = wire.getServerTps(); long base = wire.getServerTick();
        var scheduled = new TreeMap<Long, TankProtocol.PlayerInput>();
        for (var input : history.values()) { scheduled.put(input.getTargetTick(), input); }
        long horizon = Math.min(base + MAX_REPLAY_TICKS, Math.max(estimatedServerTick(now) + leadTicks(tps), scheduled.isEmpty() ? base : scheduled.lastKey()));
        var held = new TankProtocol.PlayerInput[2]; long[] expires = new long[2];
        for (int p = 0; p < 2; p++) {
            var ack = wire.getInputAcks(p); held[p] = ack.hasActiveInput() ? ack.getActiveInput() : null; expires[p] = ack.getExpiresTick();
        }
        var motions = authority.tanks().stream().map(TankMovement.Motion::of).toList();
        for (long tick = base + 1; tick <= horizon; tick++) {
            var next = scheduled.get(tick); if (next != null) { held[slot] = next; expires[slot] = tick + Math.max(1, tps / 2); }
            for (int sub = 0; sub < 60 / tps; sub++) {
                var first = command(held[0], tick, expires[0], motions.get(0).heading());
                var second = command(held[1], tick, expires[1], motions.get(1).heading());
                motions = TankMovement.step(authority.map(), motions, List.of(first, second));
            }
            projected.put(tick, motions.get(slot)); replayTicks++;
        }
        predicted = motions.get(slot);
    }
    private static TankCommand command(TankProtocol.PlayerInput input, long tick, long expires, double heading) {
        if (input == null || tick >= expires) { return TankCommand.IDLE; }
        int move = input.getMove() == TankProtocol.MoveDirection.FORWARD ? 1 : input.getMove() == TankProtocol.MoveDirection.BACKWARD ? -1 : 0;
        int turn = input.getTurn() == TankProtocol.TurnDirection.LEFT ? 1 : input.getTurn() == TankProtocol.TurnDirection.RIGHT ? -1 : 0;
        return TankMovement.command(move, turn, false, input.hasAimDegrees() ? input.getAimDegrees() : null, heading);
    }
    public TankSnapshot.Tank display(long now) {
        if (authority == null) { return null; }
        forecast(now); var original = authority.tanks().get(slot);
        if (!enabled || exhausted || authority.state() != TankGame.State.RUNNING) { return original; }
        double elapsed = lastVisualNanos == 0 ? 0 : Math.max(0, Math.min(250_000_000L, now - lastVisualNanos)) / 1_000_000_000.0;
        lastVisualNanos = now;
        double decay = StrictMath.exp(-elapsed / .08); offset = offset.scale(decay); angleOffset *= decay;
        var position = predicted.position().add(offset);
        if (authority.map().blocked(position, TankGame.TANK_RADIUS)) { position = predicted.position(); offset = new Vec2(0, 0); }
        return new TankSnapshot.Tank(original.id(), position, TankGame.normalize(predicted.heading() + angleOffset), predicted.velocity(), original.health(), original.cooldown());
    }
    public TankMovement.Motion predicted() { return predicted; }
    public Metrics metrics() {
        int count = (int) Math.min(samples, errors.length); var sorted = Arrays.copyOf(errors, count); Arrays.sort(sorted);
        return new Metrics(error, samples == 0 ? 0 : sumError / samples, maxError, count == 0 ? 0 : sorted[Math.max(0, (int) Math.ceil(count * .95) - 1)],
                corrections, history.size(), replayTicks, history.overflows(), history.expired());
    }
    public void clearPending() { history.clear(); projected.clear(); offset = new Vec2(0, 0); angleOffset = 0; }
    public static double shortAngle(double angle) { return (TankGame.normalize(angle) + 180) % 360 - 180; }
}
