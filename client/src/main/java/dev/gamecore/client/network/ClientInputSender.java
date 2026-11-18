package dev.gamecore.client.network;

import dev.gamecore.protocol.TankProtocol;
import dev.gamecore.tank.TankCommand;

/** 完整意图20Hz保活、变化优先且硬限制50Hz；目标Tick使用显式服务器估计/预留，不追发停顿积压。 */
public final class ClientInputSender {
    private long sequence, lastTarget, nextSend;
    private boolean initialized;
    private long lastSend;
    private TankCommand lastCommand = TankCommand.IDLE;
    public long sequence() { return sequence; }
    public void send(GameNetworkClient network, String matchId, long tick, int tps, TankCommand command, long now, boolean force) {
        var value = sample(matchId, tick, tps, command, now, force); if (value != null) { network.sendInput(value); }
    }
    TankProtocol.PlayerInput sample(String matchId, long tick, int tps, TankCommand command, long now, boolean force) {
        return sample(matchId, tick, tps, Math.max(2, tps / 10), command, now, force);
    }
    TankProtocol.PlayerInput sample(String matchId, long tick, int tps, int lead, TankCommand command, long now, boolean force) {
        if (initialized && (now - lastSend < 20_000_000L || !force && command.equals(lastCommand) && now - nextSend < 0)) { return null; }
        long target = Math.max(lastTarget, tick + Math.min(Math.max(2, tps / 2), lead));
        if (sequence == Long.MAX_VALUE) { throw new IllegalStateException("输入序号已用尽，请重新连接"); }
        var value = TankProtocol.PlayerInput.newBuilder().setMatchId(matchId).setSequence(++sequence).setTargetTick(target)
                .setMove(command.throttle() > 0 ? TankProtocol.MoveDirection.FORWARD : command.throttle() < 0 ? TankProtocol.MoveDirection.BACKWARD : TankProtocol.MoveDirection.MOVE_IDLE)
                .setTurn(command.turn() > 0 ? TankProtocol.TurnDirection.LEFT : command.turn() < 0 ? TankProtocol.TurnDirection.RIGHT : TankProtocol.TurnDirection.TURN_IDLE)
                .setFire(command.fire()).build();
        lastTarget = target; nextSend = now + 50_000_000L; lastSend = now; lastCommand = command; initialized = true; return value;
    }
}
