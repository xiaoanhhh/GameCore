package dev.gamecore.tank;
import dev.gamecore.api.event.GameEvent;
import java.util.ArrayList;
import java.util.List;
/** 纯只读通用事件投影；存储模型迁移不改变回放/网络事件语义。 */
public final class TankEvents {
    private TankEvents() { }
    public static List<GameEvent> between(TankSnapshot before, TankSnapshot after) {
        List<GameEvent> batch = new ArrayList<>();
        for (var event : after.events()) {
            switch (event.type()) {
                case FINISHED -> { }
                case DESTROYED -> {
                    batch.add(new GameEvent.PlayerDied(after.tick(), event.actor()));
                    batch.add(effect(after, event));
                }
                default -> batch.add(effect(after, event));
            }
        }
        for (int player = 0; player < 2; player++) {
            int old = before.tanks().get(player).health(), next = after.tanks().get(player).health();
            if (old != next) { batch.add(new GameEvent.HealthChanged(after.tick(), player, old, next)); }
        }
        if (after.state() == TankGame.State.FINISHED) { batch.add(new GameEvent.MatchEnded(after.tick(), after.result().name())); }
        return List.copyOf(batch);
    }
    private static GameEvent.Effect effect(TankSnapshot view, TankSnapshot.Event event) {
        double heading = event.actor() < 0 ? 0 : view.tanks().get(event.actor()).heading();
        return new GameEvent.Effect(view.tick(), GameEvent.Effect.Kind.valueOf(event.type().name()), event.actor(),
                event.position().x(), event.position().y(), heading);
    }
}
