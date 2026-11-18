package dev.gamecore.online.tank;
import dev.gamecore.api.online.*;
import dev.gamecore.tank.*;
import dev.gamecore.protocol.GameProtocol;
import dev.gamecore.online.ProtocolValues;
import java.util.*;
/** Tank仅提供注册/输入/快照适配，原TankSession仍是唯一生产规则。 */
public final class TankFactory implements GameFactory<TankCommand,TankSnapshot> {
    // 注册器创建时加载一次，房间Tick/首个Join不访问地图文件。
    private static final Map<String,TankMap> MAPS=Map.of("crossfire",load("crossfire"),"ruins",load("ruins"),"open",load("open"));
    private final GameType type = new GameType("tank","1","tank/1",2,2,60,30);
    private final TankSnapshotCodec snapshots = new TankSnapshotCodec();
    private final GameInputCodec<TankCommand> inputs = new GameInputCodec<>() {
        @Override public Optional<TankCommand> neutral() { return Optional.of(TankCommand.IDLE); }
        @Override public TankCommand decode(byte[] bytes) {
            try { var b = GameProtocol.GameInput.parseFrom(bytes); ProtocolValues.known(b); if (!b.hasTank()) { throw new IllegalArgumentException("Wrong game input"); }
                var c = b.getTank(); ProtocolValues.known(c); return new TankCommand(c.getThrottle(), c.getTurn(), c.getFire());
            } catch (com.google.protobuf.InvalidProtocolBufferException e) { throw new IllegalArgumentException("Malformed input",e); }
        }
        @Override public byte[] encode(TankCommand command) { return GameProtocol.GameInput.newBuilder().setTank(GameProtocol.TankInput.newBuilder().setThrottle(command.throttle()).setTurn(command.turn()).setFire(command.fire())).build().toByteArray(); }
        @Override public Optional<TankCommand> held(TankCommand input) { return Optional.of(input); }
    };
    @Override public GameType type() { return type; }
    @Override public GameRuleConfig select(GameRuleConfig current,int slot,Map<String,String> choices){
        if(slot!=0||!choices.keySet().equals(Set.of("map"))||!Set.of("crossfire","ruins").contains(choices.get("map"))){throw new IllegalArgumentException("Map ownership");}
        var next=new GameRuleConfig(current.players(),current.roomTps(),choices);validate(next);return next;
    }
    @Override public GameRuleConfig defaults() { return new GameRuleConfig(2,30,Map.of("map","crossfire")); }
    @Override public void validate(GameRuleConfig c) { c.require(type); if (!c.options().keySet().equals(Set.of("map")) || !Set.of("crossfire","ruins","open").contains(c.options().get("map"))) { throw new IllegalArgumentException("Unsupported Tank map"); } }
    public static TankMap map(String id) {
        var value=MAPS.get(id);if(value==null){throw new IllegalArgumentException("Unknown map");}return value;
    }
    private static TankMap load(String id) {
        if (id.equals("open")) { return new TankMap("open",32,16,new boolean[512],List.of(new TankMap.Spawn(new Vec2(3.5,7.5),0),new TankMap.Spawn(new Vec2(28.5,7.5),180)),List.of()); }
        try (var stream = TankFactory.class.getResourceAsStream("/maps/tank/" + id + ".tmx")) { if (stream == null) { throw new IllegalArgumentException("Missing map"); } return TmxArenaLoader.load(stream); }
        catch (java.io.IOException e) { throw new IllegalStateException("Map load failed",e); }
    }
    @Override public GameSessionFactory<TankCommand,TankSnapshot> sessions() { return (config,seed) -> { validate(config); return new TankSession(map(config.options().get("map")),TankMode.DUEL,TankBot.Level.NORMAL,seed); }; }
    @Override public GameInputCodec<TankCommand> inputs() { return inputs; }
    @Override public GameSnapshotCodec<TankSnapshot> snapshots() { return snapshots; }
    @Override public TankCommand bot(TankSnapshot snapshot,int slot,long step,boolean active) { return new TankCommand(active ? 1 : 0,active ? 1 : 0,active); }
}
