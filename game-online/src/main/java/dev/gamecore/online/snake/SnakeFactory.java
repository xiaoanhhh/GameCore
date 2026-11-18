package dev.gamecore.online.snake;
import dev.gamecore.api.online.*;
import dev.gamecore.snake.*;
import dev.gamecore.protocol.GameProtocol;
import dev.gamecore.online.ProtocolValues;
import java.util.*;
/** 在线双人复用SnakeGameSession/同时碰撞/服务器随机食物；无客户端位置写入。 */
public final class SnakeFactory implements GameFactory<Direction,SnakeSnapshot> {
    private final GameType type=new GameType("snake","1","snake/1",2,2,20,20);
    private final SnakeSnapshotCodec snapshots=new SnakeSnapshotCodec();
    private final GameInputCodec<Direction> inputs=new GameInputCodec<>() {
        @Override public Direction decode(byte[] bytes){try{var b=GameProtocol.GameInput.parseFrom(bytes);ProtocolValues.known(b);if(!b.hasSnake()){throw new IllegalArgumentException("Wrong game input");}
            var c=b.getSnake();ProtocolValues.known(c);if(c.getDirection()==GameProtocol.SnakeDirection.SNAKE_NONE||c.getDirection()==GameProtocol.SnakeDirection.UNRECOGNIZED){throw new IllegalArgumentException("Invalid direction");}
            return Direction.valueOf(c.getDirection().name());}catch(com.google.protobuf.InvalidProtocolBufferException e){throw new IllegalArgumentException("Malformed input",e);}}
        @Override public byte[] encode(Direction d){return GameProtocol.GameInput.newBuilder().setSnake(GameProtocol.SnakeInput.newBuilder().setDirection(GameProtocol.SnakeDirection.valueOf(d.name()))).build().toByteArray();}
        @Override public Optional<Direction> held(Direction input){return Optional.empty();}
    };
    @Override public GameType type(){return type;}
    @Override public GameRuleConfig select(GameRuleConfig current,int slot,Map<String,String> choices){
        if(slot!=0||!choices.keySet().equals(Set.of("map"))){throw new IllegalArgumentException("Map ownership");}
        var next=new GameRuleConfig(current.players(),current.roomTps(),choices);validate(next);return next;
    }
    @Override public GameRuleConfig defaults(){return new GameRuleConfig(2,20,Map.of());}
    @Override public void validate(GameRuleConfig c){c.require(type);if(!c.options().isEmpty()&&(!c.options().keySet().equals(Set.of("map"))||!Set.of("clear","obstacles").contains(c.options().get("map")))){throw new IllegalArgumentException("Unknown Snake option");}}
    @Override public GameSessionFactory<Direction,SnakeSnapshot> sessions(){return(c,seed)->{validate(c);return "obstacles".equals(c.options().get("map"))?SnakeGameSession.duelWithObstacles(seed):new SnakeGameSession(SnakeMode.DUO,seed);};}
    @Override public GameInputCodec<Direction> inputs(){return inputs;}
    @Override public GameSnapshotCodec<SnakeSnapshot> snapshots(){return snapshots;}
    @Override public Direction bot(SnakeSnapshot s,int slot,long step,boolean active){
        var player=s.players().get(slot); if(!active){return player.direction();}
        var blocked=new HashSet<>(s.obstacles());for(var p:s.players()){blocked.addAll(p.body());}
        var directions=new ArrayList<>(List.of(Direction.values()));
        directions.sort(Comparator.comparingInt(d->{var f=s.food();var h=player.head();return f==null?0:Math.abs(h.x()+d.dx()-f.x())+Math.abs(h.y()+d.dy()-f.y());}));
        for(var d:directions){if(d.dx()==-player.direction().dx()&&d.dy()==-player.direction().dy()){continue;}var n=new Cell(player.head().x()+d.dx(),player.head().y()+d.dy());
            if(n.x()>=0&&n.y()>=0&&n.x()<20&&n.y()<20&&!blocked.contains(n)){return d;}}
        return player.direction();
    }
}
