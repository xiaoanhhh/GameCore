package dev.gamecore.server.client;

import dev.gamecore.protocol.TankProtocol;
import dev.gamecore.server.core.GameRoom;
import dev.gamecore.network.transport.IoResources;
import dev.gamecore.network.transport.ProtocolPipeline;
import dev.gamecore.server.network.Wire;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** 无图形客户端：仅保存服务器快照并发送意图，不创建TankSession或自行判定碰撞/生命/胜者。 */
public final class GameCliClient implements AutoCloseable {
    private final EventLoopGroup group = IoResources.group(1, "tank-cli-network");
    private final ArrayBlockingQueue<TankProtocol.Envelope> inbox = new ArrayBlockingQueue<>(128);
    private final AtomicReference<TankProtocol.WorldSnapshot> latest = new AtomicReference<>();
    private final AtomicLong requests = new AtomicLong(2);
    private final AtomicInteger pendingWrites = new AtomicInteger();
    private Channel channel;
    private TankProtocol.ServerHello hello;
    private TankProtocol.JoinRoomResponse join;
    private boolean closed;
    private GameCliClient() { }
    public static GameCliClient connect(String host, int port, String name) throws InterruptedException {
        if (host == null || host.isBlank() || port < 1 || port > 65535 || name == null || name.isBlank() || name.length() > 64) {
            throw new IllegalArgumentException("Invalid client options");
        }
        var client = new GameCliClient();
        try {
            client.channel = new Bootstrap().group(client.group).channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel socket) {
                            ProtocolPipeline.install(socket.pipeline());
                            socket.pipeline().addLast("inbox", new SimpleChannelInboundHandler<TankProtocol.Envelope>() {
                                @Override protected void channelRead0(ChannelHandlerContext context, TankProtocol.Envelope envelope) {
                                    if (envelope.getProtocolVersion() != Wire.VERSION) { context.close(); }
                                    else if (envelope.hasWorldSnapshot()) { client.latest.set(envelope.getWorldSnapshot()); }
                                    else if (!client.inbox.offer(envelope)) { context.close(); }
                                }
                                @Override public void exceptionCaught(ChannelHandlerContext context, Throwable failure) { context.close(); }
                            });
                        }
                    }).connect(new InetSocketAddress(host, port)).sync().channel();
            client.channel.closeFuture().addListener(future -> client.group.shutdownGracefully(0, 5, TimeUnit.SECONDS));
            client.send(Wire.envelope(1, builder -> builder.setClientHello(TankProtocol.ClientHello.newBuilder().setClientName(name)))).sync();
            var first = client.expect(1);
            if (!first.hasServerHello()) { throw new IllegalStateException("Expected ServerHello"); }
            client.hello = first.getServerHello();
            client.send(Wire.envelope(2, builder -> builder.setJoinRoomRequest(TankProtocol.JoinRoomRequest.newBuilder()
                    .setRoomId(GameRoom.ID).setCredential(client.hello.getJoinCredential())))).sync();
            var response = client.expect(2);
            if (!response.hasJoinRoomResponse()) { throw new IllegalStateException("Expected JoinRoomResponse"); }
            client.join = response.getJoinRoomResponse(); return client;
        } catch (InterruptedException | RuntimeException | Error failure) {
            try { client.close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); } throw failure;
        }
    }
    private TankProtocol.Envelope expect(long request) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (true) {
            long remaining = deadline - System.nanoTime(); if (remaining <= 0) { throw new IllegalStateException("Handshake timed out"); }
            var value = receive(Duration.ofNanos(remaining));
            if (value == null) { throw new IllegalStateException("Handshake timed out"); }
            if (value.hasError()) { throw new IllegalStateException(value.getError().getCode()); }
            if (value.getRequestId() == request) { return value; }
        }
    }
    public TankProtocol.ServerHello hello() { return hello; }
    public TankProtocol.JoinRoomResponse join() { return join; }
    public TankProtocol.WorldSnapshot latest() { return latest.get(); }
    public boolean active() { return channel != null && channel.isActive(); }
    public boolean terminated() { return group.isTerminated(); }
    public ChannelFuture closeFuture() { return channel.closeFuture(); }
    public ChannelFuture sendInput(TankProtocol.PlayerInput input) { return send(Wire.envelope(requests.incrementAndGet(), builder -> builder.setPlayerInput(input))); }
    public ChannelFuture send(TankProtocol.Envelope envelope) {
        if (!active()) { return channel.newFailedFuture(new IllegalStateException("Disconnected")); }
        if (pendingWrites.incrementAndGet() > 32) {
            pendingWrites.decrementAndGet(); channel.close(); return channel.newFailedFuture(new IllegalStateException("Too many writes"));
        }
        return channel.writeAndFlush(envelope).addListener(future -> { pendingWrites.decrementAndGet(); if (!future.isSuccess()) { channel.close(); } });
    }
    public TankProtocol.Envelope receive(Duration timeout) throws InterruptedException {
        IoResources.requireOutside(group); return inbox.poll(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }
    @Override public void close() { IoResources.requireOutside(group); closeOnControlThread(); }
    private synchronized void closeOnControlThread() {
        if (closed) { return; } closed = true;
        try { if (channel != null) { if (channel.isOpen()) { channel.close(); } IoResources.await(channel.closeFuture()); } }
        finally { IoResources.stop(group); }
    }
}
