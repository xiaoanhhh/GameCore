package dev.gamecore.server;

import dev.gamecore.api.session.GameSession;
import dev.gamecore.tank.TankCommand;
import dev.gamecore.tank.TankSnapshot;
import dev.gamecore.tank.TankSession;
import dev.gamecore.tank.TankMode;
import dev.gamecore.tank.TankBot;
import java.util.function.BiFunction;
import dev.gamecore.server.core.GameRoom;
import dev.gamecore.server.core.MapResources;
import dev.gamecore.server.core.RoomFrame;
import dev.gamecore.server.core.TickRunner;
import dev.gamecore.server.network.GameConnection;
import dev.gamecore.network.transport.IoResources;
import dev.gamecore.server.network.ProtocolHandler;
import dev.gamecore.network.transport.ProtocolPipeline;
import dev.gamecore.tank.TankMap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** 独立服务器资源组合：两个网络worker与唯一房间线程分离；控制台/测试负责生命周期等待。 */
public final class NettyGameServer implements AutoCloseable {
    private final EventLoopGroup acceptors = IoResources.group(1, "tank-accept");
    private final EventLoopGroup workers = IoResources.group(2, "tank-network");
    private final ChannelGroup children = new DefaultChannelGroup("tank-connections", workers.next(), true);
    private final AtomicReference<Thread> roomThread = new AtomicReference<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(action -> {
        var thread = new Thread(action, "tank-room"); roomThread.set(thread); return thread;
    });
    private final AtomicInteger connections = new AtomicInteger();
    private final AtomicLong connectionIds = new AtomicLong();
    private final String identityEpoch = UUID.randomUUID().toString();
    private final GameRoom room;
    private final TickRunner runner;
    private Channel listener;
    private ScheduledFuture<?> pump;
    private boolean closed;
    private NettyGameServer(ServerSettings settings, TankMap map,
            BiFunction<TankMap, Long, GameSession<TankCommand, TankSnapshot>> sessions) {
        room = new GameRoom(map, settings.tps(), settings.seed(), sessions);
        runner = new TickRunner(settings.tps(), System::nanoTime, room::step, room::abort);
    }
    public static NettyGameServer start(ServerSettings settings) throws IOException, InterruptedException {
        return start(settings, MapResources.load(settings.mapId()));
    }
    /** 包内测试注入受信地图；网络协议没有上传地图或修改出生点的入口。 */
    static NettyGameServer start(ServerSettings settings, TankMap map) throws InterruptedException {
        return start(settings, map, (arena, seed) -> new TankSession(arena,
            TankMode.DUEL, TankBot.Level.NORMAL, seed));
    }
    /** 实验启动口：工厂仅在Room线程执行；服务端默认/协议均不选择第三方ECS。 */
    public static NettyGameServer start(ServerSettings settings,
            BiFunction<TankMap, Long, GameSession<TankCommand, TankSnapshot>> sessions)
            throws IOException, InterruptedException {
        return start(settings, MapResources.load(settings.mapId()), sessions);
    }
    private static NettyGameServer start(ServerSettings settings, TankMap map,
            BiFunction<TankMap, Long, GameSession<TankCommand, TankSnapshot>> sessions) throws InterruptedException {
        var server = new NettyGameServer(settings, map, sessions);
        try {
            var initializer = new ChannelInitializer<SocketChannel>() {
                @Override protected void initChannel(SocketChannel channel) {
                    server.children.add(channel);
                    if (server.connections.incrementAndGet() > 8) { server.connections.decrementAndGet(); channel.close(); return; }
                    channel.closeFuture().addListener(future -> server.connections.decrementAndGet());
                    var connection = new GameConnection(channel, server.identityEpoch + "/" + server.connectionIds.incrementAndGet());
                    channel.pipeline().addLast("readTimeout", new ReadTimeoutHandler(settings.idleSeconds()));
                    channel.pipeline().addLast("writeTimeout", new WriteTimeoutHandler(3));
                    ProtocolPipeline.install(channel.pipeline());
                    channel.pipeline().addLast("protocol", new ProtocolHandler(connection, server.room, settings));
                }
            };
            server.listener = new ServerBootstrap().group(server.acceptors, server.workers).channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 16).childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(16_384, 32_768))
                    .childHandler(initializer).bind(new InetSocketAddress(settings.host(), settings.port())).sync().channel();
            server.pump = server.executor.scheduleWithFixedDelay(server.runner::runDue, 0, 1, TimeUnit.MILLISECONDS);
            return server;
        } catch (InterruptedException | RuntimeException | Error failure) {
            try { server.close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); } throw failure;
        }
    }
    public InetSocketAddress address() { return (InetSocketAddress) listener.localAddress(); }
    public RoomFrame latest() { return room.latest(); }
    public TickRunner.Metrics metrics() { return runner.metrics(); }
    public int connectionCount() { return connections.get(); }
    public int queuedInputs() { return room.inputQueue().size(); }
    public boolean isTerminated() { return executor.isTerminated() && workers.isTerminated() && acceptors.isTerminated() && children.isEmpty(); }
    @Override public void close() {
        IoResources.requireOutside(acceptors, workers);
        if (Thread.currentThread() == roomThread.get()) { throw new IllegalStateException("Cannot wait for own room executor"); }
        closeOnControlThread();
    }
    private synchronized void closeOnControlThread() {
        if (closed) { return; } closed = true;
        try {
            if (listener != null) { IoResources.await(listener.close()); }
            if (pump != null) { pump.cancel(false); }
            try { executor.submit(room::close).get(10, TimeUnit.SECONDS); }
            catch (Exception failure) { throw new IllegalStateException("Room shutdown failed", failure); }
            IoResources.await(children.close());
        } finally {
            executor.shutdownNow();
            try { if (!executor.awaitTermination(10, TimeUnit.SECONDS)) { throw new IllegalStateException("Room did not terminate"); } }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
            finally { try { IoResources.stop(workers); } finally { IoResources.stop(acceptors); } }
        }
    }
}
