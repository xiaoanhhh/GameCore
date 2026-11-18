package dev.gamecore.client.traffic;

import com.google.protobuf.MessageLite;
import dev.gamecore.network.transport.TrafficObserver;
import dev.gamecore.protocol.GameProtocol;
import dev.gamecore.protocol.TankProtocol;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * 单条连接的记录器：把协议消息转成展示文本。回调都在该连接的 EventLoop 上，心跳表不需要同步。
 * 这里按字段白名单取值，握手凭证、平台票据、玩家名称和快照内容都不进入文本；
 * 不要改成打印消息本身，Protobuf 的 toString 会带出全部字段。
 */
public final class TrafficRecorder implements TrafficObserver {
    private static final int PING_LIMIT = 8;
    private final TrafficLog log;
    private final LongSupplier nanos;
    private final Map<Long, Long> pings = new LinkedHashMap<>();

    public TrafficRecorder(TrafficLog log) { this(log, System::nanoTime); }
    TrafficRecorder(TrafficLog log, LongSupplier nanos) { this.log = log; this.nanos = nanos; }

    @Override public void sent(MessageLite message) { record(message, TrafficDirection.SENT); }
    @Override public void received(MessageLite message) { record(message, TrafficDirection.RECEIVED); }
    @Override public void connection(String state) { log.add(TrafficKind.CONNECTION, TrafficDirection.LOCAL, state); }

    private void record(MessageLite message, TrafficDirection direction) {
        if (message instanceof GameProtocol.Envelope value) { arena(value, direction); }
        else if (message instanceof TankProtocol.Envelope value) { tank(value, direction); }
    }

    private void arena(GameProtocol.Envelope e, TrafficDirection d) {
        switch (e.getPayloadCase()) {
            case HELLO -> line(TrafficKind.CONNECTION, d, "握手请求");
            case WELCOME -> line(TrafficKind.CONNECTION, d, "握手完成 · 游戏 " + e.getWelcome().getGamesCount() + " · 房间 " + e.getWelcome().getRoomsCount());
            case JOIN -> line(TrafficKind.CONNECTION, d, "加入房间 " + e.getJoin().getRoomId() + " · " + e.getJoin().getRulesVersion());
            case JOINED -> line(TrafficKind.CONNECTION, d, joined(e.getJoined().getRoomId(), e.getJoined().getMatchId(), e.getJoined().getPlayerSlot()));
            case INPUT -> log.burst(TrafficKind.INPUT, d, "序号", e.getInput().getSequence(), e.getSerializedSize());
            case SNAPSHOT -> log.burst(TrafficKind.SNAPSHOT, d, "tick", e.getSnapshot().getServerTick(), e.getSerializedSize());
            case EVENTS -> line(TrafficKind.EVENT, d, events(e.getEvents()));
            case RESULT -> line(TrafficKind.EVENT, d, "比赛结果 " + e.getResult().getOutcome() + " · " + e.getResult().getReason());
            case ERROR -> line(TrafficKind.ERROR, d, error(e.getError().getCode(), e.getError().getInputSequence()));
            case HEARTBEAT -> heartbeat(e.getRequestId(), d);
            case CONTROL -> line(TrafficKind.ROOM, d, control(e.getControl()));
            case ROOM_STATE -> line(TrafficKind.ROOM, d, room(e.getRoomState()));
            default -> line(TrafficKind.ERROR, d, "未识别的消息");
        }
    }

    private void tank(TankProtocol.Envelope e, TrafficDirection d) {
        switch (e.getPayloadCase()) {
            case CLIENT_HELLO -> line(TrafficKind.CONNECTION, d, "握手请求");
            case SERVER_HELLO -> line(TrafficKind.CONNECTION, d, "握手完成 · " + e.getServerHello().getRulesVersion() + " · " + e.getServerHello().getServerTps() + " tps");
            case JOIN_ROOM_REQUEST -> line(TrafficKind.CONNECTION, d, "加入房间 " + e.getJoinRoomRequest().getRoomId());
            case JOIN_ROOM_RESPONSE -> line(TrafficKind.CONNECTION, d, joined(e.getJoinRoomResponse().getRoomId(), e.getJoinRoomResponse().getMatchId(), e.getJoinRoomResponse().getPlayerSlot()));
            case PLAYER_INPUT -> log.burst(TrafficKind.INPUT, d, "序号", e.getPlayerInput().getSequence(), e.getSerializedSize());
            case WORLD_SNAPSHOT -> log.burst(TrafficKind.SNAPSHOT, d, "tick", e.getWorldSnapshot().getServerTick(), e.getSerializedSize());
            case GAME_EVENT -> line(TrafficKind.EVENT, d, e.getGameEvent().getKind() + " · tick " + e.getGameEvent().getServerTick());
            case MATCH_RESULT -> line(TrafficKind.EVENT, d, "比赛结果 " + e.getMatchResult().getOutcome() + " · " + e.getMatchResult().getReason());
            case ERROR -> line(TrafficKind.ERROR, d, error(e.getError().getCode(), e.getError().getInputSequence()));
            case HEARTBEAT -> heartbeat(e.getRequestId(), d);
            default -> line(TrafficKind.ERROR, d, "未识别的消息");
        }
    }

    private void line(TrafficKind kind, TrafficDirection direction, String text) { log.add(kind, direction, text); }

    private void heartbeat(long request, TrafficDirection direction) {
        if (direction == TrafficDirection.SENT) {
            pings.put(request, nanos.getAsLong());
            if (pings.size() > PING_LIMIT) { pings.remove(pings.keySet().iterator().next()); }
            line(TrafficKind.HEARTBEAT, direction, "#" + request);
            return;
        }
        Long sent = pings.remove(request);
        line(TrafficKind.HEARTBEAT, direction, sent == null ? "#" + request : "#" + request + " · 往返 " + (nanos.getAsLong() - sent) / 1_000_000 + " ms");
    }

    private static String joined(String room, String match, int slot) {
        return "已加入 " + room + " · 比赛 " + match.substring(0, Math.min(8, match.length())) + " · P" + (slot + 1);
    }
    private static String error(String code, long sequence) { return sequence > 0 ? code + " · 序号 " + sequence : code; }
    private static String events(GameProtocol.EventBatch batch) {
        var kinds = batch.getEventsList().stream().map(GameProtocol.GameEvent::getKind).distinct().limit(3).toList();
        return "×" + batch.getEventsCount() + " · " + String.join(", ", kinds);
    }
    /** 控制消息的 choices 是白名单配置项（地图、角色），可以展示。 */
    private static String control(GameProtocol.RoomControl control) {
        StringBuilder text = new StringBuilder(control.getAction().name()).append(" · 序号 ").append(control.getSequence());
        for (var choice : control.getChoicesList()) { text.append(" · ").append(choice.getKey()).append('=').append(choice.getValue()); }
        return text.toString();
    }
    private static String room(GameProtocol.RoomState state) {
        long ready = state.getSeatsList().stream().filter(GameProtocol.RoomSeat::getReady).count();
        return state.getPhase() + (state.getPaused() ? " · 暂停" : "") + " · 版本 " + state.getRevision() + " · 准备 " + ready + "/" + state.getSeatsCount();
    }
}
