package dev.gamecore.client.effects;

import dev.gamecore.api.event.GameEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** ECS 实验纯 Java 验证：组件交集推进、寿命回收、容量和只读边界。 */
class EffectWorldTest {
    private static GameEvent.Effect event(GameEvent.Effect.Kind kind) { return new GameEvent.Effect(1, kind, 0, 5, 4, 0); }
    @Test void shotParticlesStartAtMuzzleMoveAndFadeWithoutMutatingEarlierSnapshot() {
        var world = new EffectWorld(); world.accept(event(GameEvent.Effect.Kind.SHOT));
        var before = world.snapshot(); assertEquals(5, before.size()); assertEquals(5.6, before.getFirst().x(), 1e-9);
        world.update(.09); var after = world.snapshot();
        assertTrue(after.getFirst().x() > before.getFirst().x()); assertEquals(.5, after.getFirst().alpha(), 1e-9);
        assertEquals(1, before.getFirst().alpha());
        assertThrows(UnsupportedOperationException.class, () -> before.clear());
        world.update(.1); assertEquals(0, world.size()); assertEquals(0, world.componentCount());
    }
    @Test void allKindsHaveFiniteLifetimeAndNonEffectEventsCreateNothing() {
        var world = new EffectWorld(); world.accept(new GameEvent.MatchEnded(1, "DRAW")); assertEquals(0, world.size());
        for (var kind : GameEvent.Effect.Kind.values()) { world.accept(event(kind)); }
        assertTrue(world.size() > 0); assertEquals(world.size() * 4, world.componentCount());
        world.update(1); assertEquals(0, world.componentCount());
    }
    @Test void capacityEvictsOldestAndClearReleasesEveryComponent() {
        var world = new EffectWorld();
        for (int i = 0; i < 100; i++) { world.accept(event(GameEvent.Effect.Kind.DESTROYED)); }
        assertEquals(EffectWorld.MAX_PARTICLES, world.size());
        assertTrue(world.snapshot().getFirst().entity() > 0);
        assertEquals(world.size() * 4, world.componentCount()); world.clear(); assertEquals(0, world.componentCount());
    }
    @Test void invalidFrameTimesAreRejected() {
        var world = new EffectWorld();
        assertThrows(IllegalArgumentException.class, () -> world.update(-1));
        assertThrows(IllegalArgumentException.class, () -> world.update(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> world.update(Double.POSITIVE_INFINITY));
    }
}
