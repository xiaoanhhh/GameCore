package dev.gamecore.client.network;

import dev.gamecore.protocol.TankProtocol;
import dev.gamecore.tank.TankGame;
import dev.gamecore.tank.TankMap;
import dev.gamecore.tank.TankMapFingerprint;
import dev.gamecore.tank.TankSnapshot;
import dev.gamecore.tank.TmxArenaLoader;
import dev.gamecore.tank.Vec2;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

/** 渲染消费者校验完整快照后生成稳定TankSnapshot，复用画板；不创建/推进TankGame。 */
public final class SnapshotReceiver {
    private final Function<String, TankMap> maps;
    private TankMap map;
    private String mapDigest;
    private String match;
    private int tps;
    private boolean predictionSupported;
    private long serverTick = -1, simulationTick = -1;
    private final long[] acknowledgements = new long[2];
    private final long[] receivedSequences = new long[2];
    public SnapshotReceiver() { this(SnapshotReceiver::loadMap); }
    SnapshotReceiver(Function<String, TankMap> maps) { this.maps = maps; }
    public void configure(String matchId, int serverTps) { match = matchId; tps = serverTps; }
    public void configure(String matchId, int serverTps, boolean prediction) { configure(matchId, serverTps); predictionSupported = prediction; }
    public long serverTick() { return serverTick; }
    public int tps() { return tps; }
    public long acknowledgement(int slot) { return acknowledgements[slot]; }
    public long receivedSequence(int slot) { return receivedSequences[slot]; }
    public TankSnapshot receive(TankProtocol.WorldSnapshot value, TankGame.Result result) {
        check(match != null && value.getRoomId().equals("tank-1") && value.getMatchId().equals(match));
        check(value.getServerTps() == tps && tps >= 10 && tps <= 60 && 60 % tps == 0);
        check(value.getState() != TankProtocol.RoomState.UNRECOGNIZED);
        check(value.getServerTick() >= serverTick && value.getServerTick() >= 0 && value.getServerTick() <= TankGame.MATCH_TICKS);
        check(value.getSimulationTick() >= simulationTick && value.getSimulationTick() >= 0 && value.getSimulationTick() <= TankGame.MATCH_TICKS);
        check(value.getSimulationTick() <= value.getServerTick() * (60 / tps));
        check(value.getState() != TankProtocol.RoomState.WAITING || value.getSimulationTick() == 0);
        if (map == null) {
            map = maps.apply(value.getMapId());
            mapDigest = map == null ? "" : TankMapFingerprint.digest(map);
            if (map == null || !map.id().equals(value.getMapId()) || !mapDigest.equals(value.getMapDigest())) {
                map = null; throw new IllegalArgumentException("地图版本不一致，请更新客户端和服务器");
            }
        } else { check(value.getMapId().equals(map.id()) && value.getMapDigest().equals(mapDigest)); }
        check(value.getPlayersCount() == 2 && value.getInputAcksCount() == 2 && value.getBulletsCount() <= 128);
        var tanks = new ArrayList<TankSnapshot.Tank>(2);
        for (int slot = 0; slot < 2; slot++) {
            var tank = value.getPlayers(slot); var ack = value.getInputAcks(slot);
            check(tank.getPlayerSlot() == slot && ack.getPlayerSlot() == slot && ack.getSequence() >= 0
                    && (ack.getSequence() >= acknowledgements[slot] || !tank.getConnected() && ack.getSequence() == 0));
            if (predictionSupported) {
                validateInputStatus(ack, value);
                check(ack.getReceivedSequence() >= receivedSequences[slot] || !tank.getConnected() && ack.getReceivedSequence() == 0);
            }
            check(tank.getHealth() >= 0 && tank.getHealth() <= TankGame.MAX_HEALTH && tank.getCooldown() >= 0 && tank.getCooldown() <= TankGame.FIRE_COOLDOWN);
            check(Double.isFinite(tank.getHeading()) && tank.getHeading() >= 0 && tank.getHeading() < 360);
            position(tank.getX(), tank.getY()); velocity(tank.getVelocityX(), tank.getVelocityY(), TankGame.MOVE_SPEED);
            tanks.add(new TankSnapshot.Tank(slot, new Vec2(tank.getX(), tank.getY()), tank.getHeading(),
                    new Vec2(tank.getVelocityX(), tank.getVelocityY()), tank.getHealth(), tank.getCooldown()));
        }
        var bullets = new ArrayList<TankSnapshot.Bullet>(); var ids = new HashSet<Long>();
        for (var bullet : value.getBulletsList()) {
            check(bullet.getId() >= 0 && ids.add(bullet.getId()) && bullet.getOwner() >= 0 && bullet.getOwner() < 2);
            check(bullet.getRemainingTicks() > 0 && bullet.getRemainingTicks() <= TankGame.BULLET_LIFETIME);
            position(bullet.getX(), bullet.getY()); velocity(bullet.getVelocityX(), bullet.getVelocityY(), TankGame.BULLET_SPEED);
            bullets.add(new TankSnapshot.Bullet(bullet.getId(), bullet.getOwner(), new Vec2(bullet.getX(), bullet.getY()),
                    new Vec2(bullet.getVelocityX(), bullet.getVelocityY()), bullet.getRemainingTicks()));
        }
        for (int slot = 0; slot < 2; slot++) {
            acknowledgements[slot] = value.getInputAcks(slot).getSequence(); receivedSequences[slot] = value.getInputAcks(slot).getReceivedSequence();
        }
        serverTick = value.getServerTick(); simulationTick = value.getSimulationTick();
        var state = switch (value.getState()) {
            case WAITING -> TankGame.State.READY; case RUNNING -> TankGame.State.RUNNING;
            case FINISHED -> TankGame.State.FINISHED; case ABORTED -> TankGame.State.PAUSED;
            default -> throw new IllegalArgumentException("服务器比赛状态异常");
        };
        return new TankSnapshot(map, simulationTick, state, result, tanks, bullets, List.of());
    }
    public void event(TankProtocol.GameEvent value) {
        check(match != null && value.getMatchId().equals(match) && value.getEventId() > 0 && value.getServerTick() >= 0
                && value.getServerTick() <= TankGame.MATCH_TICKS && value.getSimulationTick() >= 0 && value.getSimulationTick() <= TankGame.MATCH_TICKS);
        check(value.getActor() >= -1 && value.getActor() < 2 && value.getTarget() >= -1 && value.getTarget() < 2);
        check(Double.isFinite(value.getX()) && Double.isFinite(value.getY()) && Math.abs(value.getX()) <= 128 && Math.abs(value.getY()) <= 128);
        check(java.util.Arrays.stream(TankSnapshot.EventType.values()).anyMatch(type -> type.name().equals(value.getKind())));
    }
    private static void validateInputStatus(TankProtocol.InputAck ack, TankProtocol.WorldSnapshot world) {
        check(ack.getReceivedSequence() >= ack.getSequence() && ack.getAppliedTick() >= 0 && ack.getAppliedTick() <= world.getServerTick());
        check(ack.getExpiresTick() >= 0 && (ack.getSequence() == 0 ? ack.getExpiresTick() == 0
                : ack.getAppliedTick() > 0 && ack.getExpiresTick() == ack.getAppliedTick() + Math.max(1, world.getServerTps() / 2)));
        if (ack.hasActiveInput()) {
            var input = ack.getActiveInput();
            check(input.getSequence() == ack.getSequence() && input.getSequence() > 0 && input.getTargetTick() == ack.getAppliedTick()
                    && input.getMatchId().equals(world.getMatchId()) && ack.getExpiresTick() > world.getServerTick() && input.getUnknownFields().asMap().isEmpty());
            check(input.getMove() != TankProtocol.MoveDirection.UNRECOGNIZED && input.getTurn() != TankProtocol.TurnDirection.UNRECOGNIZED);
            check(!input.hasAimDegrees() || Double.isFinite(input.getAimDegrees()) && input.getAimDegrees() >= 0 && input.getAimDegrees() < 360
                    && input.getTurn() == TankProtocol.TurnDirection.TURN_IDLE);
        }
    }
    private void position(double x, double y) { check(Double.isFinite(x) && Double.isFinite(y) && x >= 0 && x <= map.width() && y >= 0 && y <= map.height()); }
    private static void velocity(double x, double y, double maximum) { check(Double.isFinite(x) && Double.isFinite(y) && Math.hypot(x, y) <= maximum + 1e-6); }
    private static void check(boolean valid) { if (!valid) { throw new IllegalArgumentException("服务器状态数据异常，已安全断开"); } }
    private static TankMap loadMap(String id) {
        if (!(id.equals("crossfire") || id.equals("ruins"))) { throw new IllegalArgumentException("地图版本不一致，请更新客户端和服务器"); }
        try (var input = SnapshotReceiver.class.getResourceAsStream("/maps/tank/" + id + ".tmx")) {
            if (input == null) { throw new IOException("Missing map"); } return TmxArenaLoader.load(input);
        } catch (IOException failure) { throw new IllegalArgumentException("无法读取本地地图，请检查客户端安装", failure); }
    }
}
