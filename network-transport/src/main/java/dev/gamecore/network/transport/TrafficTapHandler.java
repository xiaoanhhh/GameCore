package dev.gamecore.network.transport;

import com.google.protobuf.MessageLite;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 安装在 Protobuf 编解码之后的只读旁路：原样透传全部消息和连接事件。
 * 观察方的异常在这里截断，诊断代码不能中断连接或改变消息顺序。未开启诊断时不安装。
 */
public final class TrafficTapHandler extends ChannelDuplexHandler {
    private final TrafficObserver observer;
    public TrafficTapHandler(TrafficObserver observer) { this.observer = Objects.requireNonNull(observer); }

    /** 供不经过管线的连接失败路径复用同一套异常隔离；observer 可为空。 */
    public static void report(TrafficObserver observer, String state) {
        if (observer != null) { guarded(observer, value -> value.connection(state)); }
    }
    private static void guarded(TrafficObserver observer, Consumer<TrafficObserver> action) {
        try { action.accept(observer); } catch (RuntimeException ignored) { /* 诊断失败不影响连接。 */ }
    }

    @Override public void channelActive(ChannelHandlerContext context) {
        report(observer, "TCP 已连接");
        context.fireChannelActive();
    }
    @Override public void channelInactive(ChannelHandlerContext context) {
        report(observer, "TCP 已断开");
        context.fireChannelInactive();
    }
    @Override public void channelRead(ChannelHandlerContext context, Object message) {
        if (message instanceof MessageLite value) { guarded(observer, target -> target.received(value)); }
        context.fireChannelRead(message);
    }
    @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
        if (message instanceof MessageLite value) { guarded(observer, target -> target.sent(value)); }
        context.write(message, promise);
    }
}
