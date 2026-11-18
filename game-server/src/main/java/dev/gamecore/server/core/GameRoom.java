package dev.gamecore.server.core;

import dev.gamecore.api.session.GameSession;
import dev.gamecore.api.session.PlayerInput;
import dev.gamecore.api.session.SessionLoop;
import dev.gamecore.tank.TankBot;
import dev.gamecore.tank.TankCommand;
import dev.gamecore.tank.TankGame;
import dev.gamecore.tank.TankMap;
import dev.gamecore.tank.TankMode;
import dev.gamecore.tank.TankMovement;
import dev.gamecore.tank.TankSessionFactory;
import dev.gamecore.tank.TankSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 唯一Tank房间；只有step/abort/close的所属线程可读写live session。网络仅offer或标记连接断开。
 * 服务器默认30TPS，每步两次原60Hz规则，保留原速度/冷却/维修/胜负，不复制业务实现。
 */
public final class GameRoom {
    public static final String ID = "tank-1";
    private record Join(RoomPeer peer, long requestId) { }
    private static final class Player {
        final RoomPeer peer;
        final TreeMap<Long, InputIntent> pending = new TreeMap<>();
        long received, acceptedTarget, applied, appliedTick;
        InputIntent held;
        Player(RoomPeer peer) { this.peer = peer; }
    }
    private final java.util.function.BiFunction<TankMap, Long, GameSession<TankCommand, TankSnapshot>> sessionFactory;
    private final TankMap map;
    private final int tps;
    private final long seed;
    private final String digest;
    private final String epoch = UUID.randomUUID().toString();
    private final RoomInputQueue inputs = new RoomInputQueue();
    private final ArrayBlockingQueue<Join> joins = new ArrayBlockingQueue<>(16);
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final Player[] players = new Player[2];
    private final SnapshotPublisher publisher = new SnapshotPublisher();
    private Thread owner;
    private GameSession<TankCommand, TankSnapshot> session;
    private SessionLoop<TankCommand, TankSnapshot> loop;
    private RoomFrame.State state;
    private long tick, generation, eventId;
    private String matchId, reason = "";
    private boolean terminalPublished;
    private volatile RoomFrame latest;
    public GameRoom(TankMap map, int tps, long seed) {
        this(map, tps, seed, (arena, randomSeed) -> new TankSessionFactory().create(
            new TankSessionFactory.Config(arena, TankMode.DUEL, TankBot.Level.NORMAL), randomSeed));
    }
    /** 仅受信启动/测试选择规则适配器；客户端协议不能切换实现。 */
    public GameRoom(TankMap map, int tps, long seed,
            java.util.function.BiFunction<TankMap, Long, GameSession<TankCommand, TankSnapshot>> sessionFactory) {
        this.sessionFactory = java.util.Objects.requireNonNull(sessionFactory);
        if (tps < 10 || tps > 60 || 60 % tps != 0) { throw new IllegalArgumentException("TPS must divide 60"); }
        this.map = map; this.tps = tps; this.seed = seed; digest = MapResources.digest(map);
    }
    public boolean requestJoin(RoomPeer peer, long requestId) {
        if (!accepting.get()) { return false; }
        var join = new Join(peer, requestId);
        if (!joins.offer(join)) { return false; }
        if (!accepting.get()) { joins.remove(join); return false; }
        return true;
    }
    public RoomInputQueue inputQueue() { return inputs; }
    public RoomFrame latest() { return latest; }
    private void requireOwner() {
        if (owner == null) { owner = Thread.currentThread(); }
        if (owner != Thread.currentThread()) { throw new IllegalStateException("Room owner violated"); }
    }
    private void fresh() {
        closeSession();
        session = sessionFactory.apply(map, seed);
        loop = new SessionLoop<>(session); state = RoomFrame.State.WAITING; tick = 0; eventId = 0;
        matchId = epoch + "/" + ++generation; reason = ""; terminalPublished = false;
    }
    public void step() {
        requireOwner(); if (session == null) { fresh(); }
        boolean removed = false;
        for (int slot = 0; slot < 2; slot++) {
            if (players[slot] != null && players[slot].peer.disconnected()) { players[slot] = null; removed = true; }
        }
        if (removed && state == RoomFrame.State.RUNNING) { stop("DISCONNECTED"); }
        // 空房间也可能因调度过载被取消；没有离线事件时仍须能重新接纳下一局。
        if ((removed || state == RoomFrame.State.ABORTED || state == RoomFrame.State.FINISHED)
                && players[0] == null && players[1] == null) { fresh(); }
        int joinBoundary = joins.size();
        for (int index = 0; index < joinBoundary; index++) {
            var join = joins.poll(); if (join == null) { break; }
            if (join.peer().disconnected()) { continue; }
            if (slotOf(join.peer()) >= 0) { join.peer().reject(join.requestId(), "ALREADY_JOINED"); continue; }
            int slot = players[0] == null ? 0 : players[1] == null ? 1 : -1;
            if (slot < 0 || state != RoomFrame.State.WAITING) { join.peer().reject(join.requestId(), "ROOM_FULL_OR_CLOSED"); continue; }
            players[slot] = new Player(join.peer()); join.peer().joined(join.requestId(), slot, matchId);
        }
        if (state == RoomFrame.State.WAITING && players[0] != null && players[1] != null) { session.start(); state = RoomFrame.State.RUNNING; }
        for (var entry : inputs.drain()) { accept(entry); }
        List<RoomFrame.Event> events = new ArrayList<>();
        if (state == RoomFrame.State.RUNNING) {
            tick++;
            for (var player : players) {
                var command = player.pending.remove(tick);
                if (command != null) { player.held = command; player.appliedTick = tick; }
                if (tick - player.appliedTick >= Math.max(1, tps / 2)) { player.held = null; }
            }
            for (int substep = 0; substep < TankGame.TICKS_PER_SECOND / tps; substep++) {
                var before = session.snapshot();
                for (int slot = 0; slot < 2; slot++) {
                    var held = players[slot].held; TankCommand command = TankCommand.IDLE;
                    if (held != null) {
                        command = TankMovement.command(held.move(), held.turn(), held.fire(), held.aimDegrees(), before.tanks().get(slot).heading());
                    }
                    if (!session.submitInput(new PlayerInput<>(session.tickNumber() + 1, slot, slot, command))) {
                        throw new IllegalStateException("Canonical input rejected");
                    }
                }
                if (!loop.tick().isEmpty()) { throw new IllegalStateException("Unexpected observer failure"); }
                // 至少一个规则子步确实成功后才确认；接纳/排队不等于执行。
                for (var player : players) { if (player.held != null) { player.applied = player.held.sequence(); } }
                var after = session.snapshot();
                for (var event : after.events()) { events.add(new RoomFrame.Event(++eventId, tick, after.tick(), event)); }
                if (session.isFinished()) { state = RoomFrame.State.FINISHED; reason = "RULES_FINISHED"; break; }
            }
        }
        publish(events);
    }
    private int slotOf(RoomPeer peer) {
        for (int slot = 0; slot < 2; slot++) { if (players[slot] != null && players[slot].peer == peer) { return slot; } }
        return -1;
    }
    private void accept(RoomInputQueue.Entry entry) {
        if (entry.peer().disconnected()) { return; }
        var input = entry.input(); int slot = slotOf(entry.peer()); String error = null;
        if (!input.valid()) { error = "INVALID_INPUT"; }
        else if (slot < 0 || state != RoomFrame.State.RUNNING) { error = "NOT_RUNNING"; }
        else if (!matchId.equals(input.matchId())) { error = "STALE_MATCH"; }
        else {
            var player = players[slot];
            if (input.sequence() <= player.received) { error = "DUPLICATE_SEQUENCE"; }
            else if (input.targetTick() <= tick) { error = "LATE_INPUT"; }
            else if (input.targetTick() - tick > Math.max(2, tps / 2)) { error = "EARLY_INPUT"; }
            else if (input.targetTick() < player.acceptedTarget) { error = "TARGET_ORDER"; }
            else if (!player.pending.containsKey(input.targetTick()) && player.pending.size() >= 32) { error = "INPUT_QUEUE_FULL"; }
            else {
                player.pending.put(input.targetTick(), input); player.received = input.sequence(); player.acceptedTarget = input.targetTick();
            }
        }
        if (error != null) { entry.peer().rejectInput(input, error); }
    }
    private void stop(String cause) {
        session.pause(); state = RoomFrame.State.ABORTED; reason = cause;
        for (var player : players) { if (player != null) { player.pending.clear(); player.held = null; } }
    }
    public void abort(String cause) {
        requireOwner(); if (session == null) { fresh(); }
        if (state == RoomFrame.State.WAITING || state == RoomFrame.State.RUNNING) { stop(cause); publish(List.of()); }
    }
    private void closeSession() {
        if (session instanceof AutoCloseable resource) {
            try { resource.close(); } catch (Exception failure) { throw new IllegalStateException("Session cleanup failed", failure); }
        }
    }
    public void close() {
        requireOwner(); accepting.set(false); abort("SERVER_STOPPED"); joins.clear(); inputs.close(); Arrays.fill(players, null);
        publish(List.of()); closeSession();
    }
    private void publish(List<RoomFrame.Event> events) {
        var frame = new RoomFrame(matchId, tick, tps, state, session.snapshot(),
                List.of(players[0] == null ? 0L : players[0].applied, players[1] == null ? 0L : players[1].applied),
                List.of(players[0] != null, players[1] != null), events, digest, reason, List.of(inputStatus(0), inputStatus(1)));
        latest = frame;
        boolean terminal = !terminalPublished && (state == RoomFrame.State.FINISHED || state == RoomFrame.State.ABORTED);
        if (terminal) { terminalPublished = true; }
        List<RoomPeer> peers = new ArrayList<>(); for (var player : players) { if (player != null) { peers.add(player.peer); } }
        publisher.publish(frame, peers, terminal);
    }
    private RoomFrame.InputStatus inputStatus(int slot) {
        var player = players[slot];
        return player == null ? new RoomFrame.InputStatus(0, 0, null, 0)
                : new RoomFrame.InputStatus(player.received, player.appliedTick, player.held,
                        player.applied == 0 ? 0 : player.appliedTick + Math.max(1, tps / 2));
    }
}
