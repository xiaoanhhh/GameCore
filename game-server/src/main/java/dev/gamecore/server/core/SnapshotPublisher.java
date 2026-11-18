package dev.gamecore.server.core;

import java.util.List;

/** 发布域对象到有界出站端点；序列化/写socket在网络执行域，房间不等待任何网络Future。 */
public final class SnapshotPublisher {
    public void publish(RoomFrame frame, List<RoomPeer> peers, boolean terminal) {
        for (var peer : peers) {
            if (peer.disconnected()) { continue; }
            peer.offerFrame(frame);
            if (terminal) { peer.result(frame); }
        }
    }
}
