package dev.gamecore.network.transport;

import com.google.protobuf.CodedInputStream;
import dev.gamecore.protocol.TankProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;

/** 四字节大端Protobuf正文长度，最多16KiB；入站基类释放ByteBuf，生成代码由Gradle维护。 */
public final class ProtocolPipeline {
    public static final int MAX_BODY = 16_384;
    private ProtocolPipeline() { }
    public static void install(ChannelPipeline pipeline) {
        install(pipeline, TankProtocol.Envelope.parser(), MAX_BODY);
    }
    /** 游戏无关帧层：parser/容量由端点选择，长度字段与引用计数策略完全共享。 */
    public static <T extends com.google.protobuf.MessageLite> void install(ChannelPipeline pipeline, com.google.protobuf.Parser<T> parser, int maximum) {
        if (maximum < 256 || maximum > 65_536) { throw new IllegalArgumentException("Invalid frame limit"); }
        pipeline.addLast("frame", new LengthFieldBasedFrameDecoder(maximum + 4, 0, 4, 0, 4, true));
        pipeline.addLast("protobufDecode", new MessageToMessageDecoder<ByteBuf>() {
            @Override protected void decode(ChannelHandlerContext context, ByteBuf input, List<Object> output) throws Exception {
                byte[] bytes = new byte[input.readableBytes()]; input.readBytes(bytes);
                var coded = CodedInputStream.newInstance(bytes); coded.setRecursionLimit(16); coded.setSizeLimit(maximum);
                output.add(parser.parseFrom(coded));
            }
        });
        pipeline.addLast("length", new LengthFieldPrepender(4));
        pipeline.addLast("protobufEncode", new MessageToByteEncoder<com.google.protobuf.MessageLite>(com.google.protobuf.MessageLite.class) {
            @Override protected void encode(ChannelHandlerContext context, com.google.protobuf.MessageLite message, ByteBuf output) {
                if (message.getSerializedSize() > maximum) { throw new IllegalArgumentException("Outbound frame too large"); }
                output.writeBytes(message.toByteArray());
            }
        });
    }
}
