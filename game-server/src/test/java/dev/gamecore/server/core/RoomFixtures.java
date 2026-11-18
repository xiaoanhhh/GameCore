package dev.gamecore.server.core;

import dev.gamecore.tank.TankMap;
import dev.gamecore.tank.Vec2;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 受信测试地图/非阻塞伪连接；不向生产网络暴露地图或状态注入接口。 */
public final class RoomFixtures {
    private RoomFixtures() { }
    public static TankMap open() { return map(false); }
    public static TankMap map(boolean wall) {
        boolean[] walls = new boolean[12 * 8];
        if (wall) { for (int y = 0; y < 8; y++) { walls[y * 12 + 6] = true; } }
        return new TankMap("fixture", 12, 8, walls, List.of(new TankMap.Spawn(new Vec2(2.5, 4.5), 0), new TankMap.Spawn(new Vec2(9.5, 4.5), 180)), List.of());
    }
    public static final class Peer implements RoomPeer {
        public final AtomicInteger pending = new AtomicInteger();
        public final List<String> errors = new ArrayList<>();
        public final List<RoomFrame.Event> events = new ArrayList<>();
        public RoomFrame frame, result;
        public int slot = -1, resultCount;
        public String match;
        public volatile boolean disconnected;
        @Override public String identity() { return "fixture-peer"; }
        @Override public boolean disconnected() { return disconnected; }
        @Override public AtomicInteger queuedInputs() { return pending; }
        @Override public void joined(long request, int assigned, String matchId) { slot = assigned; match = matchId; }
        @Override public void reject(long request, String code) { errors.add(code); }
        @Override public void offerFrame(RoomFrame value) { frame = value; events.addAll(value.events()); }
        @Override public void result(RoomFrame value) { result = value; resultCount++; }
    }
    public static GameRoom started(int tps, Peer first, Peer second) {
        var room = new GameRoom(open(), tps, 42); room.requestJoin(first, 1); room.requestJoin(second, 2); room.step(); return room;
    }
    public static InputIntent intent(GameRoom room, long sequence, int move, int turn, boolean fire) {
        return new InputIntent(sequence, room.latest().matchId(), sequence, room.latest().serverTick() + 1, move, turn, null, fire);
    }
}
