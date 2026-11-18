package dev.gamecore.fight;

import dev.gamecore.api.replay.SessionFactory;
import java.util.Objects;

/** 配置、种子与规则版本足够重建首局；不读取文件、时间或设备。 */
public final class FightSessionFactory implements SessionFactory<FightSessionFactory.Config, FightCommand, FightSnapshot> {
    public enum Mode { BOT, DUEL;
        public static Mode fromId(String id) {
            return switch (id) { case "bot" -> BOT; case "duel" -> DUEL;
                default -> throw new IllegalArgumentException("Unknown fight mode: " + id); };
        }
    }
    public record Config(FighterKind one, FighterKind two, Mode mode, FightMap map) {
        /** 原三参数入口继续使用霓虹天城，避免无关调用点被迫改写。 */
        public Config(FighterKind one, FighterKind two, Mode mode) { this(one,two,mode,FightMap.NEON); }
        public Config { Objects.requireNonNull(one); Objects.requireNonNull(two); Objects.requireNonNull(mode); Objects.requireNonNull(map); }
    }
    @Override public Rules rules() { return new Rules("fight", "7", FightSession.TICKS_PER_SECOND); }
    @Override public FightSession create(Config configuration, long seed) { return new FightSession(configuration, seed); }
}
