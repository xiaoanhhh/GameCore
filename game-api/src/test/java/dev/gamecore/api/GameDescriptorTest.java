package dev.gamecore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 公共目录契约验证，整个模块不依赖 libGDX。 */
class GameDescriptorTest {
    private GameDescriptor game(List<GameMode> modes) {
        return new GameDescriptor("demo", "Demo", "示例", "简介", "完整介绍", modes);
    }

    @Test void snapshotsAndProtectsModeList() {
        List<GameMode> modes = new ArrayList<>(List.of(new GameMode("solo", "单人", "单人模式")));
        GameDescriptor descriptor = game(modes);
        modes.clear();
        assertEquals(1, descriptor.modes().size());
        assertThrows(UnsupportedOperationException.class, () -> descriptor.modes().clear());
    }

    @Test void rejectsEmptyOrDuplicateModes() {
        GameMode mode = new GameMode("solo", "单人", "单人模式");
        assertThrows(IllegalArgumentException.class, () -> game(List.of()));
        assertThrows(IllegalArgumentException.class, () -> game(List.of(mode, mode)));
    }

    @Test void rejectsIncompleteMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GameMode("Bad ID", "name", "description"));
        assertThrows(IllegalArgumentException.class, () -> new GameMode("valid", " ", "description"));
        assertThrows(IllegalArgumentException.class, () -> new GameDescriptor("demo", "", "x", "x", "x",
                List.of(new GameMode("solo", "name", "description"))));
    }

    @Test void resolvesModesAndRejectsUnknownMode() {
        GameMode mode = new GameMode("solo", "单人", "单人模式");
        assertEquals(mode, game(List.of(mode)).requireMode("solo"));
        assertThrows(IllegalArgumentException.class, () -> game(List.of(mode)).requireMode("unknown"));
    }
}
