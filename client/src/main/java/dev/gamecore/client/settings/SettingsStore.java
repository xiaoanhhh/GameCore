package dev.gamecore.client.settings;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Properties;

/** UTF-8 本地设置存储。写入临时文件后替换，解析异常回退并向界面返回提示。 */
public final class SettingsStore {
    public record LoadResult(ClientSettings settings, String notice) { }
    private final Path path;

    public SettingsStore(Path path) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath();
    }

    public LoadResult load() {
        if (!Files.exists(path)) {
            return new LoadResult(ClientSettings.defaults(), "");
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Properties values = new Properties();
            values.load(reader);
            if (!"1".equals(values.getProperty("version"))) {
                throw new IllegalArgumentException("Unsupported settings version");
            }
            String sync = values.getProperty("vsync");
            if (!"true".equals(sync) && !"false".equals(sync)) {
                throw new IllegalArgumentException("Invalid vsync flag");
            }
            return new LoadResult(new ClientSettings(Integer.parseInt(values.getProperty("volume")),
                    ClientSettings.Resolution.valueOf(values.getProperty("resolution")), Boolean.parseBoolean(sync),
                    new KeyBindings(Integer.parseInt(values.getProperty("up")), Integer.parseInt(values.getProperty("down")),
                            Integer.parseInt(values.getProperty("left")), Integer.parseInt(values.getProperty("right")))), "");
        } catch (IOException | IllegalArgumentException | NullPointerException exception) {
            return new LoadResult(ClientSettings.defaults(), "设置文件无法读取，已使用默认值。重新保存会覆盖原设置。");
        }
    }

    public void save(ClientSettings settings) throws IOException {
        Objects.requireNonNull(settings, "settings");
        Files.createDirectories(path.getParent());
        Properties values = new Properties();
        values.setProperty("version", "1");
        values.setProperty("volume", Integer.toString(settings.volume()));
        values.setProperty("resolution", settings.resolution().name());
        values.setProperty("vsync", Boolean.toString(settings.vsync()));
        for (KeyBindings.Action action : KeyBindings.Action.values()) {
            values.setProperty(action.name().toLowerCase(java.util.Locale.ROOT), Integer.toString(settings.keys().key(action)));
        }
        Path temporary = Files.createTempFile(path.getParent(), "gamecore-settings-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                values.store(writer, "GameCore preferences");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
