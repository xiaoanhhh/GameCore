package dev.gamecore.online;
import dev.gamecore.online.fight.FightFactory;
import dev.gamecore.online.runtime.*;
import dev.gamecore.api.online.GameRuleConfig;
import dev.gamecore.fight.*;
import dev.gamecore.api.session.*;
import dev.gamecore.protocol.GameProtocol;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FightAdapterTest {
    @Test void fullRulesMatchUnadaptedSessionAcrossFiveMapsToResult(){var f=new FightFactory();
        for(var map:FightMap.values()){
            var config=new GameRuleConfig(2,60,Map.of("map",map.name(),"one","NAIWA","two","FEIDUDU"));
            var original=new FightSessionFactory().create(new FightSessionFactory.Config(FighterKind.NAIWA,FighterKind.FEIDUDU,FightSessionFactory.Mode.DUEL,map),42);
            try(var adapted=GameInstance.create(f,config,42)){original.start();adapted.start();long sequence=0;int damage=0;
                for(int tick=1;tick<=FightSession.MATCH_TICKS;tick++){
                    var inputs=new ArrayList<AppliedInput>();for(int player=0;player<2;player++){
                        var command=new FightCommand(player==0?1:-1,tick%173==0,tick%19==0,tick%61==0,tick%239==0,tick%83<8,tick%191==0,tick%83<8,tick%211==0);
                        long order=++sequence;assertTrue(original.submitInput(new PlayerInput<>(tick,player,order,command)));inputs.add(new AppliedInput(player,order,order,f.inputs().encode(command)));
                    }original.tick();var frame=adapted.step(inputs);var decoded=f.snapshots().decode(frame.snapshot());
                    assertEquals(original.snapshot(),decoded,"map="+map.name()+" tick="+tick);assertArrayEquals(frame.snapshot(),f.snapshots().encode(decoded));
                    damage=decoded.fighters().stream().mapToInt(FightSnapshot.Fighter::totalDamage).sum();if(original.isFinished()){break;}
                }assertTrue(original.isFinished());assertTrue(damage>0,"Mixed workload must cause real damage");
            }
        }
    }
    @Test void heldInputStripsPulsesAndMalformedValuesFail(){var f=new FightFactory();var c=new FightCommand(1,true,true,true,true,true,true,true,true);var held=f.inputs().held(c).orElseThrow();assertEquals(1,held.move());assertTrue(held.guard());assertFalse(held.jump());assertFalse(held.punch());assertFalse(held.ranged());assertFalse(held.ultimate());assertFalse(held.dash());assertFalse(held.laugh());
        assertThrows(IllegalArgumentException.class,()->f.inputs().decode(GameProtocol.GameInput.newBuilder().setFight(GameProtocol.FightInput.newBuilder().setMove(2)).build().toByteArray()));assertThrows(IllegalArgumentException.class,()->f.inputs().decode(new dev.gamecore.online.snake.SnakeFactory().inputs().encode(dev.gamecore.snake.Direction.UP)));
    }
    @Test void corruptedGeometryAndNanNeverReachRenderer() throws Exception{var f=new FightFactory();var s=f.sessions().create(f.defaults(),42).snapshot();var all=GameProtocol.GameSnapshot.parseFrom(f.snapshots().encode(s));
        var nan=all.toBuilder().setFight(all.getFight().toBuilder().setFighters(0,all.getFight().getFighters(0).toBuilder().setX(Double.NaN))).build();assertThrows(IllegalArgumentException.class,()->f.snapshots().decode(nan.toByteArray()));
        var geometry=all.toBuilder().setFight(all.getFight().toBuilder().clearPlates()).build();assertThrows(IllegalArgumentException.class,()->f.snapshots().decode(geometry.toByteArray()));
    }
}
