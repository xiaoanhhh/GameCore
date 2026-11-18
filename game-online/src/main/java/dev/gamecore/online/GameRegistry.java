package dev.gamecore.online;
import dev.gamecore.api.online.GameFactory;
import java.util.*;
/** 服务端/客户端共享手动注册表；构造后不可变，不扫描JAR，不在网络层按gameType分支。 */
public final class GameRegistry {
    private final Map<String, GameFactory<?, ?>> games;
    public GameRegistry(List<GameFactory<?, ?>> factories) {
        var map = new LinkedHashMap<String, GameFactory<?, ?>>();
        for (var factory : factories) { if (map.putIfAbsent(factory.type().id(), factory) != null) { throw new IllegalArgumentException("Duplicate game"); } factory.validate(factory.defaults()); }
        games = Collections.unmodifiableMap(map);
    }
    public GameFactory<?, ?> require(String id) { var result = games.get(id); if (result == null) { throw new IllegalArgumentException("Unknown game: " + id); } return result; }
    public List<GameFactory<?, ?>> all() { return List.copyOf(games.values()); }
    public static GameRegistry builtIn() { return new GameRegistry(List.of(new dev.gamecore.online.tank.TankFactory(), new dev.gamecore.online.snake.SnakeFactory(),new dev.gamecore.online.fight.FightFactory())); }
}
