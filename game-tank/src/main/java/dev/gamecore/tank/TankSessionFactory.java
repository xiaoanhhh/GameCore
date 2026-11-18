package dev.gamecore.tank;

import dev.gamecore.api.replay.SessionFactory;
import java.util.Objects;

/** 配置内携带完整不可变逻辑地图，而不只存容易发生内容漂移的地图 ID；Bot 状态由难度和种子重建。 */
public final class TankSessionFactory implements SessionFactory<TankSessionFactory.Config, TankCommand, TankSnapshot> {
    public record Config(TankMap map, TankMode mode, TankBot.Level level) {
        public Config { Objects.requireNonNull(map); Objects.requireNonNull(mode); Objects.requireNonNull(level); }
    }
    @Override public Rules rules() { return new Rules("tank", "1", TankGame.TICKS_PER_SECOND); }
    @Override public TankSession create(Config configuration, long seed) {
        return new TankSession(configuration.map(), configuration.mode(), configuration.level(), seed);
    }
}
