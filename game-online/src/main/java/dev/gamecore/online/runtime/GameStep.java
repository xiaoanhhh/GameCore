package dev.gamecore.online.runtime;
import dev.gamecore.api.session.SessionState;
import dev.gamecore.protocol.GameProtocol;
import java.util.List;
/** 单个规则子步的稳定值；可供网络/录像异步消费，不持有GameSession或World。 */
public record GameStep(long tick,SessionState state,String outcome,byte[] snapshot,int entities,List<GameProtocol.GameEvent> events,List<AppliedInput> inputs,long simulationNanos,long snapshotNanos){
    public GameStep{snapshot=snapshot.clone();events=List.copyOf(events);inputs=List.copyOf(inputs);}
    @Override public byte[] snapshot(){return snapshot.clone();}
}
