package dev.gamecore.online.snake;
import dev.gamecore.api.online.GameSnapshotCodec;
import dev.gamecore.snake.*;
import dev.gamecore.protocol.GameProtocol;
import dev.gamecore.online.ProtocolValues;
import java.util.*;
/** 身体按头到尾保序，障碍按坐标排序；食物只由服务器快照发布。 */
public final class SnakeSnapshotCodec implements GameSnapshotCodec<SnakeSnapshot>{
    private static GameProtocol.Cell cell(Cell c){return GameProtocol.Cell.newBuilder().setX(c.x()).setY(c.y()).build();}
    private static Cell cell(GameProtocol.Cell c){if(c.getX()<-1||c.getY()<-1||c.getX()>20||c.getY()>20){throw new IllegalArgumentException("Invalid grid cell");}return new Cell(c.getX(),c.getY());}
    @Override public byte[] encode(SnakeSnapshot s){
        var v=GameProtocol.SnakeView.newBuilder().setMode(s.mode().name()).setState(s.state().name()).setResult(s.result().name()).setMovementTick(s.tick()).setSimulationTick(s.simulationTick()).setMovementCredit(s.movementCredit());
        for(var p:s.players()){var b=GameProtocol.SnakePlayer.newBuilder().setDirection(GameProtocol.SnakeDirection.valueOf(p.direction().name())).setScore(p.score()).setCollision(p.collision().name());for(var c:p.body()){b.addBody(cell(c));}v.addPlayers(b);}
        s.obstacles().stream().sorted(Comparator.comparingInt(Cell::y).thenComparingInt(Cell::x)).forEach(c->v.addObstacles(cell(c)));
        if(s.food()!=null){v.setFood(cell(s.food()));}return ProtocolValues.bytes(GameProtocol.GameSnapshot.newBuilder().setSnake(v).build());
    }
    @Override public SnakeSnapshot decode(byte[] bytes){try{var all=GameProtocol.GameSnapshot.parseFrom(bytes);ProtocolValues.known(all);if(!all.hasSnake()){throw new IllegalArgumentException("Wrong snapshot game");}var v=all.getSnake();
        ProtocolValues.known(v);if(v.getSimulationTick()<0||v.getMovementTick()<0||v.getPlayersCount()!=2||v.getObstaclesCount()>400||v.getMovementCredit()<0||v.getMovementCredit()>=20){throw new IllegalArgumentException("Invalid Snake snapshot");}
        var players=v.getPlayersList().stream().map(p->{ProtocolValues.known(p);if(p.getScore()<0||p.getScore()>4000||p.getBodyCount()<1||p.getBodyCount()>401){throw new IllegalArgumentException("Invalid body");}
            return new SnakeSnapshot.Player(p.getBodyList().stream().map(SnakeSnapshotCodec::cell).toList(),Direction.valueOf(p.getDirection().name()),p.getScore(),SnakeGame.Collision.valueOf(p.getCollision()));}).toList();
        var obstacles=new HashSet<Cell>();for(var c:v.getObstaclesList()){obstacles.add(cell(c));}
        return new SnakeSnapshot(SnakeMode.valueOf(v.getMode()),SnakeGame.State.valueOf(v.getState()),SnakeGame.Result.valueOf(v.getResult()),v.getMovementTick(),players,obstacles,v.hasFood()?cell(v.getFood()):null,v.getSimulationTick(),v.getMovementCredit());
        }catch(com.google.protobuf.InvalidProtocolBufferException e){throw new IllegalArgumentException("Malformed snapshot",e);}}
    @Override public String outcome(SnakeSnapshot s){return s.result().name();}
    @Override public int entityCount(SnakeSnapshot s){return s.players().stream().mapToInt(p->p.body().size()).sum()+(s.food()==null?0:1);}
}
