package dev.gamecore.client.navigation;

import dev.gamecore.client.registry.GameRegistry;
import java.util.Objects;

/** 可无图形测试的路由状态。设置页记住来路，预览必须使用已注册的游戏和模式。 */
public final class LobbyNavigation {
    public enum Page { HOME, DETAIL, SETTINGS, PREVIEW }

    public record Destination(Page page, String gameId, String modeId) {
    }

    private final GameRegistry registry;
    private Destination current = new Destination(Page.HOME, null, null);
    private Destination settingsReturn = current;

    public LobbyNavigation(GameRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public Destination current() {
        return current;
    }

    public void home() {
        current = new Destination(Page.HOME, null, null);
    }

    public void detail(String gameId) {
        registry.require(gameId);
        current = new Destination(Page.DETAIL, gameId, null);
    }

    public void preview(String gameId, String modeId) {
        registry.require(gameId).requireMode(modeId);
        current = new Destination(Page.PREVIEW, gameId, modeId);
    }

    public void settings() {
        if (current.page() != Page.SETTINGS) {
            settingsReturn = current;
            current = new Destination(Page.SETTINGS, null, null);
        }
    }

    public void back() {
        current = switch (current.page()) {
            case SETTINGS -> settingsReturn;
            case PREVIEW -> new Destination(Page.DETAIL, current.gameId(), null);
            case DETAIL, HOME -> new Destination(Page.HOME, null, null);
        };
    }
}
