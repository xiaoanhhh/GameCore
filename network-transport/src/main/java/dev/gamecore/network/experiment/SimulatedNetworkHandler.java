package dev.gamecore.network.experiment;

import dev.gamecore.protocol.TankProtocol;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** 仅开发连接安装在Protobuf之后；队列只持不可变Envelope，不持ByteBuf，不阻塞EventLoop。 */
public final class SimulatedNetworkHandler extends ChannelDuplexHandler {
    private record Write(TankProtocol.Envelope value, ChannelPromise promise) { }
    private final SimulatedLink<TankProtocol.Envelope> inbound;
    private final SimulatedLink<Write> outbound;
    private final LongSupplier clock;
    private ScheduledFuture<?> pump;
    public SimulatedNetworkHandler(NetworkProfile profile) { this(profile, System::nanoTime); }
    public SimulatedNetworkHandler(NetworkProfile profile, LongSupplier clock) {
        inbound = new SimulatedLink<>(profile, 0x51L); outbound = new SimulatedLink<>(profile, 0x92L); this.clock = clock;
    }
    @Override public void handlerAdded(ChannelHandlerContext context) {
        pump = context.executor().scheduleAtFixedRate(() -> deliver(context), 0, 2, TimeUnit.MILLISECONDS);
    }
    @Override public void channelRead(ChannelHandlerContext context, Object message) {
        if (!(message instanceof TankProtocol.Envelope value)) { context.fireChannelRead(message); return; }
        if (inbound.offer(value, clock.getAsLong(), value.hasWorldSnapshot(), value.getPayloadCase().name()) == SimulatedLink.Admission.FULL) { context.close(); }
        deliver(context);
    }
    @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
        if (!(message instanceof TankProtocol.Envelope value)) { context.write(message, promise); return; }
        var status = outbound.offer(new Write(value, promise), clock.getAsLong(), value.hasPlayerInput(), value.getPayloadCase().name());
        if (status == SimulatedLink.Admission.DROPPED) { promise.trySuccess(); }
        else if (status == SimulatedLink.Admission.FULL) { promise.tryFailure(new IllegalStateException("Simulation queue full")); context.close(); }
        deliver(context);
    }
    private void deliver(ChannelHandlerContext context) {
        long now = clock.getAsLong();
        for (var write : outbound.drain(now)) { context.write(write.value(), write.promise()); }
        context.flush();
        for (var value : inbound.drain(now)) { context.fireChannelRead(value); }
    }
    public SimulatedLink.Metrics inboundMetrics() { return inbound.metrics(); }
    public SimulatedLink.Metrics outboundMetrics() { return outbound.metrics(); }
    public java.util.List<SimulatedLink.Trace> inboundTrace() { return inbound.trace(); }
    public java.util.List<SimulatedLink.Trace> outboundTrace() { return outbound.trace(); }
    private void clear() {
        if (pump != null) { pump.cancel(false); }
        for (var write : outbound.clear()) { write.promise().tryFailure(new ClosedChannelException()); }
        inbound.clear();
    }
    @Override public void channelInactive(ChannelHandlerContext context) { clear(); context.fireChannelInactive(); }
    @Override public void handlerRemoved(ChannelHandlerContext context) { clear(); }
}
