package dev.gamecore.network.transport;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.MessageLite;
import dev.gamecore.protocol.GameProtocol;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 旁路只读：消息原样透传，观察方失败不能影响连接。 */
class TrafficTapHandlerTest {
    private static GameProtocol.Envelope message(long id) {
        return GameProtocol.Envelope.newBuilder().setProtocolVersion(2).setRequestId(id)
                .setHeartbeat(GameProtocol.Heartbeat.getDefaultInstance()).build();
    }
    private static class Recording implements TrafficObserver {
        final List<String> calls = new ArrayList<>();
        @Override public void sent(MessageLite message) { calls.add("sent"); }
        @Override public void received(MessageLite message) { calls.add("received"); }
        @Override public void connection(String state) { calls.add(state); }
    }

    @Test void bothDirectionsReachTheObserverAndContinueUnchanged() {
        var observer = new Recording();
        var channel = new EmbeddedChannel(new TrafficTapHandler(observer));
        var inbound = message(1);
        var outbound = message(2);
        channel.writeInbound(inbound);
        channel.writeOutbound(outbound);
        assertSame(inbound, channel.readInbound());
        assertSame(outbound, channel.readOutbound());
        channel.close();
        assertEquals(List.of("TCP 已连接", "received", "sent", "TCP 已断开"), observer.calls);
    }

    @Test void observerFailureDoesNotBreakTheConnectionOrItsOrder() {
        var channel = new EmbeddedChannel(new TrafficTapHandler(new TrafficObserver() {
            @Override public void sent(MessageLite message) { throw new IllegalStateException("sent"); }
            @Override public void received(MessageLite message) { throw new IllegalStateException("received"); }
            @Override public void connection(String state) { throw new IllegalStateException("state"); }
        }));
        var first = message(1);
        var second = message(2);
        channel.writeInbound(first, second);
        channel.writeOutbound(first);
        assertSame(first, channel.readInbound());
        assertSame(second, channel.readInbound());
        assertSame(first, channel.readOutbound());
        assertTrue(channel.isActive());
        channel.checkException();
    }

    @Test void nonProtobufFramesPassThroughWithoutReachingTheObserver() {
        var observer = new Recording();
        var channel = new EmbeddedChannel(new TrafficTapHandler(observer));
        observer.calls.clear();
        channel.writeInbound("frame");
        assertEquals("frame", channel.readInbound());
        assertTrue(observer.calls.isEmpty());
    }

    @Test void reportIgnoresMissingObserverAndContainsFailures() {
        assertDoesNotThrow(() -> TrafficTapHandler.report(null, "state"));
        assertDoesNotThrow(() -> TrafficTapHandler.report(new Recording() {
            @Override public void connection(String state) { throw new IllegalStateException("state"); }
        }, "state"));
    }
}
