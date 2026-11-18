package dev.gamecore.server.multigame;
import dev.gamecore.protocol.GameProtocol;
import java.util.concurrent.atomic.AtomicInteger;
/** 网络连接的最小端口；Room只发送不可变Protobuf值，不知道Channel或UI。 */
public interface RoomPeer {
    /** 旧客户端保留自动开局；交互客户端必须协商同一准备/控制协议。 */
    default boolean interactive(){return false;}
    /** 准备/演出走有界可靠控制面；客户端只保留最新进度值。 */
    default void roomState(dev.gamecore.protocol.GameProtocol.Envelope value){reliable(value);}
    String id(); boolean active(); AtomicInteger pending();
    void reliable(GameProtocol.Envelope message);
    void snapshot(GameProtocol.Envelope message);
    default int reservedSlot(){return -1;}
    default String reservedMatch(){return "";}
    default void disconnect() { }
    default void error(String code,String description,long sequence){reliable(dev.gamecore.online.ProtocolValues.envelope(0,b->b.setError(GameProtocol.Error.newBuilder().setCode(code).setDescription(description).setInputSequence(sequence))));}
}
