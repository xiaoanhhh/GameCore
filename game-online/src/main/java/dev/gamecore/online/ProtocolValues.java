package dev.gamecore.online;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import dev.gamecore.api.online.*;
import dev.gamecore.api.event.GameEvent;
import dev.gamecore.protocol.GameProtocol;
import java.util.*;
/** 协议值工具：容量、未知字段、规则身份和规范排序；不运行模拟，不引用Netty。 */
public final class ProtocolValues {
    public static final int VERSION = 2, MAX_FRAME = 65_536, MAX_INPUT = 256;
    private ProtocolValues() { }
    public static void known(Message message) { if (!message.getUnknownFields().asMap().isEmpty()) { throw new IllegalArgumentException("Unknown fields"); } }
    public static GameProtocol.Envelope envelope(long request, java.util.function.Consumer<GameProtocol.Envelope.Builder> payload) {
        var b = GameProtocol.Envelope.newBuilder().setProtocolVersion(VERSION).setRequestId(request); payload.accept(b); return b.build();
    }
    public static GameProtocol.GameType type(GameType type) { return GameProtocol.GameType.newBuilder().setGameId(type.id()).setGameVersion(type.gameVersion()).setRulesVersion(type.rulesVersion())
        .setMinPlayers(type.minPlayers()).setMaxPlayers(type.maxPlayers()).setSimulationTps(type.simulationTps()).setDefaultRoomTps(type.defaultRoomTps()).build(); }
    public static GameProtocol.RuleConfig config(GameRuleConfig value) {
        var b = GameProtocol.RuleConfig.newBuilder().setPlayers(value.players()).setRoomTps(value.roomTps());
        value.options().forEach((k,v) -> b.addOptions(GameProtocol.Option.newBuilder().setKey(k).setValue(v))); return b.build();
    }
    public static GameRuleConfig config(GameProtocol.RuleConfig value) {
        var options = new TreeMap<String,String>(); for (var option : value.getOptionsList()) { if (options.putIfAbsent(option.getKey(),option.getValue()) != null) { throw new IllegalArgumentException("Duplicate option"); } }
        return new GameRuleConfig(value.getPlayers(), value.getRoomTps(), options);
    }
    public static byte[] bytes(MessageLite message) { if (message.getSerializedSize() > MAX_FRAME - 1024) { throw new IllegalArgumentException("Snapshot too large"); } return message.toByteArray(); }
    public static void finite(double... values) { for (double value : values) { if (!Double.isFinite(value)) { throw new IllegalArgumentException("Non-finite snapshot"); } } }
    public static GameProtocol.GameEvent event(GameEvent event) {
        var b = GameProtocol.GameEvent.newBuilder().setTick(event.tick());
        switch (event) {
            case GameEvent.PlayerDied e -> b.setKind("PLAYER_DIED").setPlayer(e.player());
            case GameEvent.MatchEnded e -> b.setKind("MATCH_ENDED").setResult(e.result());
            case GameEvent.ScoreChanged e -> b.setKind("SCORE_CHANGED").setPlayer(e.player()).setPrevious(e.previous()).setCurrent(e.current());
            case GameEvent.HealthChanged e -> b.setKind("HEALTH_CHANGED").setPlayer(e.player()).setPrevious(e.previous()).setCurrent(e.current());
            case GameEvent.Effect e -> b.setKind(e.kind().name()).setPlayer(e.player()).setX(e.x()).setY(e.y()).setHeading(e.heading());
        }
        return b.build();
    }
    /** 展示端还原已发生的值事件，不能将事件重新送回模拟作为伤害或移动。 */
    public static GameEvent event(GameProtocol.GameEvent value){
        known(value);finite(value.getX(),value.getY(),value.getHeading());if(value.getTick()<0||value.getPlayer()<-1||value.getPlayer()>1){throw new IllegalArgumentException("Event identity");}
        return switch(value.getKind()){
            case "PLAYER_DIED" -> new GameEvent.PlayerDied(value.getTick(),value.getPlayer());
            case "MATCH_ENDED" -> new GameEvent.MatchEnded(value.getTick(),value.getResult());
            case "SCORE_CHANGED" -> new GameEvent.ScoreChanged(value.getTick(),value.getPlayer(),value.getPrevious(),value.getCurrent());
            case "HEALTH_CHANGED" -> new GameEvent.HealthChanged(value.getTick(),value.getPlayer(),value.getPrevious(),value.getCurrent());
            default -> new GameEvent.Effect(value.getTick(),GameEvent.Effect.Kind.valueOf(value.getKind()),value.getPlayer(),value.getX(),value.getY(),value.getHeading());
        };
    }
}
