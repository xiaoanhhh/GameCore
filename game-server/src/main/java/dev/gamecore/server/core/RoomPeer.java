package dev.gamecore.server.core;

import java.util.concurrent.atomic.AtomicInteger;

/** 房间仅使用非阻塞值消息端点；连接标记/入队额度是线程安全桥接状态，不是游戏世界。 */
public interface RoomPeer {
    String identity();
    boolean disconnected();
    AtomicInteger queuedInputs();
    void joined(long requestId, int slot, String matchId);
    void reject(long requestId, String code);
    default void rejectInput(InputIntent input, String code) { reject(input.requestId(), code); }
    void offerFrame(RoomFrame frame);
    void result(RoomFrame frame);
}
