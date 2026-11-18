package dev.gamecore.server.core;

import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** 单房间执行器调用的固定步时钟；默认最多追赶3步，超2秒落后显式取消比赛，绝不改变dt或伪造胜负。 */
public final class TickRunner {
    public record Metrics(long steps, long overruns, long limitedCatchups, long maxLagNanos, long failures) { }
    private final int tps;
    private final LongSupplier clock;
    private final Runnable step;
    private final Consumer<String> abort;
    private long epoch, index = 1, steps, overruns, limited, lag, failures;
    private volatile Metrics metrics = new Metrics(0, 0, 0, 0, 0);
    public TickRunner(int tps, LongSupplier clock, Runnable step, Consumer<String> abort) {
        if (tps < 10 || tps > 60 || 60 % tps != 0) { throw new IllegalArgumentException("Invalid TPS"); }
        this.tps = tps; this.clock = clock; this.step = step; this.abort = abort; epoch = clock.getAsLong();
    }
    private long deadline() { return epoch + (index / tps) * 1_000_000_000L + (index % tps) * 1_000_000_000L / tps; }
    public void runDue() {
        try {
            int count = 0; long now = clock.getAsLong();
            lag = Math.max(lag, Math.max(0, now - deadline()));
            if (now - deadline() > 2_000_000_000L) { abort.accept("TICK_OVERLOAD"); epoch = now; index = 1; limited++; }
            while (now >= deadline() && count < 3) {
                long start = clock.getAsLong(); step.run(); steps++; index++; count++;
                now = clock.getAsLong(); if (now - start > 1_000_000_000L / tps) { overruns++; }
            }
            if (now >= deadline()) { limited++; }
        } catch (RuntimeException failure) {
            failures++; abort.accept("SIMULATION_FAILURE"); epoch = clock.getAsLong(); index = 1;
        } finally { metrics = new Metrics(steps, overruns, limited, lag, failures); }
    }
    public Metrics metrics() { return metrics; }
}
