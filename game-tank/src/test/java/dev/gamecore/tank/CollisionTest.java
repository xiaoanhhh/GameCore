package dev.gamecore.tank;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollisionTest {
    @Test void sweptCircleHitsEvenWhenBothEndpointsMiss() {
        assertEquals(.45, Collision.circle(new Vec2(0, 0), new Vec2(10, 0), new Vec2(5, 0), .5), 1e-12);
        assertEquals(Double.POSITIVE_INFINITY, Collision.circle(new Vec2(0, 1), new Vec2(10, 1), new Vec2(5, 0), .5));
    }
    @Test void stationaryAndTangentSegmentsAreHandled() {
        assertEquals(0, Collision.circle(new Vec2(0, 0), new Vec2(0, 0), new Vec2(0, 0), .5));
        assertEquals(Double.POSITIVE_INFINITY, Collision.circle(new Vec2(0, 0), new Vec2(0, 0), new Vec2(1, 0), .5));
        assertEquals(.5, Collision.circle(new Vec2(0, .5), new Vec2(10, .5), new Vec2(5, 0), .5), 1e-12);
    }
    @Test void slabIntersectionSupportsAxisAlignedReverseAndStartingInside() {
        assertEquals(.4, Collision.box(new Vec2(0, 0), new Vec2(10, 0), 4, -1, 5, 1));
        assertEquals(.5, Collision.box(new Vec2(10, 0), new Vec2(0, 0), 4, -1, 5, 1));
        assertEquals(0, Collision.box(new Vec2(4.5, 0), new Vec2(4.5, 2), 4, -1, 5, 1));
        assertEquals(Double.POSITIVE_INFINITY, Collision.box(new Vec2(3, 0), new Vec2(3, 2), 4, -1, 5, 1));
    }
}
