package dev.gamecore.client.network;

import dev.gamecore.protocol.TankProtocol;
import dev.gamecore.tank.TankCommand;
import dev.gamecore.tank.TankGame;
import dev.gamecore.tank.TankSnapshot;
import dev.gamecore.client.network.sync.ClientPrediction;
import dev.gamecore.client.network.sync.SnapshotInterpolator;
import dev.gamecore.network.experiment.NetworkProfile;

/**
 * 渲染线程拥有的远程展示会话；刻意不实现带start/pause/tick写世界语义的GameSession。
 * update消费有界网络消息并采样输入；离页close只异步关闭网络，迟到回调不会访问页面。
 */
public final class RemoteGameSession implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private final NetworkEventDispatcher dispatcher;
    private final GameNetworkClient network;
    private final SnapshotReceiver receiver;
    private final ClientInputSender sender = new ClientInputSender();
    private final ClientPrediction prediction = new ClientPrediction();
    private final SnapshotInterpolator interpolation = new SnapshotInterpolator();
    private boolean predictionRequested = true, predictionSupported;
    private long staleSnapshots;
    private ConnectionState state = ConnectionState.NEW;
    private String message = "输入服务器地址，开始在线双人游戏", match;
    private int tps, slot = -1;
    private long eventId;
    private TankSnapshot snapshot;
    private TankSnapshot authority;
    private TankProtocol.MatchResult result;
    public RemoteGameSession() { this(new SnapshotReceiver()); }
    public RemoteGameSession(NetworkProfile profile) { this(profile, null); }
    public RemoteGameSession(NetworkProfile profile, dev.gamecore.network.transport.TrafficObserver traffic) {
        this(new SnapshotReceiver(), profile, Boolean.getBoolean("gamecore.network.dev"), traffic);
    }
    RemoteGameSession(SnapshotReceiver receiver) {
        this(receiver, NetworkProfile.NONE, Boolean.getBoolean("gamecore.network.dev"));
    }
    RemoteGameSession(SnapshotReceiver receiver, NetworkProfile profile, boolean development) {
        this(receiver, profile, development, null);
    }
    RemoteGameSession(SnapshotReceiver receiver, NetworkProfile profile, boolean development, dev.gamecore.network.transport.TrafficObserver traffic) {
        this.receiver = receiver; dispatcher = new NetworkEventDispatcher(); network = new GameNetworkClient(dispatcher, profile, development, traffic);
        prediction.enabled(false);
    }
    public void connect(ServerEndpoint endpoint, String name) { requireOwner(); state = ConnectionState.CONNECTING; message = "正在连接服务器"; network.connect(endpoint, name); }
    public ConnectionState state() { return state; }
    public String message() { return message; }
    public String matchId() { return match; }
    public int slot() { return slot; }
    public TankSnapshot snapshot() { return snapshot; }
    public TankSnapshot authoritativeSnapshot() { return authority; }
    public boolean predictionSupported() { return predictionSupported; }
    public boolean predictionEnabled() { return predictionRequested && predictionSupported; }
    public void predictionEnabled(boolean value) { requireOwner(); predictionRequested = value; prediction.enabled(value && predictionSupported); }
    public boolean interpolationEnabled() { return interpolation.enabled(); }
    public void interpolationEnabled(boolean value) { requireOwner(); interpolation.enabled(value); }
    public void interpolationBufferMillis(int value) { requireOwner(); interpolation.bufferMillis(value); }
    public int interpolationBufferMillis() { return interpolation.bufferMillis(); }
    public ClientPrediction.Metrics predictionMetrics() { return prediction.metrics(); }
    public long staleSnapshots() { return staleSnapshots + dispatcher.staleSnapshots(); }
    public TankProtocol.MatchResult result() { return result; }
    public GameNetworkClient network() { return network; }
    public long serverTick() { return receiver.serverTick(); }
    public long acknowledgedSequence() { return slot < 0 ? 0 : receiver.acknowledgement(slot); }
    public long eventId() { return eventId; }
    public void update(TankCommand command, long now) {
        requireOwner(); if (state == ConnectionState.CLOSED) { return; }
        for (var event : dispatcher.drain()) {
            if (state.terminal()) { break; }
            try {
                if (event instanceof NetworkEventDispatcher.Notice notice) { state = notice.state(); message = notice.text(); }
                else if (event instanceof NetworkEventDispatcher.Message packet) { accept(packet.value(), packet.arrivalNanos(), now); }
            } catch (IllegalArgumentException | IllegalStateException failure) {
                fail(failure.getMessage() != null && failure.getMessage().contains("地图") ? ConnectionState.INCOMPATIBLE : ConnectionState.FAILED,
                        failure.getMessage() == null ? "服务器消息异常，已安全断开" : failure.getMessage());
            }
        }
        if (state == ConnectionState.RUNNING && authority != null) {
            prediction.rttNanos(network.metrics().rttNanos()); send(command, now, false);
            snapshot = interpolation.display(authority, slot, predictionEnabled() ? prediction.display(now) : null, now);
        } else if (state.terminal()) { prediction.clearPending(); interpolation.clear(); snapshot = authority; }
    }
    public void releaseInput() {
        requireOwner();
        if (state == ConnectionState.RUNNING) { send(TankCommand.IDLE, System.nanoTime(), true); }
    }
    private void send(TankCommand command, long now, boolean force) {
        var input = sender.sample(match, prediction.estimatedServerTick(now), tps, prediction.leadTicks(tps), command, now, force);
        if (input != null && network.sendInput(input)) { prediction.input(input); }
    }
    private void accept(TankProtocol.Envelope value, long arrived, long now) {
        if (value.hasServerHello()) {
            tps = value.getServerHello().getServerTps(); predictionSupported = value.getServerHello().getPredictionSupported();
            prediction.enabled(predictionRequested && predictionSupported);
        }
        else if (value.hasJoinRoomResponse()) {
            var join = value.getJoinRoomResponse(); slot = join.getPlayerSlot(); match = join.getMatchId(); receiver.configure(match, tps, predictionSupported);
            state = ConnectionState.WAITING; message = "已加入房间，正在等待另一位玩家";
        } else if (value.hasWorldSnapshot()) {
            var world = value.getWorldSnapshot();
            if (match.equals(world.getMatchId()) && (world.getServerTick() < receiver.serverTick()
                    || world.getServerTick() == receiver.serverTick() && state == ConnectionState.RUNNING && world.getState() == TankProtocol.RoomState.RUNNING)) {
                staleSnapshots++; return;
            }
            authority = receiver.receive(world, TankGame.Result.NONE); validateAck();
            prediction.rttNanos(network.metrics().rttNanos()); prediction.reconcile(authority, world, slot, arrived, now);
            interpolation.offer(world.getServerTick(), tps, authority, arrived); snapshot = authority;
            switch (value.getWorldSnapshot().getState()) {
                case WAITING -> { state = ConnectionState.WAITING; message = "已加入房间，正在等待另一位玩家"; }
                case RUNNING -> { state = ConnectionState.RUNNING; message = "比赛开始 · 在线对战进行中"; }
                case FINISHED, ABORTED -> { state = ConnectionState.FINISHING; message = "比赛结束，正在确认结果"; }
                default -> throw new IllegalArgumentException("服务器比赛状态异常");
            }
        } else if (value.hasGameEvent()) {
            receiver.event(value.getGameEvent()); eventId = Math.max(eventId, value.getGameEvent().getEventId());
        } else if (value.hasMatchResult()) {
            var finalResult = value.getMatchResult();
            if (!finalResult.hasFinalSnapshot()) { throw new IllegalArgumentException("服务器结算数据异常"); }
            boolean cancelled = finalResult.getOutcome() == TankProtocol.Outcome.CANCELLED;
            if (finalResult.getFinalSnapshot().getState() != (cancelled ? TankProtocol.RoomState.ABORTED : TankProtocol.RoomState.FINISHED)) {
                throw new IllegalArgumentException("服务器结算状态异常");
            }
            TankGame.Result winner = switch (finalResult.getOutcome()) {
                case PLAYER_ONE -> TankGame.Result.PLAYER_ONE; case PLAYER_TWO -> TankGame.Result.PLAYER_TWO;
                case DRAW -> TankGame.Result.DRAW; case CANCELLED -> TankGame.Result.NONE;
                default -> throw new IllegalArgumentException("服务器结算结果异常");
            };
            authority = receiver.receive(finalResult.getFinalSnapshot(), winner); snapshot = authority; validateAck(); result = finalResult;
            prediction.clearPending(); interpolation.clear();
            state = cancelled ? ConnectionState.DISCONNECTED : ConnectionState.FINISHED;
            message = cancelled ? (finalResult.getReason().equals("DISCONNECTED") ? "玩家已离开，比赛取消，可返回大厅" : "服务器已取消比赛，可返回大厅")
                    : winner == TankGame.Result.DRAW ? "本局平局 · 比赛结束"
                    : (winner == TankGame.Result.PLAYER_ONE ? "P1 获胜" : "P2 获胜") + " · 比赛结束";
            network.close();
        } else if (value.hasError()) {
            var error = value.getError();
            if (error.getInputSequence() < 0 || error.getInputSequence() > sender.sequence()) { throw new IllegalArgumentException("服务器输入确认异常"); }
            // 重复报错可能对应原先已接纳但尚未执行的同一输入，不能提前删除它。
            if (!error.getCode().equals("DUPLICATE_SEQUENCE")) { prediction.reject(error.getInputSequence()); }
            message = "输入到达时间不合适，正在同步服务器状态";
        }
    }
    private void validateAck() {
        if (slot < 0 || receiver.acknowledgement(slot) > sender.sequence() || predictionSupported && receiver.receivedSequence(slot) > sender.sequence()) {
            throw new IllegalArgumentException("服务器输入确认异常");
        }
    }
    public void fail(ConnectionState failure, String text) { requireOwner(); if (!state.terminal()) { state = failure; message = text; prediction.clearPending(); interpolation.clear(); network.close(); } }
    private void requireOwner() { if (Thread.currentThread() != owner) { throw new IllegalStateException("Remote session owner violated"); } }
    @Override public void close() { requireOwner(); if (state != ConnectionState.CLOSED) { state = ConnectionState.CLOSED; prediction.clearPending(); interpolation.clear(); dispatcher.close(); network.close(); } }
}
