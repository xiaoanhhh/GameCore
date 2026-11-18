package dev.gamecore.network.experiment;

import dev.gamecore.network.transport.ProtocolMessages;
import dev.gamecore.protocol.TankProtocol;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 虚拟时间不等待；同种子同事件表必须得到逐项相同的到达/丢弃记录。 */
class SimulatedLinkTest {
    @Test void seededDelayJitterAndDropAreRepeatableAndKeepFifo() {
        var profile = new NetworkProfile(100, 50, 10, 73, 0, 0);
        var a = new SimulatedLink<Integer>(profile, 1); var b = new SimulatedLink<Integer>(profile, 1);
        var delivered = new ArrayList<Integer>();
        for (int index = 0; index < 500; index++) {
            long now = index * 10_000_000L;
            assertEquals(a.offer(index, now, true, "input"), b.offer(index, now, true, "input"));
            var first = a.drain(now); assertEquals(first, b.drain(now)); delivered.addAll(first);
        }
        delivered.addAll(a.drain(10_000_000_000L)); b.drain(10_000_000_000L);
        assertEquals(a.trace(), b.trace()); assertTrue(a.metrics().dropped() > 0);
        assertEquals(delivered.stream().sorted().toList(), delivered);
    }
    @Test void capacitiesAndTemporaryBlackoutHaveExplicitRecovery() {
        var full = new SimulatedLink<Integer>(new NetworkProfile(2000, 0, 0, 1, 0, 0), 0);
        for (int index = 0; index < 128; index++) { assertEquals(SimulatedLink.Admission.QUEUED, full.offer(index, 0, true, "world")); }
        assertEquals(SimulatedLink.Admission.FULL, full.offer(129, 0, true, "world")); assertEquals(128, full.metrics().queued());
        assertEquals(128, full.clear().size()); assertEquals(0, full.metrics().queued());
        var blackout = new SimulatedLink<Integer>(new NetworkProfile(0, 0, 0, 1, 100, 200), 0);
        blackout.offer(1, 0, true, "input"); assertEquals(List.of(1), blackout.drain(0));
        assertEquals(SimulatedLink.Admission.DROPPED, blackout.offer(2, 100_000_000L, false, "heartbeat"));
        blackout.offer(3, 300_000_000L, true, "input"); assertEquals(List.of(3), blackout.drain(300_000_000L));
        assertEquals("BLACKOUT", blackout.trace().stream().filter(trace -> trace.id() == 2).findFirst().orElseThrow().action());
    }
    @Test void controlMessagesAreNotRandomlyDiscardedAndTraceHasBound() {
        var link = new SimulatedLink<Integer>(new NetworkProfile(0, 0, 50, 2, 0, 0), 0);
        for (int index = 0; index < 3000; index++) {
            assertEquals(SimulatedLink.Admission.QUEUED, link.offer(index, index, false, "result")); assertEquals(List.of(index), link.drain(index));
        }
        assertEquals(0, link.metrics().dropped()); assertEquals(SimulatedLink.TRACE_CAPACITY, link.trace().size());
    }
    @Test void nettyAdapterDelaysImmutableEnvelopesAndCompletesPendingWritesWhenClosed() {
        var clock = new AtomicLong(); var handler = new SimulatedNetworkHandler(new NetworkProfile(100, 0, 0, 3, 0, 0), clock::get);
        var channel = new EmbeddedChannel(handler);
        try {
            var value = ProtocolMessages.envelope(1, builder -> builder.setHeartbeat(TankProtocol.Heartbeat.getDefaultInstance()));
            var write = channel.writeAndFlush(value); assertFalse(write.isDone());
            clock.set(50_000_000L); channel.advanceTimeBy(50, TimeUnit.MILLISECONDS); channel.runScheduledPendingTasks();
            assertTrue(write.isSuccess()); assertEquals(value, channel.readOutbound());
            var pending = channel.writeAndFlush(value); assertFalse(pending.isDone()); channel.close();
            assertTrue(pending.isDone()); assertFalse(pending.isSuccess()); assertEquals(0, handler.outboundMetrics().queued());
        } finally { channel.finishAndReleaseAll(); }
    }
}
