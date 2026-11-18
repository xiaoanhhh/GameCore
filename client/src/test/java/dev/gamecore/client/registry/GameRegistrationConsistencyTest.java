package dev.gamecore.client.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.gamecore.api.GameDescriptor;
import dev.gamecore.client.network.OnlineGameViews;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 一款游戏要在大厅目录、在线注册表、在线展示三处分别登记。
 * 这里比较三处的游戏 ID 集合，防止只登记了其中一处，导致大厅可见却无法联机（或相反）。
 */
class GameRegistrationConsistencyTest {
    private static Set<String> catalogIds() {
        return GameRegistry.builtIn().all().stream().map(GameDescriptor::id).collect(Collectors.toSet());
    }

    private static Set<String> onlineIds() {
        return dev.gamecore.online.GameRegistry.builtIn().all().stream()
                .map(factory -> factory.type().id()).collect(Collectors.toSet());
    }

    @Test
    void catalogAndOnlineRegistryListTheSameGames() {
        assertEquals(catalogIds(), onlineIds());
    }

    @Test
    void everyOnlineGameHasAnOnlineView() {
        for (String id : onlineIds()) {
            assertTrue(OnlineGameViews.supports(id), "缺少在线展示：" + id);
        }
        assertFalse(OnlineGameViews.supports("unknown"));
    }
}
