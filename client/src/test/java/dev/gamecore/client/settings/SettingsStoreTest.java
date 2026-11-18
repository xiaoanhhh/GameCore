package dev.gamecore.client.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Input;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 真文件读写验证；测试路径完全隔离，不覆盖用户配置。 */
class SettingsStoreTest {
    @TempDir Path directory;

    @Test void missingFileUsesDefaultsWithoutWriting() {
        Path path = directory.resolve("nested/settings.properties");
        SettingsStore.LoadResult loaded = new SettingsStore(path).load();
        assertEquals(ClientSettings.defaults(), loaded.settings());
        assertTrue(loaded.notice().isEmpty());
        assertFalse(Files.exists(path));
    }

    @Test void settingsSurviveNewStoreInstance() throws IOException {
        Path path = directory.resolve("nested/settings.properties");
        ClientSettings custom = new ClientSettings(35, ClientSettings.Resolution.SMALL, false,
                KeyBindings.defaults().with(KeyBindings.Action.UP, Input.Keys.I));
        new SettingsStore(path).save(custom);
        assertEquals(custom, new SettingsStore(path).load().settings());
        try (var files = Files.list(path.getParent())) {
            assertEquals(1, files.count(), "No temporary files remain after successful save");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"corrupt", "version=99", "version=1\nvolume=-1", "version=1\nvsync=maybe", "bad=\\uZZZZ"})
    void invalidSettingsFallBackWithVisibleNotice(String content) throws IOException {
        Path path = directory.resolve("broken.properties");
        Files.writeString(path, content);
        SettingsStore.LoadResult result = new SettingsStore(path).load();
        assertEquals(ClientSettings.defaults(), result.settings());
        assertFalse(result.notice().isBlank());
        assertEquals(content, Files.readString(path), "Reading must not overwrite malformed settings");
    }

    @Test void writeFailureIsPropagated() throws IOException {
        Path blocked = directory.resolve("file-not-directory");
        Files.writeString(blocked, "keep");
        assertThrows(IOException.class, () -> new SettingsStore(blocked.resolve("settings.properties")).save(ClientSettings.defaults()));
        assertEquals("keep", Files.readString(blocked));
    }

    @Test void rejectsOutOfRangeVolume() {
        assertThrows(IllegalArgumentException.class, () -> new ClientSettings(-1, ClientSettings.Resolution.MEDIUM, true, KeyBindings.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new ClientSettings(101, ClientSettings.Resolution.MEDIUM, true, KeyBindings.defaults()));
    }
}
