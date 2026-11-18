package dev.gamecore.client.network;

import dev.gamecore.network.transport.IoResources;
import dev.gamecore.network.transport.ProtocolMessages;
import dev.gamecore.network.transport.ProtocolPipeline;
import dev.gamecore.network.experiment.NetworkProfile;
import dev.gamecore.network.experiment.SimulatedNetworkHandler;
import dev.gamecore.protocol.TankProtocol;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单页面异步TCP连接；回调只推进握手/发布不可变消息，完全不引用libGDX。
 * DNS使用有界查询的异步解析器；关闭不在渲染线程等待，termination用于控制面验收。
 */
public final class GameNetworkClient implements AutoCloseable {
    public record Metrics(long rttNanos, double snapshotIntervalMillis, double snapshotsPerSecond, long snapshots) { }
    private final NetworkEventDispatcher dispatcher;
    private final EventLoopGroup group = IoResources.group(1, "tank-desktop-network");
    private final DnsAddressResolverGroup resolver = new DnsAddressResolverGroup(new DnsNameResolverBuilder()
            .datagramChannelType(NioDatagramChannel.class).queryTimeoutMillis(1000).maxQueriesPerResolve(4)
            .consolidateCacheSize(16).ttl(0, 60).negativeTtl(1));
    private final AtomicReference<Channel> channel = new AtomicReference<>();
    private final AtomicBoolean closing = new AtomicBoolean(), failed = new AtomicBoolean();
    private final AtomicInteger pendingWrites = new AtomicInteger();
    private final AtomicLong requests = new AtomicLong(2);
    private final CompletableFuture<Void> termination = new CompletableFuture<>();
    private volatile boolean joined;
    private final NetworkProfile profile;
    private final boolean development;
    private volatile Metrics metrics = new Metrics(0, 0, 0, 0);
    private volatile SimulatedNetworkHandler simulation;
    private final dev.gamecore.network.transport.TrafficObserver traffic;
    public GameNetworkClient(NetworkEventDispatcher dispatcher) {
        this(dispatcher, NetworkProfile.NONE, Boolean.getBoolean("gamecore.network.dev"));
    }
    GameNetworkClient(NetworkEventDispatcher dispatcher, NetworkProfile profile, boolean development) {
        this(dispatcher, profile, development, null);
    }
    /** traffic 非空时安装只读旁路；为空时管线与正式连接完全相同。 */
    GameNetworkClient(NetworkEventDispatcher dispatcher, NetworkProfile profile, boolean development,
                      dev.gamecore.network.transport.TrafficObserver traffic) {
        this.dispatcher = dispatcher;
        this.profile = profile; this.development = development; this.traffic = traffic;
        group.terminationFuture().addListener(future -> {
            if (future.isSuccess()) { termination.complete(null); } else { termination.completeExceptionally(future.cause()); }
        });
    }
    public void connect(ServerEndpoint endpoint, String name) {
        if (profile.enabled() && (!development || !endpoint.loopback())) {
            problem(ConnectionState.FAILED, "Network simulation requires LOCAL DEV loopback"); return;
        }
        if (name == null || name.isBlank() || name.length() > 64) { problem(ConnectionState.FAILED, "请输入1到64个字符的玩家名称"); return; }
        if (closing.get()) { return; }
        dispatcher.notice(ConnectionState.CONNECTING, "正在连接服务器");
        try {
            var bootstrap = new Bootstrap().group(group).channel(NioSocketChannel.class).resolver(resolver)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000).option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(16_384, 32_768))
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel socket) {
                            channel.set(socket);
                            if (closing.get()) { socket.close(); return; }
                            socket.pipeline().addLast("readTimeout", new ReadTimeoutHandler(8));
                            socket.pipeline().addLast("writeTimeout", new WriteTimeoutHandler(3));
                            ProtocolPipeline.install(socket.pipeline());
                            if (profile.enabled()) {
                                simulation = new SimulatedNetworkHandler(profile); socket.pipeline().addLast("developmentNetwork", simulation);
                            }
                            if (traffic != null) { socket.pipeline().addLast("trafficTap", new dev.gamecore.network.transport.TrafficTapHandler(traffic)); }
                            socket.pipeline().addLast("handshake", new Handler(name));
                        }
                    });
            bootstrap.connect(InetSocketAddress.createUnresolved(endpoint.host(), endpoint.port())).addListener(future -> {
                if (!future.isSuccess() && !closing.get()) { problem(ConnectionState.FAILED, "无法连接服务器，请检查地址、端口及服务器状态"); }
            });
            group.next().schedule(() -> {
                if (!joined && !closing.get()) { problem(ConnectionState.FAILED, "连接或加入房间超时，请重试"); }
            }, 7, TimeUnit.SECONDS);
        } catch (RuntimeException failure) { problem(ConnectionState.FAILED, "连接失败，请检查服务器地址后重试"); }
    }
    public boolean sendInput(TankProtocol.PlayerInput value) {
        return joined && send(ProtocolMessages.envelope(requests.incrementAndGet(), builder -> builder.setPlayerInput(value)));
    }
    private boolean send(TankProtocol.Envelope value) {
        var socket = channel.get();
        if (closing.get() || socket == null || !socket.isActive()) { return false; }
        if (!socket.isWritable()) { return false; }
        if (pendingWrites.incrementAndGet() > 32) { pendingWrites.decrementAndGet(); problem(ConnectionState.DISCONNECTED, "网络发送积压，连接已关闭，请重试"); return false; }
        ChannelFuture write = socket.writeAndFlush(value);
        write.addListener(future -> {
            pendingWrites.decrementAndGet();
            if (!future.isSuccess() && !closing.get()) { problem(ConnectionState.DISCONNECTED, "发送失败，服务器连接已中断"); }
        });
        return true;
    }
    public CompletionStage<Void> termination() { return termination.minimalCompletionStage(); }
    public boolean isTerminated() { return group.isTerminated(); }
    public int pendingWrites() { return pendingWrites.get(); }
    public Metrics metrics() { return metrics; }
    public SimulatedNetworkHandler simulation() { return simulation; }
    public void problem(ConnectionState state, String text) {
        if (failed.compareAndSet(false, true) && !closing.get()) {
            dev.gamecore.network.transport.TrafficTapHandler.report(traffic, "连接失败 " + state);
            dispatcher.fail(state, text); close();
        }
    }
    @Override public void close() {
        if (!closing.compareAndSet(false, true)) { return; }
        var socket = channel.get(); if (socket != null) { socket.close(); }
        try { resolver.close(); } finally { group.shutdownGracefully(0, 3, TimeUnit.SECONDS); }
    }
    private final class Handler extends SimpleChannelInboundHandler<TankProtocol.Envelope> {
        private final String name;
        private int phase;
        private ScheduledFuture<?> heartbeat;
        private final java.util.LinkedHashMap<Long, Long> probes = new java.util.LinkedHashMap<>();
        private long helloSent, firstSnapshot, lastSnapshot, snapshots;
        Handler(String name) { this.name = name; }
        @Override public void channelActive(ChannelHandlerContext context) {
            if (closing.get()) { context.close(); return; }
            phase = 1; dispatcher.notice(ConnectionState.NEGOTIATING, "正在协商协议版本");
            helloSent = System.nanoTime();
            send(ProtocolMessages.envelope(1, builder -> builder.setClientHello(TankProtocol.ClientHello.newBuilder().setClientName(name))));
        }
        @Override protected void channelRead0(ChannelHandlerContext context, TankProtocol.Envelope value) {
            if (closing.get()) { return; }
            if (value.getProtocolVersion() != ProtocolMessages.VERSION) { problem(ConnectionState.INCOMPATIBLE, "协议版本不兼容，请更新客户端和服务器"); return; }
            if (!value.getUnknownFields().asMap().isEmpty()) { problem(ConnectionState.INCOMPATIBLE, "服务器消息版本不兼容"); return; }
            if (value.hasError()) {
                String code = value.getError().getCode();
                if (phase == 3 && java.util.Set.of("LATE_INPUT", "EARLY_INPUT", "TARGET_ORDER", "DUPLICATE_SEQUENCE", "INPUT_QUEUE_FULL", "NOT_RUNNING").contains(code)) {
                    if (!dispatcher.message(value)) { overflow(); } return;
                }
                problem(code.equals("INVALID_ENVELOPE") ? ConnectionState.INCOMPATIBLE : ConnectionState.REJECTED, errorText(code)); return;
            }
            if (phase == 1 && value.hasServerHello() && value.getRequestId() == 1) {
                recordRtt(System.nanoTime() - helloSent);
                var hello = value.getServerHello(); int tps = hello.getServerTps();
                if (!hello.getHeartbeatSupported() || !hello.getRulesVersion().equals("tank/1") || hello.getSimulationTps() != 60
                        || tps < 10 || tps > 60 || 60 % tps != 0) {
                    problem(ConnectionState.INCOMPATIBLE, "服务器协议或规则版本不兼容，请更新服务器"); return;
                }
                if (hello.getConnectionId().isBlank() || hello.getConnectionId().length() > 100 || hello.getJoinCredential().isBlank()
                        || hello.getJoinCredential().length() > 128) { malformed(); return; }
                phase = 2;
                if (!dispatcher.message(value) || !dispatcher.notice(ConnectionState.JOINING, "正在加入联机房间")) { overflow(); return; }
                send(ProtocolMessages.envelope(2, builder -> builder.setJoinRoomRequest(TankProtocol.JoinRoomRequest.newBuilder()
                        .setRoomId("tank-1").setCredential(hello.getJoinCredential()))));
                return;
            }
            if (phase == 2 && value.hasJoinRoomResponse() && value.getRequestId() == 2) {
                var join = value.getJoinRoomResponse();
                if (!join.getRoomId().equals("tank-1") || join.getPlayerSlot() < 0 || join.getPlayerSlot() > 1
                        || join.getMatchId().isBlank() || join.getMatchId().length() > 80) { malformed(); return; }
                joined = true; phase = 3;
                if (!dispatcher.message(value)) { overflow(); return; }
                heartbeat = context.executor().scheduleWithFixedDelay(() -> {
                    long request = requests.incrementAndGet(); probes.put(request, System.nanoTime());
                    while (probes.size() > 4) { probes.remove(probes.keySet().iterator().next()); }
                    send(ProtocolMessages.envelope(request, builder -> builder.setHeartbeat(TankProtocol.Heartbeat.getDefaultInstance())));
                }, 1, 1, TimeUnit.SECONDS);
                return;
            }
            if (phase == 3 && value.hasHeartbeat() && value.getRequestId() > 2 && value.getRequestId() <= requests.get()) {
                var sent = probes.remove(value.getRequestId()); if (sent != null) { recordRtt(System.nanoTime() - sent); } return;
            }
            if (phase == 3 && value.getRequestId() == 0 && (value.hasWorldSnapshot() || value.hasGameEvent() || value.hasMatchResult())) {
                long now = System.nanoTime();
                if (value.hasWorldSnapshot()) {
                    if (snapshots++ == 0) { firstSnapshot = now; }
                    double interval = lastSnapshot == 0 ? 0 : (now - lastSnapshot) / 1_000_000.0;
                    lastSnapshot = now;
                    metrics = new Metrics(metrics.rttNanos(), interval, now == firstSnapshot ? 0 : (snapshots - 1) * 1_000_000_000.0 / (now - firstSnapshot), snapshots);
                }
                if (!dispatcher.message(value, now)) { overflow(); } return;
            }
            malformed();
        }
        @Override public void channelInactive(ChannelHandlerContext context) {
            if (heartbeat != null) { heartbeat.cancel(false); }
            if (phase != 0 && !closing.get()) { problem(ConnectionState.DISCONNECTED, "与服务器的连接已中断，请返回大厅或重新连接"); }
        }
        @Override public void exceptionCaught(ChannelHandlerContext context, Throwable failure) {
            problem(ConnectionState.DISCONNECTED, "收到异常数据或连接超时，已安全断开");
        }
        private void malformed() { problem(ConnectionState.FAILED, "服务器消息异常，已安全断开，请检查服务器版本"); }
        private void overflow() { problem(ConnectionState.DISCONNECTED, "接收消息过多，连接已关闭，请重试"); }
        private void recordRtt(long sample) {
            if (sample < 0 || sample > 10_000_000_000L) { return; }
            long previous = metrics.rttNanos();
            metrics = new Metrics(previous == 0 ? sample : (previous * 4 + sample) / 5,
                    metrics.snapshotIntervalMillis(), metrics.snapshotsPerSecond(), metrics.snapshots());
        }
    }
    private static String errorText(String code) {
        return switch (code) {
            case "ROOM_FULL_OR_CLOSED" -> "房间已满或比赛已开始，请稍后重试";
            case "JOIN_TIMEOUT" -> "加入房间超时，请重新连接";
            case "INVALID_ENVELOPE" -> "协议版本不兼容，请更新客户端和服务器";
            case "RATE_LIMIT" -> "发送操作过于频繁，服务器已断开连接";
            case "JOIN_DENIED" -> "服务器拒绝加入房间，请重新连接";
            default -> "服务器拒绝了本次连接，请检查服务器状态";
        };
    }
}
