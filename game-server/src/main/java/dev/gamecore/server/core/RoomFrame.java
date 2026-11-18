package dev.gamecore.server.core;

import dev.gamecore.tank.TankSnapshot;
import java.util.List;

/** 发布后稳定的只读投影；含原不可变TankSnapshot，不暴露GameSession/可写数组或ByteBuf。 */
public record RoomFrame(String matchId, long serverTick, int tps, State state, TankSnapshot world,
                        List<Long> acknowledgements, List<Boolean> connected, List<Event> events,
                        String mapDigest, String reason, List<InputStatus> inputStatus) {
    public enum State { WAITING, RUNNING, FINISHED, ABORTED }
    public record Event(long id, long serverTick, long simulationTick, TankSnapshot.Event value) { }
    public record InputStatus(long received, long appliedTick, InputIntent active, long expiresTick) { }
    public RoomFrame(String matchId, long serverTick, int tps, State state, TankSnapshot world,
                     List<Long> acknowledgements, List<Boolean> connected, List<Event> events, String mapDigest, String reason) {
        this(matchId, serverTick, tps, state, world, acknowledgements, connected, events, mapDigest, reason,
                List.of(new InputStatus(acknowledgements.get(0), 0, null, 0), new InputStatus(acknowledgements.get(1), 0, null, 0)));
    }
    public RoomFrame {
        acknowledgements = List.copyOf(acknowledgements); connected = List.copyOf(connected); events = List.copyOf(events);
        inputStatus = List.copyOf(inputStatus);
    }
}
