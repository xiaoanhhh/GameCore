package dev.gamecore.server.network;

import dev.gamecore.protocol.TankProtocol;
import dev.gamecore.server.core.GameRoom;
import dev.gamecore.server.core.RoomFrame;
import dev.gamecore.server.core.RoomPeer;
import io.netty.channel.Channel;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** 服务端身份绑定到Channel；凭证仅可在原连接加入一次，不接受客户端自报槽位。 */
public final class GameConnection implements RoomPeer {
    private final String identity;
    private final String credential = UUID.randomUUID().toString();
    private final Channel channel;
    private final AtomicBoolean disconnected = new AtomicBoolean();
    private final AtomicInteger pending = new AtomicInteger();
    private final OutboundMailbox outbound;
    private volatile boolean joined;
    public GameConnection(Channel channel, String identity) {
        this.channel = channel; this.identity = identity; outbound = new OutboundMailbox(channel, this::close);
    }
    @Override public String identity() { return identity; }
    public String credential() { return credential; }
    public boolean isJoined() { return joined; }
    public OutboundMailbox outbound() { return outbound; }
    @Override public boolean disconnected() { return disconnected.get() || !channel.isActive(); }
    @Override public AtomicInteger queuedInputs() { return pending; }
    public void send(TankProtocol.Envelope message) { if (!disconnected()) { outbound.control(message); } }
    @Override public void joined(long requestId, int slot, String matchId) {
        if (disconnected()) { return; } joined = true;
        send(Wire.envelope(requestId, builder -> builder.setJoinRoomResponse(TankProtocol.JoinRoomResponse.newBuilder()
                .setRoomId(GameRoom.ID).setPlayerSlot(slot).setMatchId(matchId))));
    }
    @Override public void reject(long requestId, String code) { send(Wire.error(requestId, code)); }
    @Override public void rejectInput(dev.gamecore.server.core.InputIntent input, String code) { send(Wire.inputError(input, code)); }
    @Override public void offerFrame(RoomFrame frame) { if (!disconnected()) { outbound.frame(frame); } }
    @Override public void result(RoomFrame frame) { if (!disconnected()) { outbound.result(frame); } }
    public void closeWithError(long requestId, String code) {
        if (disconnected.compareAndSet(false, true)) { outbound.control(Wire.error(requestId, code)); outbound.closeAfterFlush(); }
    }
    public void markDisconnected() { disconnected.set(true); outbound.clear(); }
    public void close() {
        disconnected.set(true); outbound.clear(); channel.close();
    }
}
