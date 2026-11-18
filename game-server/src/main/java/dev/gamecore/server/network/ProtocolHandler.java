package dev.gamecore.server.network;

import dev.gamecore.protocol.TankProtocol;
import dev.gamecore.server.ServerSettings;
import dev.gamecore.server.core.GameRoom;
import dev.gamecore.server.core.InputIntent;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** EventLoop只校验协议/凭证/标量/限流并入队；不调用session、房间step或读取live世界。 */
public final class ProtocolHandler extends SimpleChannelInboundHandler<TankProtocol.Envelope> {
    private final GameConnection connection;
    private final GameRoom room;
    private final ServerSettings settings;
    private boolean hello, joinRequested;
    private long rateEpoch = System.nanoTime();
    private int inputs;
    private long heartbeatEpoch = System.nanoTime();
    private int heartbeats;
    private ScheduledFuture<?> handshake;
    public ProtocolHandler(GameConnection connection, GameRoom room, ServerSettings settings) {
        this.connection = connection; this.room = room; this.settings = settings;
    }
    @Override public void channelActive(ChannelHandlerContext context) {
        handshake = context.executor().schedule(() -> { if (!connection.isJoined()) { connection.closeWithError(0, "JOIN_TIMEOUT"); } }, 3, TimeUnit.SECONDS);
    }
    @Override public void channelInactive(ChannelHandlerContext context) {
        if (handshake != null) { handshake.cancel(false); } connection.markDisconnected();
    }
    @Override public void channelWritabilityChanged(ChannelHandlerContext context) { connection.outbound().signal(); }
    @Override protected void channelRead0(ChannelHandlerContext context, TankProtocol.Envelope message) {
        long request = message.getRequestId();
        if (connection.disconnected()) { return; }
        if (message.getProtocolVersion() != Wire.VERSION || request <= 0 || !message.getUnknownFields().asMap().isEmpty()) {
            connection.closeWithError(request, "INVALID_ENVELOPE"); return;
        }
        if (message.hasClientHello() && !hello && message.getClientHello().getClientName().length() <= 64
                && !message.getClientHello().getClientName().isBlank() && message.getClientHello().getUnknownFields().asMap().isEmpty()) {
            hello = true;
            connection.send(Wire.envelope(request, builder -> builder.setServerHello(TankProtocol.ServerHello.newBuilder()
                    .setConnectionId(connection.identity()).setJoinCredential(connection.credential()).setServerTps(settings.tps())
                    .setSimulationTps(60).setRulesVersion("tank/1").setHeartbeatSupported(true).setPredictionSupported(true))));
        } else if (message.hasHeartbeat() && hello && message.getHeartbeat().getUnknownFields().asMap().isEmpty()) {
            long now = System.nanoTime();
            if (now - heartbeatEpoch >= 1_000_000_000L) { heartbeatEpoch = now; heartbeats = 0; }
            if (++heartbeats > 4) { connection.closeWithError(request, "RATE_LIMIT"); return; }
            connection.send(Wire.envelope(request, builder -> builder.setHeartbeat(TankProtocol.Heartbeat.getDefaultInstance())));
        } else if (message.hasJoinRoomRequest() && hello && !joinRequested) {
            var join = message.getJoinRoomRequest(); joinRequested = true;
            if (!join.getUnknownFields().asMap().isEmpty() || !join.getRoomId().equals(GameRoom.ID) || !join.getCredential().equals(connection.credential())) {
                connection.closeWithError(request, "JOIN_DENIED");
            } else if (!room.requestJoin(connection, request)) { connection.closeWithError(request, "CONTROL_QUEUE_FULL"); }
        } else if (message.hasPlayerInput() && connection.isJoined()) {
            long now = System.nanoTime();
            if (now - rateEpoch >= 1_000_000_000L) { rateEpoch = now; inputs = 0; }
            if (++inputs > settings.inputsPerSecond()) { connection.closeWithError(request, "RATE_LIMIT"); return; }
            var value = message.getPlayerInput();
            if (!value.getUnknownFields().asMap().isEmpty() || value.getMove() == TankProtocol.MoveDirection.UNRECOGNIZED
                    || value.getTurn() == TankProtocol.TurnDirection.UNRECOGNIZED) { connection.reject(request, "INVALID_INPUT"); return; }
            int move = switch (value.getMove()) { case FORWARD -> 1; case BACKWARD -> -1; default -> 0; };
            int turn = switch (value.getTurn()) { case LEFT -> 1; case RIGHT -> -1; default -> 0; };
            var intent = new InputIntent(request, value.getMatchId(), value.getSequence(), value.getTargetTick(), move, turn,
                    value.hasAimDegrees() ? value.getAimDegrees() : null, value.getFire());
            if (!intent.valid()) { connection.rejectInput(intent, "INVALID_INPUT"); }
            else if (!room.inputQueue().offer(connection, intent)) { connection.rejectInput(intent, "INPUT_QUEUE_FULL"); }
        } else { connection.closeWithError(request, "UNEXPECTED_PAYLOAD"); }
    }
    @Override public void exceptionCaught(ChannelHandlerContext context, Throwable failure) { connection.close(); }
}
