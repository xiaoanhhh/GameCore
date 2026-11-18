package dev.gamecore.server.network;

import dev.gamecore.protocol.TankProtocol;
import dev.gamecore.network.transport.ProtocolMessages;
import dev.gamecore.server.core.GameRoom;
import dev.gamecore.server.core.RoomFrame;
import java.util.function.Consumer;

/** 显式领域→Protobuf只读映射；快照映射由出站EventLoop调用，不把生成Builder作为共享世界。 */
public final class Wire {
    public static final int VERSION = ProtocolMessages.VERSION;
    private Wire() { }
    public static TankProtocol.Envelope envelope(long requestId, Consumer<TankProtocol.Envelope.Builder> payload) {
        return ProtocolMessages.envelope(requestId, payload);
    }
    public static TankProtocol.Envelope error(long requestId, String code) {
        return envelope(requestId, builder -> builder.setError(TankProtocol.ErrorMessage.newBuilder().setCode(code).setDescription(code)));
    }
    public static TankProtocol.Envelope inputError(dev.gamecore.server.core.InputIntent input, String code) {
        return envelope(input.requestId(), builder -> builder.setError(TankProtocol.ErrorMessage.newBuilder().setCode(code).setDescription(code)
                .setInputSequence(input.sequence()).setTargetTick(input.targetTick())));
    }
    public static TankProtocol.WorldSnapshot snapshot(RoomFrame frame) {
        var builder = TankProtocol.WorldSnapshot.newBuilder().setRoomId(GameRoom.ID).setMatchId(frame.matchId())
                .setServerTick(frame.serverTick()).setSimulationTick(frame.world().tick()).setServerTps(frame.tps())
                .setState(TankProtocol.RoomState.valueOf(frame.state().name())).setMapId(frame.world().map().id()).setMapDigest(frame.mapDigest());
        for (var player : frame.world().tanks()) {
            builder.addPlayers(TankProtocol.PlayerState.newBuilder().setPlayerSlot(player.id()).setX(player.position().x()).setY(player.position().y())
                    .setHeading(player.heading()).setHealth(player.health()).setVelocityX(player.velocity().x()).setVelocityY(player.velocity().y())
                    .setCooldown(player.cooldown()).setConnected(frame.connected().get(player.id())));
        }
        for (int slot = 0; slot < 2; slot++) {
            var status = frame.inputStatus().get(slot);
            var ack = TankProtocol.InputAck.newBuilder().setPlayerSlot(slot).setSequence(frame.acknowledgements().get(slot))
                    .setReceivedSequence(status.received()).setAppliedTick(status.appliedTick()).setExpiresTick(status.expiresTick());
            var active = status.active();
            if (active != null) {
                var input = TankProtocol.PlayerInput.newBuilder().setMatchId(active.matchId()).setSequence(active.sequence()).setTargetTick(active.targetTick())
                        .setMove(active.move() > 0 ? TankProtocol.MoveDirection.FORWARD : active.move() < 0 ? TankProtocol.MoveDirection.BACKWARD : TankProtocol.MoveDirection.MOVE_IDLE)
                        .setTurn(active.turn() > 0 ? TankProtocol.TurnDirection.LEFT : active.turn() < 0 ? TankProtocol.TurnDirection.RIGHT : TankProtocol.TurnDirection.TURN_IDLE).setFire(active.fire());
                if (active.aimDegrees() != null) { input.setAimDegrees(active.aimDegrees()); }
                ack.setActiveInput(input);
            }
            builder.addInputAcks(ack);
        }
        for (var bullet : frame.world().bullets()) {
            builder.addBullets(TankProtocol.BulletState.newBuilder().setId(bullet.id()).setOwner(bullet.owner()).setX(bullet.position().x()).setY(bullet.position().y())
                    .setVelocityX(bullet.velocity().x()).setVelocityY(bullet.velocity().y()).setRemainingTicks(bullet.remainingTicks()));
        }
        return builder.build();
    }
    public static TankProtocol.Envelope event(String matchId, RoomFrame.Event event) {
        var value = event.value();
        return envelope(0, builder -> builder.setGameEvent(TankProtocol.GameEvent.newBuilder().setMatchId(matchId).setEventId(event.id())
                .setServerTick(event.serverTick()).setSimulationTick(event.simulationTick()).setKind(value.type().name())
                .setActor(value.actor()).setTarget(value.target()).setX(value.position().x()).setY(value.position().y())));
    }
    public static TankProtocol.Envelope result(RoomFrame frame) {
        var outcome = frame.state() == RoomFrame.State.ABORTED ? TankProtocol.Outcome.CANCELLED : switch (frame.world().result()) {
            case PLAYER_ONE -> TankProtocol.Outcome.PLAYER_ONE; case PLAYER_TWO -> TankProtocol.Outcome.PLAYER_TWO;
            case DRAW -> TankProtocol.Outcome.DRAW; case NONE -> TankProtocol.Outcome.NO_RESULT;
        };
        return envelope(0, builder -> builder.setMatchResult(TankProtocol.MatchResult.newBuilder().setOutcome(outcome).setReason(frame.reason()).setFinalSnapshot(snapshot(frame))));
    }
}
