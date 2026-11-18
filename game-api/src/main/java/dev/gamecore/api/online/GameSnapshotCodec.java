package dev.gamecore.api.online;
/** 稳定只读快照与协议投影；不得返回可变World或组件容器。 */
public interface GameSnapshotCodec<S> {
    byte[] encode(S snapshot);
    S decode(byte[] bytes);
    String outcome(S snapshot);
    int entityCount(S snapshot);
}
