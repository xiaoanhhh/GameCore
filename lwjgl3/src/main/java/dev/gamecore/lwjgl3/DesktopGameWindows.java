package dev.gamecore.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowConfiguration;
import dev.gamecore.client.GameCoreClient;
import dev.gamecore.client.window.GameWindowHost;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 同一桌面应用的多窗口适配。每个游戏/模式只保留一个窗口，重复启动聚焦；不同模式可同时打开。
 * 所有操作仍在 LWJGL 图形线程，成绩共享、GPU 资源分开拥有；回调直接指向所属窗口客户端。
 */
public class DesktopGameWindows implements GameWindowHost {
    private record Key(String game, String mode) { }
    private static final class Entry {
        private Lwjgl3Window window;
        private GameCoreClient client;
        private boolean closing;
    }
    private final Map<Key, Entry> entries = new LinkedHashMap<>();

    @Override public void open(GameCoreClient owner, String gameId, String modeId) {
        var game = owner.registry().require(gameId);
        var mode = game.requireMode(modeId);
        Key key = new Key(gameId, modeId);
        Entry existing = entries.get(key);
        if (existing != null) {
            if (existing.window != null && existing.window.getWindowHandle() != 0 && !existing.closing) {
                if (existing.window.isIconified()) { existing.window.restoreWindow(); }
                existing.window.focusWindow();
            }
            return;
        }
        Lwjgl3Window lobby = ((Lwjgl3Graphics) Gdx.graphics).getWindow();
        Entry entry = new Entry();
        entry.client = createClient(owner, gameId, modeId, () -> {
            if (entry.window != null && !entry.closing) { entry.closing = true; entry.window.closeWindow(); }
        }, () -> {
            entries.remove(key, entry);
            if (!owner.isDisposed()) { lobby.focusWindow(); }
        });
        entries.put(key, entry);
        Lwjgl3WindowConfiguration configuration = new Lwjgl3WindowConfiguration();
        configuration.setTitle(game.name() + " · " + mode.name());
        configuration.setWindowedMode(1280, 800);
        configuration.setWindowSizeLimits(960, 640, -1, -1);
        configuration.setResizable(true);
        configuration.setMaximized(true);
        configuration.useVsync(owner.settings().vsync());
        configuration.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override public void created(Lwjgl3Window window) {
                entry.window = window; entry.client.setWindowScheduler(window::postRunnable);
            }
            @Override public void focusLost() { entry.client.pause(); }
            @Override public boolean closeRequested() { entry.closing = true; return true; }
        });
        try {
            entry.window = ((Lwjgl3Application) Gdx.app).newWindow(entry.client, configuration);
        } catch (RuntimeException failure) {
            entries.remove(key, entry);
            throw failure;
        }
    }

    /** 图形验收可注入子类监听真实窗口；正式路径直接创建相同的独立游戏客户端。 */
    protected GameCoreClient createClient(GameCoreClient owner, String gameId, String modeId,
                                         Runnable close, Runnable disposed) {
        return GameCoreClient.gameWindow(owner, gameId, modeId, close, disposed);
    }
    int count() { return entries.size(); }
    Lwjgl3Window window(String gameId, String modeId) {
        Entry entry = entries.get(new Key(gameId, modeId)); return entry == null ? null : entry.window;
    }
}
