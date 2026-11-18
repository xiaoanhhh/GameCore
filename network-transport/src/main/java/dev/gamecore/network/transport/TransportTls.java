package dev.gamecore.network.transport;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import java.nio.file.Path;
import javax.net.ssl.SSLException;

/** TLS只保护传输，不改变帧/Protobuf；使用JDK证书链和主机名校验，不提供跳过验证的开关。 */
public final class TransportTls {
    private TransportTls() { }
    public static SslContext server(Path certificate, Path privateKey) throws SSLException {
        return SslContextBuilder.forServer(certificate.toFile(), privateKey.toFile()).sslProvider(SslProvider.JDK)
                .protocols("TLSv1.3", "TLSv1.2").build();
    }
    public static SslContext client(Path ca) throws SSLException {
        var builder = SslContextBuilder.forClient().sslProvider(SslProvider.JDK)
                .protocols("TLSv1.3", "TLSv1.2").endpointIdentificationAlgorithm("HTTPS");
        if (ca != null) { builder.trustManager(ca.toFile()); }
        return builder.build();
    }
    /** 仅字面回环可以在无TLS时传平台票据，不能依赖DNS解析结果放宽这个边界。 */
    public static boolean loopback(String host) { return host.equals("127.0.0.1") || host.equals("::1") || host.equals("localhost"); }
}
