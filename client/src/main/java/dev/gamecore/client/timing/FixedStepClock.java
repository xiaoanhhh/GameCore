package dev.gamecore.client.timing;

/** 固定步长与明确降级：每帧接收至多 250ms、执行至多五步；丢弃超额整步，保留不足一步的余量。 */
public final class FixedStepClock {
    public static final int MAX_UPDATES = 5;
    private final double step;
    private double accumulator;
    private long slowFrames;
    private double droppedSeconds;
    public FixedStepClock(int ticksPerSecond) {
        if (ticksPerSecond <= 0) { throw new IllegalArgumentException("Invalid tick rate"); }
        step = 1.0 / ticksPerSecond;
    }
    public int advance(double elapsed) {
        if (!Double.isFinite(elapsed) || elapsed < 0) { return 0; }
        double accepted = Math.min(elapsed, .25);
        double dropped = elapsed - accepted;
        accumulator += accepted;
        int available = (int) Math.floor((accumulator + 1e-8) / step);
        int count = Math.min(available, MAX_UPDATES);
        accumulator = Math.max(0, accumulator - count * step);
        if (available > MAX_UPDATES) {
            double wholeSteps = (available - MAX_UPDATES) * step;
            accumulator = Math.max(0, accumulator - wholeSteps);
            dropped += wholeSteps;
        }
        if (dropped > 0) { slowFrames++; droppedSeconds += dropped; }
        return count;
    }
    public void reset() { accumulator = 0; }
    public long slowFrames() { return slowFrames; }
    public double droppedSeconds() { return droppedSeconds; }
    public void restart() { reset(); slowFrames = 0; droppedSeconds = 0; }
}
