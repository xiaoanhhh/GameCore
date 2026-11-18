package dev.gamecore.online;
import dev.gamecore.api.online.*;
import dev.gamecore.online.runtime.*;
import dev.gamecore.online.tank.TankFactory;
import dev.gamecore.online.snake.SnakeFactory;
import dev.gamecore.snake.Direction;
import dev.gamecore.tank.TankCommand;
import dev.gamecore.protocol.GameProtocol;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AdapterTest {
    @Test void registryAndVersions(){var games=GameRegistry.builtIn();assertEquals(3,games.all().size());assertThrows(IllegalArgumentException.class,()->new GameRegistry(List.of(new TankFactory(),new TankFactory())));assertThrows(IllegalArgumentException.class,()->games.require("unknown"));}
    @Test void codecsRejectWrongGameAndIllegalValues(){var t=new TankFactory();var s=new SnakeFactory();
        assertThrows(IllegalArgumentException.class,()->t.inputs().decode(s.inputs().encode(Direction.UP)));assertThrows(IllegalArgumentException.class,()->s.inputs().decode(t.inputs().encode(TankCommand.IDLE)));
        assertThrows(IllegalArgumentException.class,()->t.inputs().decode(GameProtocol.GameInput.newBuilder().setTank(GameProtocol.TankInput.newBuilder().setThrottle(2)).build().toByteArray()));
        assertThrows(IllegalArgumentException.class,()->s.inputs().decode(GameProtocol.GameInput.newBuilder().setSnake(GameProtocol.SnakeInput.newBuilder().setDirectionValue(999)).build().toByteArray()));
        assertThrows(IllegalArgumentException.class,()->s.validate(new GameRuleConfig(1,20,Map.of())));
    }
    private static <I,S> void roundtrip(GameFactory<I,S> f){var c=f.defaults();var a=GameInstance.create(f,c,77);var b=GameInstance.create(f,c,77);try(a;b){
        var initial=a.initial();assertArrayEquals(initial.snapshot(),f.snapshots().encode(f.snapshots().decode(initial.snapshot())));a.start();b.start();
        for(int i=0;i<30;i++){var x=a.step(List.of());var y=b.step(List.of());assertArrayEquals(x.snapshot(),y.snapshot());assertArrayEquals(x.snapshot(),f.snapshots().encode(f.snapshots().decode(x.snapshot())));if(x.state()==dev.gamecore.api.session.SessionState.FINISHED){break;}}
    }}
    @Test void tankRoundtripAndDeterminism(){roundtrip(new TankFactory());}
    @Test void tankFirstProjectileZeroIdRoundtripsAndUnsignedGarbageIsRejected() throws Exception{var f=new TankFactory();var s=f.sessions().create(f.defaults(),42);s.start();s.submitInput(new dev.gamecore.api.session.PlayerInput<>(1,0,1,new TankCommand(0,0,true)));s.tick();var original=s.snapshot();assertEquals(0,original.bullets().getFirst().id());assertEquals(original,f.snapshots().decode(f.snapshots().encode(original)));
        var wire=GameProtocol.GameSnapshot.parseFrom(f.snapshots().encode(original));var bad=wire.toBuilder().setTank(wire.getTank().toBuilder().setPlayers(0,wire.getTank().getPlayers(0).toBuilder().setHealth(-1))).build();assertThrows(IllegalArgumentException.class,()->f.snapshots().decode(bad.toByteArray()));
        var nan=wire.toBuilder().setTank(wire.getTank().toBuilder().setPlayers(0,wire.getTank().getPlayers(0).toBuilder().setVelocity(GameProtocol.Point.newBuilder().setX(Double.NaN)))).build();assertThrows(IllegalArgumentException.class,()->f.snapshots().decode(nan.toByteArray()));
    }
    @Test void snakeRoundtripAndDeterminism(){roundtrip(new SnakeFactory());}
    @Test void trustedSnakeObstacleScenarioPreservesDuoSpawnsAndDeterministicFood(){var f=new SnakeFactory();var config=new GameRuleConfig(2,20,Map.of("map","obstacles"));var a=f.sessions().create(config,42).snapshot();var b=f.sessions().create(config,42).snapshot();assertEquals(a,b);assertEquals(2,a.players().size());assertEquals(16,a.obstacles().size());for(var p:a.players()){for(var cell:p.body()){assertFalse(a.obstacles().contains(cell));}}assertFalse(a.obstacles().contains(a.food()));assertArrayEquals(f.snapshots().encode(a),f.snapshots().encode(b));}
    @Test void snakeSimultaneousHeadSwap(){var f=new SnakeFactory();try(var g=GameInstance.create(f,f.defaults(),42)){g.start();GameStep s=g.initial();for(int n=0;n<200&&!s.state().name().equals("FINISHED");n++){s=g.step(List.of());}
        var snapshot=f.snapshots().decode(s.snapshot());assertEquals("FINISHED",s.state().name());assertFalse(snapshot.players().get(0).alive());assertFalse(snapshot.players().get(1).alive());assertEquals("DRAW",snapshot.result().name());assertTrue(s.events().stream().anyMatch(e->e.getKind().equals("MATCH_ENDED")));}}
    @Test void snapshotsAndAppliedInputsOwnTheirBytes(){byte[] bytes={1,2};var a=new AppliedInput(0,1,1,bytes);bytes[0]=9;assertEquals(1,a.command()[0]);a.command()[0]=8;assertEquals(1,a.command()[0]);}
    @Test void sessionRejectsForeignWriter() throws Exception{var f=new TankFactory();try(var g=GameInstance.create(f,f.defaults(),1)){var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();var thread=new Thread(()->{try{g.initial();}catch(Throwable e){failure.set(e);}});thread.start();thread.join();assertInstanceOf(IllegalStateException.class,failure.get());}}
}
