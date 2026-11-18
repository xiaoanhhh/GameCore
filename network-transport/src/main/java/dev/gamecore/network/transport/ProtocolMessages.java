package dev.gamecore.network.transport;

import dev.gamecore.protocol.TankProtocol;
import java.util.function.Consumer;

/** 共用封装版本与请求关联，业务字段仍由两端各自的只读映射负责。 */
public final class ProtocolMessages {
    public static final int VERSION = 1;
    private ProtocolMessages() { }
    public static TankProtocol.Envelope envelope(long request, Consumer<TankProtocol.Envelope.Builder> payload) {
        var builder = TankProtocol.Envelope.newBuilder().setProtocolVersion(VERSION).setRequestId(request);
        payload.accept(builder); return builder.build();
    }
}
