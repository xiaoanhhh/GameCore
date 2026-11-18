package dev.gamecore.client.traffic;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import dev.gamecore.protocol.GameProtocol;
import dev.gamecore.protocol.TankProtocol;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** 每类消息都映射到正确类型，且带哨兵值的敏感字段不会进入展示文本。 */
class TrafficRecorderTest {
    private static final String SECRET = "SENTINEL-SECRET-VALUE";
    private final AtomicLong millis = new AtomicLong(1_000), nanos = new AtomicLong();
    private final TrafficLog log = new TrafficLog(millis::get, ZoneOffset.UTC);
    private final TrafficRecorder recorder = new TrafficRecorder(log, nanos::get);
    { log.enable(); }

    private static GameProtocol.Envelope.Builder v2() { return GameProtocol.Envelope.newBuilder().setProtocolVersion(2); }
    private static TankProtocol.Envelope.Builder v1() { return TankProtocol.Envelope.newBuilder().setProtocolVersion(1); }
    private TrafficEntry last() { return log.entries().getLast(); }
    private void close() { millis.addAndGet(TrafficLog.WINDOW_MILLIS); log.flush(); }

    @Test void arenaHandshakeIsConnectionTrafficAndHidesCredentials() {
        recorder.sent(v2().setHello(GameProtocol.Hello.newBuilder().setClientName(SECRET)).build());
        assertEquals(TrafficDirection.SENT, last().direction());
        recorder.received(v2().setWelcome(GameProtocol.Welcome.newBuilder().setConnectionId(SECRET).setCredential(SECRET)
                .addGames(GameProtocol.GameType.newBuilder().setGameId("snake"))).build());
        recorder.sent(v2().setJoin(GameProtocol.Join.newBuilder().setRoomId("snake-1").setRulesVersion("snake/1")
                .setCredential(SECRET).setPlatformTicket(SECRET)).build());
        assertEquals("加入房间 snake-1 · snake/1", last().text());
        recorder.received(v2().setJoined(GameProtocol.Joined.newBuilder().setRoomId("snake-1").setMatchId("0123456789abcdef").setPlayerSlot(1)).build());
        assertEquals("已加入 snake-1 · 比赛 01234567 · P2", last().text());
        assertTrue(log.entries().stream().allMatch(entry -> entry.kind() == TrafficKind.CONNECTION));
        assertSecretFree();
    }

    @Test void arenaInputsAndSnapshotsAreMergedAndDoNotExposePayloads() {
        for (int i = 1; i <= 3; i++) {
            recorder.sent(v2().setInput(GameProtocol.Input.newBuilder().setSequence(i).setCommand(ByteString.copyFromUtf8(SECRET))).build());
            recorder.received(v2().setSnapshot(GameProtocol.Snapshot.newBuilder().setServerTick(10 + i).setState(ByteString.copyFromUtf8(SECRET))).build());
        }
        assertTrue(log.entries().isEmpty());
        close();
        var entries = log.entries();
        assertEquals(List.of(TrafficKind.INPUT, TrafficKind.SNAPSHOT), entries.stream().map(TrafficEntry::kind).toList());
        assertEquals(TrafficDirection.SENT, entries.get(0).direction());
        assertEquals(TrafficDirection.RECEIVED, entries.get(1).direction());
        assertTrue(entries.get(0).text().startsWith("×3 · 序号 1→3"));
        assertTrue(entries.get(1).text().startsWith("×3 · tick 11→13"));
        assertSecretFree();
    }

    @Test void arenaRoomEventResultAndErrorUseTheirOwnKinds() {
        recorder.sent(v2().setControl(GameProtocol.RoomControl.newBuilder().setSequence(4).setAction(GameProtocol.RoomAction.SELECT)
                .addChoices(GameProtocol.Option.newBuilder().setKey("map").setValue("clear"))).build());
        assertEquals(new TrafficEntry(last().time(), TrafficKind.ROOM, TrafficDirection.SENT, "SELECT · 序号 4 · map=clear"), last());
        recorder.received(v2().setRoomState(GameProtocol.RoomState.newBuilder().setPhase("PAUSED").setPaused(true).setRevision(7)
                .addSeats(GameProtocol.RoomSeat.newBuilder().setReady(true)).addSeats(GameProtocol.RoomSeat.getDefaultInstance())).build());
        assertEquals("PAUSED · 暂停 · 版本 7 · 准备 1/2", last().text());
        recorder.received(v2().setEvents(GameProtocol.EventBatch.newBuilder()
                .addEvents(GameProtocol.GameEvent.newBuilder().setKind("HIT")).addEvents(GameProtocol.GameEvent.newBuilder().setKind("HIT"))
                .addEvents(GameProtocol.GameEvent.newBuilder().setKind("SCORE"))).build());
        assertEquals(new TrafficEntry(last().time(), TrafficKind.EVENT, TrafficDirection.RECEIVED, "×3 · HIT, SCORE"), last());
        recorder.received(v2().setResult(GameProtocol.Result.newBuilder().setOutcome("P1").setReason("SCORE")).build());
        assertEquals(TrafficKind.EVENT, last().kind());
        recorder.received(v2().setError(GameProtocol.Error.newBuilder().setCode("LATE_INPUT").setDescription(SECRET).setInputSequence(9)).build());
        assertEquals(new TrafficEntry(last().time(), TrafficKind.ERROR, TrafficDirection.RECEIVED, "LATE_INPUT · 序号 9"), last());
        assertSecretFree();
    }

