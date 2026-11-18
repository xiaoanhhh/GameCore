package dev.gamecore.client.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.gamecore.client.registry.GameRegistry;
import org.junit.jupiter.api.Test;

/** 验证设置返回路径和模式白名单，避免 UI 将不合法状态带入预览。 */
class LobbyNavigationTest {
    @Test void returnsFromSettingsToItsOrigin() {
        LobbyNavigation navigation = new LobbyNavigation(GameRegistry.builtIn());
        navigation.detail("tank");
        navigation.settings();
        navigation.settings();
        navigation.back();
        assertEquals(LobbyNavigation.Page.DETAIL, navigation.current().page());
        assertEquals("tank", navigation.current().gameId());
        navigation.back();
        assertEquals(LobbyNavigation.Page.HOME, navigation.current().page());
    }

    @Test void previewCarriesModeAndReturnsToDetailsOrHome() {
        LobbyNavigation navigation = new LobbyNavigation(GameRegistry.builtIn());
        navigation.preview("snake", "obstacles");
        assertEquals("obstacles", navigation.current().modeId());
        navigation.back();
        assertEquals(LobbyNavigation.Page.DETAIL, navigation.current().page());
        navigation.home();
        navigation.settings();
        navigation.back();
        assertEquals(LobbyNavigation.Page.HOME, navigation.current().page());
    }

    @Test void invalidNavigationDoesNotChangeCurrentPage() {
        LobbyNavigation navigation = new LobbyNavigation(GameRegistry.builtIn());
        var before = navigation.current();
        assertThrows(IllegalArgumentException.class, () -> navigation.detail("missing"));
        assertThrows(IllegalArgumentException.class, () -> navigation.preview("tank", "classic"));
        assertEquals(before, navigation.current());
    }
    @Test void tankOnlineIsSeparateFromExistingLocalModesAndCanReturnHome() {
        var navigation = new LobbyNavigation(GameRegistry.builtIn());
        for (String mode : java.util.List.of("duel", "bot", "online")) {
            navigation.preview("tank", mode); assertEquals(mode, navigation.current().modeId());
            navigation.home(); assertEquals(LobbyNavigation.Page.HOME, navigation.current().page());
        }
    }
}
