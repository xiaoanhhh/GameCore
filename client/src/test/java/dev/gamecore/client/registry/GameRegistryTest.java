package dev.gamecore.client.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.gamecore.api.GameDescriptor;
import dev.gamecore.api.GameMode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 注册、搜索与扩展目录的纯 Java 测试。 */
class GameRegistryTest {
    @Test void preservesOrderAndSearchesBothLanguages() {
        GameRegistry registry = GameRegistry.builtIn();
        assertEquals(List.of("snake", "tank", "fight"), registry.all().stream().map(GameDescriptor::id).toList());
        assertEquals("snake", registry.search(" SNAKE ").getFirst().id());
        assertEquals("tank", registry.search("坦克").getFirst().id());
        assertEquals("fight", registry.search("奶蛙").getFirst().id());
        assertEquals("fight", registry.search("终极格斗").getFirst().id());
        assertEquals(List.of("bot", "duel", "multiplayer"), registry.require("fight").modes().stream().map(GameMode::id).toList());
        assertEquals(List.of("duel", "bot", "online", "multiplayer"), registry.require("tank").modes().stream().map(GameMode::id).toList());
        assertEquals(List.of("classic", "obstacles", "duo", "multiplayer"), registry.require("snake").modes().stream().map(GameMode::id).toList());
        assertEquals(3, registry.search(" ").size());
        assertEquals(0, registry.search("unknown").size());
    }

    @Test void rejectsDuplicatesAndUnknownIds() {
        GameRegistry registry = GameRegistry.builtIn();
        GameDescriptor snake = registry.require("snake");
        assertThrows(IllegalArgumentException.class, () -> registry.register(() -> snake));
        assertThrows(IllegalArgumentException.class, () -> registry.require("unknown"));
        assertEquals(3, registry.all().size());
    }

    @Test void acceptsNewCatalogEntryWithoutChangingConsumer() {
        GameRegistry registry = GameRegistry.builtIn();
        registry.register(() -> new GameDescriptor("third", "Third", "第三款", "简介", "介绍",
                List.of(new GameMode("solo", "单人", "介绍"))));
        assertEquals(4, registry.all().size());
        assertEquals("third", registry.search("第三款").getFirst().id());
        assertThrows(UnsupportedOperationException.class, () -> registry.all().clear());
    }
}
