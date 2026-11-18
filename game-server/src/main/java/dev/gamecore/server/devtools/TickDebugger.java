package dev.gamecore.server.devtools;
import dev.gamecore.server.multigame.*;
import java.util.List;
import java.util.concurrent.*;
/** 仅供显式回环开发服务器的进程内/标准输入控制；没有HTTP、TCP管理监听口。 */
public final class TickDebugger {
    private final RoomHost rooms;
    public TickDebugger(RoomHost rooms,boolean localDevelopment){if(!localDevelopment){throw new IllegalArgumentException("Debugger disabled");}this.rooms=rooms;}
    public List<RoomActor.Metrics> rooms(){return rooms.rooms().stream().map(RoomActor::metrics).toList();}
    public CompletableFuture<RoomActor.Metrics> pause(String room){return rooms.require(room).debug(true,false);}
    public CompletableFuture<RoomActor.Metrics> resume(String room){return rooms.require(room).debug(false,false);}
    public CompletableFuture<RoomActor.Metrics> step(String room){return rooms.require(room).debug(true,true);}
    public CompletableFuture<RoomActor.Metrics> advance(String room,int steps){return rooms.require(room).debug(true,steps);}
}
