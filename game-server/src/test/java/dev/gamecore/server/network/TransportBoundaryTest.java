package dev.gamecore.server.network;

import dev.gamecore.network.transport.ProtocolPipeline;

import dev.gamecore.protocol.TankProtocol;
import dev.gamecore.server.core.RoomFixtures;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Protobuf仍位于TCP字节流内；确定性拆包/坏帧/引用计数与受阻出站验证。 */
class TransportBoundaryTest {
    private static EmbeddedChannel decoder(List<TankProtocol.Envelope> values, AtomicReference<Throwable> error) {
        var channel = new EmbeddedChannel(); ProtocolPipeline.install(channel.pipeline());
        channel.pipeline().addLast(new SimpleChannelInboundHandler<TankProtocol.Envelope>() {
            @Override protected void channelRead0(ChannelHandlerContext context, TankProtocol.Envelope value) { values.add(value); }
            @Override public void exceptionCaught(ChannelHandlerContext context, Throwable failure) { error.set(failure); context.close(); }
        }); return channel;
    }
    private static byte[] encoded(TankProtocol.Envelope message) {
        byte[] payload = message.toByteArray(); var bytes = java.nio.ByteBuffer.allocate(4 + payload.length); bytes.putInt(payload.length).put(payload); return bytes.array();
    }
    @Test void fragmentedAndCoalescedProtobufFramesPreserveMessagesAndReleaseBuffers() {
        var hello = Wire.envelope(7, builder -> builder.setClientHello(TankProtocol.ClientHello.newBuilder().setClientName("奶蛙")));
        var values = new ArrayList<TankProtocol.Envelope>(); var channel = decoder(values, new AtomicReference<>());
        try {
            byte[] bytes = encoded(hello);
            for (int index = 0; index < bytes.length - 1; index++) { channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{bytes[index]})); assertTrue(values.isEmpty()); }
            var last = Unpooled.wrappedBuffer(new byte[]{bytes[bytes.length - 1]}); channel.writeInbound(last); assertEquals(List.of(hello), values); assertEquals(0, last.refCnt());
            var combined = Unpooled.buffer().writeBytes(bytes).writeBytes(bytes); channel.writeInbound(combined); assertEquals(3, values.size()); assertEquals(0, combined.refCnt());
        } finally { channel.finishAndReleaseAll(); }
    }
    @Test void malformedOversizedAndPartialFramesNeverBecomeWorldMessages() {
        for (ByteBuf bytes : List.of(Unpooled.buffer().writeInt(ProtocolPipeline.MAX_BODY + 1), Unpooled.buffer().writeInt(-1),
                Unpooled.buffer().writeInt(1).writeByte(0x80))) {
            var error = new AtomicReference<Throwable>(); var values = new ArrayList<TankProtocol.Envelope>(); var channel = decoder(values, error);
            try { channel.writeInbound(bytes); assertNotNull(error.get()); assertTrue(values.isEmpty()); assertFalse(channel.isActive()); }
            finally { channel.finishAndReleaseAll(); assertEquals(0, bytes.refCnt()); }
        }
        var values = new ArrayList<TankProtocol.Envelope>(); var channel = decoder(values, new AtomicReference<>());
        var partial = Unpooled.buffer().writeInt(100).writeByte(1); channel.writeInbound(partial); channel.finishAndReleaseAll();
        assertTrue(values.isEmpty()); assertEquals(0, partial.refCnt());
    }
    @Test void stalledNetworkCannotBlockRoomAndOnlyLatestSnapshotIsRetained() {
        var captured = new ArrayList<Object>(); var promises = new ArrayList<ChannelPromise>();
        var channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            private boolean closing;
            @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) { captured.add(message); promises.add(promise); }
            @Override public void close(ChannelHandlerContext context, ChannelPromise promise) {
                if (!closing) {
                    closing = true; for (Object value : captured) { ReferenceCountUtil.release(value); }
                    context.close(promise); for (var pending : promises) { pending.tryFailure(new IllegalStateException("closed")); }
                } else { context.close(promise); }
            }
        });
        ProtocolPipeline.install(channel.pipeline()); var mailbox = new OutboundMailbox(channel, () -> { channel.close(); });
        var room = RoomFixtures.started(30, new RoomFixtures.Peer(), new RoomFixtures.Peer());
        try {
            mailbox.frame(room.latest()); channel.runPendingTasks(); assertEquals(2, captured.size());
            for (int index = 0; index < 1000; index++) { room.step(); mailbox.frame(room.latest()); }
            assertEquals(1001, room.latest().serverTick()); assertTrue(mailbox.hasLatest()); assertEquals(0, mailbox.queuedReliable());
            channel.runPendingTasks(); assertEquals(2, captured.size(), "A stalled batch must not create more writes");
        } finally { channel.finishAndReleaseAll(); mailbox.clear(); }
        for (Object value : captured) { assertEquals(0, ((ByteBuf) value).refCnt()); }
    }
    @Test void reliableOutboundOverflowClosesRatherThanGrowingOrDroppingUnreportedEvents() {
        var channel = new EmbeddedChannel(); var mailbox = new OutboundMailbox(channel, () -> { channel.close(); });
        try {
            for (int index = 0; index < 1000; index++) { mailbox.control(Wire.error(1, "fixture")); }
            assertFalse(channel.isActive()); assertTrue(mailbox.queuedReliable() <= OutboundMailbox.CAPACITY);
        } finally { channel.finishAndReleaseAll(); mailbox.clear(); }
    }
    @Test void failureOfEarlierWriteClosesEvenWhenLastWriteSucceeds() {
        var promises = new ArrayList<ChannelPromise>();
        var channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                ReferenceCountUtil.release(message); promises.add(promise);
            }
        });
        var mailbox = new OutboundMailbox(channel, () -> { channel.close(); });
        try {
            mailbox.control(Wire.error(1, "first")); mailbox.control(Wire.error(2, "last")); channel.runPendingTasks();
            assertEquals(2, promises.size()); promises.get(1).setSuccess();
            assertTrue(channel.isActive()); promises.get(0).setFailure(new IllegalStateException("earlier write failed"));
            assertFalse(channel.isActive());
        } finally { channel.finishAndReleaseAll(); mailbox.clear(); }
    }
}
