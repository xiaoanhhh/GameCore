package dev.gamecore.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** 包内精简 JVM 的图形探针；模拟 HTTP 由外部测试进程提供，不给发行运行时增加 jdk.httpserver。 */
public final class PlatformUiPackagedProbe {
    private PlatformUiPackagedProbe() { }
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]); Files.createDirectories(out);
        System.setProperty("gamecore.platform.url", args[1]);
        System.setProperty("gamecore.offline", "false");
        var client = new PlatformUiSmokeTest.Probe(out);
        var config = Lwjgl3Launcher.configuration(DesktopSettings.defaults()); config.disableAudio(true);
        config.setTitle("GameCore packaged platform UI acceptance");
        try {
            new Lwjgl3Application(client, config);
            client.platform().termination().get(8, TimeUnit.SECONDS);
            if (!client.done || !client.isDisposed()) { throw new IllegalStateException("Incomplete packaged UI probe"); }
            Files.writeString(out.resolve("result.txt"), "PACKAGED_PLATFORM_UI_OK\nBundledRuntime=true\nExternalLoopbackFixture=true\nFullPlatformUiSequence=true\n");
            System.out.println("PACKAGED_PLATFORM_UI_OK");
        } finally { System.clearProperty("gamecore.platform.url"); System.clearProperty("gamecore.offline"); }
    }
}
