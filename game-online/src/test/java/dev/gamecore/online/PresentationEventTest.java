package dev.gamecore.online;

import dev.gamecore.api.event.GameEvent;
import dev.gamecore.protocol.GameProtocol;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 展示事件严格还原并限制非法数值；不产生额外规则输入。 */
class PresentationEventTest {
    @Test void actualEffectsAndScoresRoundTrip(){
        for(GameEvent value:java.util.List.of(new GameEvent.Effect(7,GameEvent.Effect.Kind.HIT,1,3,4,180),new GameEvent.ScoreChanged(9,0,20,30),new GameEvent.HealthChanged(10,1,5,4),new GameEvent.MatchEnded(11,"DRAW"))){assertEquals(value,ProtocolValues.event(ProtocolValues.event(value)));}
    }
    @Test void unknownEffectsAndNonFiniteValuesAreRejected(){
        assertThrows(IllegalArgumentException.class,()->ProtocolValues.event(GameProtocol.GameEvent.newBuilder().setKind("INVENTED").build()));
        assertThrows(IllegalArgumentException.class,()->ProtocolValues.event(GameProtocol.GameEvent.newBuilder().setKind("HIT").setX(Double.NaN).build()));
    }
}
