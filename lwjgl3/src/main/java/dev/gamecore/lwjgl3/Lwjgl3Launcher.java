package dev.gamecore.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.Gdx;
import dev.gamecore.client.GameCoreClient;

/** 桌面程序唯一正式入口：配置窗口，再将生命周期交给 libGDX。 */
public final class Lwjgl3Launcher {
    private Lwjgl3Launcher() {
    }

    public static void main(String[] args) {
        new Lwjgl3Application(new GameCoreClient(new DesktopGameWindows()), configuration(DesktopSettings.defaults()));
    }

    /** 与冒烟程序复用真实启动配置，防止测试绕过正式窗口设置。 */
    static Lwjgl3ApplicationConfiguration configuration(DesktopSettings settings) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle(settings.title());
        config.setWindowedMode(settings.width(), settings.height());
        config.setResizable(true);
        config.useVsync(true);
        config.setForegroundFPS(settings.foregroundFps());
        // 切出窗口立即暂停，重新获得焦点后由玩家主动继续，防止后台误撞。
        config.setWindowListener(new Lwjgl3WindowAdapter() {
            private Lwjgl3Window window;
            @Override public void created(Lwjgl3Window created) {
                window = created;
                if (window.getListener() instanceof GameCoreClient client) { client.setWindowScheduler(window::postRunnable); }
            }
            @Override public void focusLost() {
                if (window != null) { window.getListener().pause(); }
            }
            @Override public boolean closeRequested() { Gdx.app.exit(); return true; }
        });
        return config;
    }
}
