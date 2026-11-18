package dev.gamecore.client.traffic;

import dev.gamecore.network.transport.TrafficObserver;
import dev.gamecore.platform.api.PlatformMessages.Profile;

/**
 * 交互记录的开关和入口，由主窗口的客户端持有。
 * 判定只看平台返回的用户名（登录名唯一且不可修改），不看可随意修改的显示名。
 * 账户状态每帧同步一次，退出、失效、换账户都走同一条路径，不依赖逐个事件通知。
 * 这是本机展示开关：面板只显示本客户端自己的流量，不从服务器取得额外数据。
 */
public final class TrafficMonitor {
    public static final String ACCOUNT = "testuser";
    private final TrafficLog log;

    public TrafficMonitor() { this(new TrafficLog()); }
    TrafficMonitor(TrafficLog log) { this.log = log; }

    public static boolean allowed(Profile profile) { return profile != null && ACCOUNT.equals(profile.username()); }
    public TrafficLog log() { return log; }
    public boolean active() { return log.enabled(); }

    /** 把开关同步到当前账户并返回是否开启。登录请求返回后才知道账户，因此记录从这里开始。 */
    public boolean sync(Profile profile) {
        boolean allowed = allowed(profile);
        if (allowed != log.enabled()) {
            if (allowed) {
                log.enable();
                log.add(TrafficKind.CONNECTION, TrafficDirection.LOCAL, "账户 " + ACCOUNT + " 已登录，开始记录");
            } else { log.disable(); }
        }
        return allowed;
    }

    /** 新连接的旁路；未开启时返回 null，连接不安装任何诊断处理器。 */
    public TrafficObserver observer(Profile profile) { return sync(profile) ? new TrafficRecorder(log) : null; }

    /** 平台 HTTP 的一次请求响应。只有路径和状态，路径里没有凭证。 */
    public void exchange(String method, String path, int status, String code, long nanos) {
        boolean success = status / 100 == 2;
        log.add(success ? TrafficKind.PLATFORM : TrafficKind.ERROR, TrafficDirection.ROUND_TRIP,
                method + " " + path + " · " + (status == 0 ? "-" : status) + (code.isEmpty() ? "" : " " + code) + " · " + nanos / 1_000_000 + " ms");
    }
}
