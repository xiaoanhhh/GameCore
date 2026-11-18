package dev.gamecore.api;

/** 游戏目录注册契约；异构输入/快照的实际会话由各游戏的类型化 SessionFactory 创建。 */
@FunctionalInterface
public interface GameModule {
    GameDescriptor descriptor();

    default String id() {
        return descriptor().id();
    }

    default String displayName() {
        return descriptor().name();
    }
}
