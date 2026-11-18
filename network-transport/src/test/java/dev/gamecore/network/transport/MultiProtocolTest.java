package dev.gamecore.network.transport;
import dev.gamecore.protocol.GameProtocol;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MultiProtocolTest {
    @Test void newJoinedCannotBeFollowedByAnUnsentOldSnapshot(){
        var c=new EmbeddedChannel();var out=new AsyncOutbound<GameProtocol.Envelope>(c,()->fail("Unexpected outbound failure"));
        try{
            var old=GameProtocol.Envelope.newBuilder().setSnapshot(GameProtocol.Snapshot.newBuilder().setMatchId("old")).build();
            var joined=GameProtocol.Envelope.newBuilder().setJoined(GameProtocol.Joined.newBuilder().setMatchId("new")).build();
            out.snapshot(old);out.clearSnapshot();out.reliable(joined);c.runPendingTasks();
            assertEquals(joined,c.readOutbound());assertNull(c.readOutbound());
        }finally{out.clear();c.finishAndReleaseAll();}
    }
    static EmbeddedChannel channel(){var c=new EmbeddedChannel();ProtocolPipeline.install(c.pipeline(),GameProtocol.Envelope.parser(),65536);return c;}
    static byte[] frame(GameProtocol.Envelope e){var b=Unpooled.buffer().writeInt(e.getSerializedSize()).writeBytes(e.toByteArray());try{var bytes=new byte[b.readableBytes()];b.readBytes(bytes);return bytes;}finally{b.release();}}
    static GameProtocol.Envelope message(){return GameProtocol.Envelope.newBuilder().setProtocolVersion(2).setRequestId(1).setHello(GameProtocol.Hello.newBuilder().setClientName("split")).build();}
    @Test void fragmentedAndCoalescedFramesKeepBoundariesAndReleaseBuffers(){var c=channel();try{
        var bytes=frame(message());for(int i=0;i<bytes.length-1;i++){assertFalse(c.writeInbound(Unpooled.wrappedBuffer(new byte[]{bytes[i]})));}assertTrue(c.writeInbound(Unpooled.wrappedBuffer(new byte[]{bytes[bytes.length-1]})));assertEquals(message(),c.readInbound());
        var combined=Unpooled.buffer().writeBytes(bytes).writeBytes(bytes);assertTrue(c.writeInbound(combined));assertEquals(0,combined.refCnt());assertEquals(message(),c.readInbound());assertEquals(message(),c.readInbound());assertNull(c.readInbound());
    }finally{c.finishAndReleaseAll();}}
    @Test void incompleteDoesNotEmitAndOversizeFails(){var c=channel();try{assertFalse(c.writeInbound(Unpooled.buffer().writeInt(30).writeByte(1)));assertNull(c.readInbound());}finally{c.finishAndReleaseAll();}var oversized=channel();try{assertThrows(io.netty.handler.codec.TooLongFrameException.class,()->oversized.writeInbound(Unpooled.buffer().writeInt(65537)));}finally{oversized.finishAndReleaseAll();}}
    @Test void malformedProtobufReleasesByteBuf(){var c=channel();var b=Unpooled.buffer().writeInt(1).writeByte(255);try{assertThrows(io.netty.handler.codec.DecoderException.class,()->c.writeInbound(b));assertEquals(0,b.refCnt());}finally{c.finishAndReleaseAll();}}
    @Test void blockedWriteKeepsBoundedMailboxAndCoalescesSnapshots(){var pending=new java.util.ArrayList<io.netty.channel.ChannelPromise>();var fatal=new java.util.concurrent.atomic.AtomicInteger();var c=new EmbeddedChannel(new io.netty.channel.ChannelOutboundHandlerAdapter(){
        @Override public void write(io.netty.channel.ChannelHandlerContext ctx,Object message,io.netty.channel.ChannelPromise promise){pending.add(promise);}
    });var out=new AsyncOutbound<GameProtocol.Envelope>(c,fatal::incrementAndGet);try{
        out.reliable(message());c.runPendingTasks();assertEquals(1,pending.size());for(int i=0;i<1000;i++){out.snapshot(message());}for(int i=0;i<128;i++){out.reliable(message());}out.reliable(message());c.runPendingTasks();assertEquals(128,out.queued());assertEquals(1,pending.size());assertEquals(1,fatal.get());
        out.clear();for(var p:pending){p.trySuccess();}c.runPendingTasks();assertEquals(0,out.queued());
    }finally{out.clear();c.finishAndReleaseAll();}}
}
