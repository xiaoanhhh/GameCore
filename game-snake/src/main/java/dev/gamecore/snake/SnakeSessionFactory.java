package dev.gamecore.snake;

import dev.gamecore.api.replay.SessionFactory;
import java.util.Objects;

/** 固定初始出生点/地图由模式决定；配置与种子足以重建新局，不提供任意快照恢复。 */
public final class SnakeSessionFactory implements SessionFactory<SnakeSessionFactory.Config, Direction, SnakeSnapshot> {
    public record Config(SnakeMode mode) { public Config { Objects.requireNonNull(mode); } }
    @Override public Rules rules() { return new Rules("snake", "1", SnakeGameSession.TICKS_PER_SECOND); }
    @Override public SnakeGameSession create(Config configuration, long seed) { return new SnakeGameSession(configuration.mode(), seed); }
}
