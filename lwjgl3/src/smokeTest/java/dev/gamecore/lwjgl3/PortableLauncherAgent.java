package dev.gamecore.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.screen.HomeScreen;
import dev.gamecore.client.screen.PlatformScreen;
import dev.gamecore.client.screen.UiScreen;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 仅测试用Agent：观察正式exe并将UI操作排到渲染线程；不随应用发行。 */
public final class PortableLauncherAgent {
    private PortableLauncherAgent() { }
    public static void premain(String output, Instrumentation ignored) throws Exception {
        Path directory = Path.of(output); Files.createDirectories(directory.resolve("tmp"));
        System.setProperty("gamecore.settingsFile", directory.resolve("settings.properties").toString());
        System.setProperty("java.io.tmpdir", directory.resolve("tmp").toString());
        System.setProperty("gamecore.offline", "false");
        if (!Path.of(System.getProperty("java.home")).toRealPath().equals(Path.of(System.getenv("GAMECORE_PORTABLE_EXPECTED_RUNTIME")).toRealPath())) {
            throw new IllegalStateException("Native launcher did not load bundled JVM");
        }
        AtomicReference<GameCoreClient> clientRef = new AtomicReference<>();
        AtomicReference<UiScreen> platformRef = new AtomicReference<>();
        AtomicInteger step = new AtomicInteger(), visibleFrames = new AtomicInteger();
        AtomicBoolean posting = new AtomicBoolean();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        var poller = Executors.newSingleThreadScheduledExecutor(action -> {
            var thread = new Thread(action, "portable-exe-verifier"); thread.setDaemon(true); return thread;
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                var client = clientRef.get();
                if (client == null || !client.isDisposed() || step.get() != 2 || !platformRef.get().isDisposed()) {
                    throw new IllegalStateException("Native application did not finish and release stages");
                }
                client.platform().termination().get(5, TimeUnit.SECONDS);
                Files.writeString(directory.resolve("result.txt"), "PORTABLE_EXE_OK\nBundledJVM=true\nNoJavaHomeOrPath=true\nGuestCloudLobby=true\nOfflineReturn=true\nDisposed=true\n");
            } catch (Exception failure) { failure(directory, failure); }
        }, "portable-exe-result"));
        poller.scheduleWithFixedDelay(() -> {
            if (Gdx.app == null || !posting.compareAndSet(false, true)) { return; }
            Gdx.app.postRunnable(() -> {
                try {
                    if (!(Gdx.app.getApplicationListener() instanceof GameCoreClient client) || client.getScreen() == null) { return; }
                    clientRef.set(client);
                    if (System.nanoTime() > deadline) { throw new IllegalStateException("Public guest query timed out"); }
                    if (step.get() == 0) {
                        if (System.getProperty("gamecore.platform.url") != null || System.getenv("GAMECORE_PLATFORM_URL") != null) {
                            throw new IllegalStateException("Endpoint override hides packaged configuration");
                        }
                        if (!(client.getScreen() instanceof PlatformScreen screen) ||
                                !(screen.stage().getRoot().findActor("platform-notice") instanceof Label notice) ||
                                !notice.getText().toString().contains("房间列表已更新")) { return; }
                        for (String game : List.of("tank", "snake", "fight")) {
                            if (screen.stage().getRoot().findActor("platform-create-" + game) == null) { throw new IllegalStateException("Missing public game " + game); }
                        }
                        // 等待布局后的帧实际绘制，避免只捕获HTTP完成前的旧framebuffer。
                        if (visibleFrames.incrementAndGet() < 3) { return; }
                        var pixels = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
                        try { PixmapIO.writePNG(new FileHandle(directory.resolve("cloud-lobby.png").toFile()), pixels, 6, true); }
                        finally { pixels.dispose(); }
                        platformRef.set(screen); step.incrementAndGet(); client.home();
                    } else if (step.get() == 1 && client.getScreen() instanceof HomeScreen) {
                        step.incrementAndGet(); poller.shutdown(); Gdx.app.exit();
                    }
                } catch (Throwable error) {
                    failure(directory, error); poller.shutdown(); Gdx.app.exit();
                } finally { posting.set(false); }
            });
        }, 500, 150, TimeUnit.MILLISECONDS);
    }
    private static void failure(Path directory, Throwable error) {
        try { Files.writeString(directory.resolve("failure.txt"), error.toString()); }
        catch (java.io.IOException cannotReport) { error.addSuppressed(cannotReport); }
        error.printStackTrace();
    }
}
