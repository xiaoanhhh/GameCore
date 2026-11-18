package dev.gamecore.lwjgl3;

import dev.gamecore.client.platform.PlatformEndpoint;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.util.Arrays;
import javax.crypto.KeyAgreement;
import javax.net.ssl.SSLContext;

/** 无图形验证真正随包运行时的TLS/HTTP/动态依赖/资源，不借用开发机JDK类路径。 */
public final class PortableRuntimeProbe {
    private PortableRuntimeProbe() { }
    public static void main(String[] args) throws Exception {
        if (!Path.of(System.getProperty("java.home")).toRealPath().equals(Path.of(args[0]).toRealPath())) {
            throw new IllegalStateException("System Java used instead of bundled runtime");
        }
        if (!System.getProperty("java.vendor").contains("Eclipse Adoptium") || !System.getProperty("java.version").startsWith("21.")) {
            throw new IllegalStateException("Unexpected runtime vendor/version");
        }
        if (Security.getProvider("SunEC") == null || !Arrays.asList(SSLContext.getDefault().getSupportedSSLParameters().getProtocols()).contains("TLSv1.3")) {
            throw new IllegalStateException("TLS provider missing from linked runtime");
        }
        KeyAgreement.getInstance("ECDH");
        try (var client = HttpClient.newHttpClient()) {
            if (client.sslContext() == null) { throw new IllegalStateException("HTTP TLS unavailable"); }
        }
        Class.forName("io.netty.buffer.Unpooled");
        Class.forName("com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application");
        try (var resource = PortableRuntimeProbe.class.getResourceAsStream("/fonts/characters.txt")) {
            if (resource == null || resource.readNBytes(8192).length < 1000) { throw new IllegalStateException("Packaged font resources missing"); }
        }
        var endpoint = PlatformEndpoint.load();
        if (!endpoint.configured() || !endpoint.url().startsWith("https://")) { throw new IllegalStateException("Published HTTPS endpoint missing"); }
        Files.writeString(Path.of(args[1]), "PORTABLE_RUNTIME_OK\nBundledJava=true\nTLS13=true\nECDH=true\nHTTP=true\nResources=true\nEndpoint=" + endpoint.url() + "\n");
    }
}
