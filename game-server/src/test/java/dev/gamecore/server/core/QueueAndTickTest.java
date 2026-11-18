package dev.gamecore.server.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static dev.gamecore.server.core.RoomFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** 有界交接和可控nanoTime调度测试，不用真实sleep模拟过载。 */
class QueueAndTickTest {
    @Test void queueHasGlobalAndPerConnectionBoundsAndReturnsReservations() {
        var queue = new RoomInputQueue(); List<Peer> peers = new ArrayList<>();
        for (int id = 0; id < 8; id++) {
            var peer = new Peer(); peers.add(peer);
            for (int input = 1; input <= 1000; input++) { queue.offer(peer, new InputIntent(input, "m", input, 1, 0, 0, null, false)); }
            assertTrue(peer.pending.get() <= RoomInputQueue.PER_CONNECTION);
        }
        assertEquals(RoomInputQueue.CAPACITY, queue.size()); assertEquals(128, queue.drain().size()); assertEquals(0, queue.size());
        assertTrue(peers.stream().allMatch(peer -> peer.pending.get() == 0)); peers.getFirst().disconnected = true;
        assertFalse(queue.offer(peers.getFirst(), new InputIntent(1, "m", 1, 1, 0, 0, null, false)));
    }
    @Test void fixedClockDoesNotRunBeforeDeadlineAndLimitsCatchupToThreeWithoutSkippingSteps() {
        var clock = new AtomicLong(); var calls = new AtomicInteger();
        var runner = new TickRunner(30, clock::get, calls::incrementAndGet, cause -> fail(cause));
        runner.runDue(); assertEquals(0, calls.get()); clock.set(33_333_333); runner.runDue(); assertEquals(1, calls.get());
        clock.set(1_000_000_000); runner.runDue(); assertEquals(4, calls.get()); assertEquals(1, runner.metrics().limitedCatchups());
        runner.runDue(); assertEquals(7, calls.get()); assertTrue(runner.metrics().maxLagNanos() > 0);
    }
    @Test void overrunsAreMeasuredAndSevereLagExplicitlyAbortsInsteadOfChangingDt() {
        var clock = new AtomicLong(); List<String> causes = new ArrayList<>();
        var runner = new TickRunner(30, clock::get, () -> clock.addAndGet(50_000_000), causes::add);
        clock.set(33_333_333); runner.runDue(); assertEquals(3, runner.metrics().overruns()); assertEquals(3, runner.metrics().steps());
        clock.set(4_000_000_000L); runner.runDue(); assertEquals(List.of("TICK_OVERLOAD"), causes); assertEquals(3, runner.metrics().steps());
    }
    @Test void simulationFailureIsTrackedAndSchedulerCanContinueControlSteps() {
        var clock = new AtomicLong(); List<String> causes = new ArrayList<>(); var bad = new java.util.concurrent.atomic.AtomicBoolean(true);
        var runner = new TickRunner(30, clock::get, () -> { if (bad.getAndSet(false)) { throw new IllegalStateException("fixture"); } }, causes::add);
        clock.set(40_000_000); runner.runDue(); assertEquals(1, runner.metrics().failures()); assertEquals(List.of("SIMULATION_FAILURE"), causes);
        clock.set(80_000_000); runner.runDue(); assertEquals(1, runner.metrics().steps());
    }
    @Test void packagedMapsLoadWithoutGraphicsAndDigestUsesContents() throws Exception {
        var crossfire = MapResources.load("crossfire"); var ruins = MapResources.load("ruins");
        assertEquals(24, crossfire.width()); assertEquals(18, ruins.height());
        assertEquals(MapResources.digest(crossfire), MapResources.digest(MapResources.load("crossfire")));
        assertNotEquals(MapResources.digest(crossfire), MapResources.digest(ruins));
        assertThrows(IllegalArgumentException.class, () -> new GameRoom(open(), 25, 42));
    }
    @Test void closingDuringConcurrentOffersLeavesNoEntriesOrBorrowedReservations() throws Exception {
        var queue = new RoomInputQueue(); var start = new java.util.concurrent.CountDownLatch(1);
        var threads = new ArrayList<Thread>(); var peers = new ArrayList<Peer>();
        for (int index = 0; index < 8; index++) {
            var peer = new Peer(); peers.add(peer);
            var thread = Thread.ofPlatform().unstarted(() -> {
                try { start.await(); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); return; }
                for (int sequence = 1; sequence <= 1000; sequence++) {
                    queue.offer(peer, new InputIntent(sequence, "m", sequence, 1, 0, 0, null, false));
                }
            });
            threads.add(thread); thread.start();
        }
        try { start.countDown(); queue.close(); }
        finally {
            start.countDown();
            for (var thread : threads) { thread.join(5000); assertFalse(thread.isAlive()); }
        }
        assertEquals(0, queue.size()); assertTrue(peers.stream().allMatch(peer -> peer.pending.get() == 0));
        assertFalse(queue.offer(peers.getFirst(), new InputIntent(1, "m", 1, 1, 0, 0, null, false)));
        var room = started(30, new Peer(), new Peer()); room.close();
        assertFalse(room.requestJoin(new Peer(), 1)); assertEquals(List.of(false, false), room.latest().connected());
    }
}
