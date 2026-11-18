package dev.gamecore.api;

import java.util.List;
import java.util.Objects;

/** 可跨客户端和未来服务端读取的目录数据；防御性复制阻止外部修改模式列表。 */
public record GameDescriptor(String id, String name, String subtitle, String summary,
                             String description, List<GameMode> modes) {
    public GameDescriptor {
        if (id == null || !id.matches("[a-z][a-z0-9-]*") || name == null || name.isBlank()
                || subtitle == null || subtitle.isBlank() || summary == null || summary.isBlank()
                || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Game metadata must be complete");
        }
        modes = List.copyOf(Objects.requireNonNull(modes, "modes"));
        if (modes.isEmpty() || modes.stream().map(GameMode::id).distinct().count() != modes.size()) {
            throw new IllegalArgumentException("Game modes must be nonempty and have unique ids");
        }
    }

    public GameMode requireMode(String modeId) {
        return modes.stream().filter(mode -> mode.id().equals(modeId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown mode: " + modeId));
    }
}