    @Test void heartbeatsShowRoundTripTimeAndTheTableIsBounded() {
        var ping = v2().setRequestId(5).setHeartbeat(GameProtocol.Heartbeat.getDefaultInstance()).build();
        nanos.set(1_000_000);
        recorder.sent(ping);
        assertEquals(new TrafficEntry(last().time(), TrafficKind.HEARTBEAT, TrafficDirection.SENT, "#5"), last());
        nanos.set(13_000_000);
        recorder.received(ping);
        assertEquals("#5 · 往返 12 ms", last().text());
        recorder.received(ping);
        assertEquals("#5", last().text(), "A repeated reply has no matching probe");
        for (long id = 100; id < 120; id++) { recorder.sent(v2().setRequestId(id).setHeartbeat(GameProtocol.Heartbeat.getDefaultInstance()).build()); }
        recorder.received(v2().setRequestId(100).setHeartbeat(GameProtocol.Heartbeat.getDefaultInstance()).build());
        assertEquals("#100", last().text(), "Oldest probes are evicted");
    }

    @Test void legacyTankEnvelopeIsCoveredWithoutExposingJoinCredential() {
        recorder.sent(v1().setClientHello(TankProtocol.ClientHello.newBuilder().setClientName(SECRET)).build());
        recorder.received(v1().setServerHello(TankProtocol.ServerHello.newBuilder().setConnectionId(SECRET).setJoinCredential(SECRET).setRulesVersion("tank/1").setServerTps(30)).build());
        assertEquals("握手完成 · tank/1 · 30 tps", last().text());
        recorder.sent(v1().setJoinRoomRequest(TankProtocol.JoinRoomRequest.newBuilder().setRoomId("tank-1").setCredential(SECRET)).build());
        recorder.received(v1().setJoinRoomResponse(TankProtocol.JoinRoomResponse.newBuilder().setRoomId("tank-1").setMatchId("m-1").setPlayerSlot(0)).build());
        assertEquals("已加入 tank-1 · 比赛 m-1 · P1", last().text());
        recorder.sent(v1().setPlayerInput(TankProtocol.PlayerInput.newBuilder().setSequence(3)).build());
        recorder.received(v1().setWorldSnapshot(TankProtocol.WorldSnapshot.newBuilder().setServerTick(8)).build());
        recorder.received(v1().setGameEvent(TankProtocol.GameEvent.newBuilder().setKind("SHOT").setServerTick(9)).build());
        assertEquals(new TrafficEntry(last().time(), TrafficKind.EVENT, TrafficDirection.RECEIVED, "SHOT · tick 9"), last());
        recorder.received(v1().setMatchResult(TankProtocol.MatchResult.newBuilder().setOutcome(TankProtocol.Outcome.DRAW).setReason("TIME")).build());
        recorder.received(v1().setError(TankProtocol.ErrorMessage.newBuilder().setCode("EARLY_INPUT").setDescription(SECRET)).build());
        assertEquals(TrafficKind.ERROR, last().kind());
        recorder.sent(v1().setRequestId(3).setHeartbeat(TankProtocol.Heartbeat.getDefaultInstance()).build());
        assertEquals(TrafficKind.HEARTBEAT, last().kind());
        close();
        var kinds = log.entries().stream().map(TrafficEntry::kind).distinct().toList();
        assertTrue(kinds.containsAll(List.of(TrafficKind.CONNECTION, TrafficKind.INPUT, TrafficKind.SNAPSHOT, TrafficKind.EVENT, TrafficKind.ERROR, TrafficKind.HEARTBEAT)));
        assertSecretFree();
    }

    @Test void connectionStatesAreLocalAndUnknownMessagesAreIgnoredSafely() {
        recorder.connection("TCP 已断开");
        assertEquals(new TrafficEntry(last().time(), TrafficKind.CONNECTION, TrafficDirection.LOCAL, "TCP 已断开"), last());
        recorder.received(v2().build());
        assertEquals(TrafficKind.ERROR, last().kind());
        int before = log.entries().size();
        recorder.received(GameProtocol.Heartbeat.getDefaultInstance());
        assertEquals(before, log.entries().size());
    }

    private void assertSecretFree() {
        close();
        for (var entry : log.entries()) { assertFalse(entry.text().contains(SECRET), entry.text()); }
    }
}
