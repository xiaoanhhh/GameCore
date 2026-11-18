package dev.gamecore.api.online;
import dev.gamecore.api.session.GameSession;
/** 与现有回放SessionFactory并存的在线配置适配口，实例只在Room所属线程创建。 */
@FunctionalInterface public interface GameSessionFactory<I, S> { GameSession<I, S> create(GameRuleConfig config, long seed); }
