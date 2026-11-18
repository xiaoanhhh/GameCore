package dev.gamecore.network.experiment;

/** 可重复应用层实验配置；RTT分成双向各一半，jitter为每方向均匀偏差，非TCP重传/UDP丢包模型。 */
public record NetworkProfile(int rttMillis, int jitterMillis, int dropPercent, long seed,
                             int blackoutStartMillis, int blackoutDurationMillis) {
    public static final NetworkProfile NONE = new NetworkProfile(0, 0, 0, 42, 0, 0);
    public NetworkProfile {
        if (rttMillis < 0 || rttMillis > 2000 || jitterMillis < 0 || jitterMillis > 500 || dropPercent < 0 || dropPercent > 50
                || blackoutStartMillis < 0 || blackoutStartMillis > 3_600_000 || blackoutDurationMillis < 0 || blackoutDurationMillis > 5000) {
            throw new IllegalArgumentException("Invalid bounded network profile");
        }
    }
    public boolean enabled() { return rttMillis != 0 || jitterMillis != 0 || dropPercent != 0 || blackoutDurationMillis != 0; }
}
