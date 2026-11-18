package dev.gamecore.network.transport;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** 自有4.2 NIO组和控制面关闭工具；不借用实验模块/GlobalEventExecutor，也不从IO线程阻塞等待。 */
public final class IoResources {
    private IoResources() { }
    public static EventLoopGroup group(int count, String name) { return new MultiThreadIoEventLoopGroup(count, new DefaultThreadFactory(name), NioIoHandler.newFactory()); }
    public static void requireOutside(EventLoopGroup... groups) {
        for (var group : groups) { for (var executor : group) { if (executor.inEventLoop()) { throw new IllegalStateException("Blocking IO lifecycle call"); } } }
    }
    public static void await(Future<?> future) {
        if (!future.awaitUninterruptibly(10, TimeUnit.SECONDS) || !future.isSuccess()) { throw new IllegalStateException("IO lifecycle failed", future.cause()); }
    }
    public static void stop(EventLoopGroup group) { await(group.shutdownGracefully(0, 5, TimeUnit.SECONDS)); }
}
