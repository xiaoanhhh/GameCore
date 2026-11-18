package dev.gamecore.api;

/** 游戏目录中的模式元数据，不含输入、图形或会话实现。 */
public record GameMode(String id, String name, String description) {
    public GameMode {
        if (id == null || !id.matches("[a-z][a-z0-9-]*") || name == null || name.isBlank()
                || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Mode requires a stable id, name and description");
        }
    }
}
