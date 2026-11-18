package dev.gamecore.client.traffic;

import static org.junit.jupiter.api.Assertions.*;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** 用注入时钟验证合并窗口边界、容量和关闭语义。 */
class TrafficLogTest {
    private final AtomicLong now = new AtomicLong(1_000);
    private final TrafficLog log = new TrafficLog(now::get, ZoneOffset.UTC);

    private TrafficLog started() { log.enable(); return log; }

    @Test void disabledLogIgnoresEverything() {
        log.add(TrafficKind.ROOM, TrafficDirection.RECEIVED, "room");
        log.burst(TrafficKind.SNAPSHOT, TrafficDirection.RECEIVED, "tick", 1, 10);
        now.addAndGet(5_000);
        log.flush();
        assertTrue(log.entries().isEmpty());
        assertEquals(0, log.version());
    }

    @Test void singleEntriesKeepOrderKindDirectionAndTime() {
        now.set(3_723_456);
        started().add(TrafficKind.ROOM, TrafficDirection.RECEIVED, "room");
        log.add(TrafficKind.HEARTBEAT, TrafficDirection.SENT, "ping");
        var entries = log.entries();
        assertEquals(new TrafficEntry("01:02:03.456", TrafficKind.ROOM, TrafficDirection.RECEIVED, "room"), entries.get(0));
        assertEquals(TrafficKind.HEARTBEAT, entries.get(1).kind());
    }

    @Test void burstsBecomeOneLinePerSecondWithCountsAndRange() {
        started();
        for (int i = 0; i < 30; i++) { log.burst(TrafficKind.SNAPSHOT, TrafficDirection.RECEIVED, "tick", 100 + i, 412); now.addAndGet(33); }
        assertTrue(log.entries().isEmpty(), "990ms still inside the window");
        now.addAndGet(10);
        log.flush();
        assertEquals(1, log.entries().size());
        var line = log.entries().getFirst();
        assertEquals(TrafficKind.SNAPSHOT, line.kind());
        assertEquals("×30 · tick 100→129 · 412 B", line.text());
    }

    @Test void windowClosesOnExactlyOneSecondAndNextMessageOpensANewOne() {
        started().burst(TrafficKind.INPUT, TrafficDirection.SENT, "序号", 1, 20);
        now.addAndGet(TrafficLog.WINDOW_MILLIS - 1);
        log.flush();
        assertTrue(log.entries().isEmpty());
        now.addAndGet(1);
        log.burst(TrafficKind.INPUT, TrafficDirection.SENT, "序号", 2, 20);
        assertEquals(1, log.entries().size());
        assertEquals("×1 · 序号 1→1 · 20 B", log.entries().getFirst().text());
        now.addAndGet(TrafficLog.WINDOW_MILLIS);
        log.flush();
        assertEquals(2, log.entries().size());
    }

    @Test void independentKindsMergeSeparatelyAndOtherKindsStayIndividual() {
        started().burst(TrafficKind.INPUT, TrafficDirection.SENT, "序号", 1, 20);
        log.burst(TrafficKind.SNAPSHOT, TrafficDirection.RECEIVED, "tick", 9, 40);
        log.add(TrafficKind.HEARTBEAT, TrafficDirection.SENT, "#1");
        log.add(TrafficKind.HEARTBEAT, TrafficDirection.RECEIVED, "#1");
        assertEquals(2, log.entries().size());
        now.addAndGet(TrafficLog.WINDOW_MILLIS);
        log.flush();
        assertEquals(4, log.entries().size());
    }

    @Test void capacityDropsTheOldestLine() {
        started();
        for (int i = 0; i < TrafficLog.CAPACITY + 25; i++) { log.add(TrafficKind.ROOM, TrafficDirection.RECEIVED, "line " + i); }
        var entries = log.entries();
        assertEquals(TrafficLog.CAPACITY, entries.size());
        assertEquals("line 25", entries.getFirst().text());
        assertEquals("line " + (TrafficLog.CAPACITY + 24), entries.getLast().text());
    }

    @Test void burstsDoNotGrowWithMessageCount() {
        started();
        for (int i = 0; i < 1_000_000; i++) { log.burst(TrafficKind.SNAPSHOT, TrafficDirection.RECEIVED, "tick", i, 8); }
        now.addAndGet(TrafficLog.WINDOW_MILLIS);
        log.flush();
        assertEquals(1, log.entries().size());
        assertTrue(log.entries().getFirst().text().startsWith("×1000000 · "));
    }

    @Test void textIsCleanedAndLimited() {
        started().add(TrafficKind.ERROR, TrafficDirection.RECEIVED, "a\nb\tc" + "x".repeat(500));
        String text = log.entries().getFirst().text();
        assertEquals(TrafficLog.TEXT_LIMIT, text.length());
        assertTrue(text.startsWith("a b c"));
    }

    @Test void clearAndDisableRemoveOpenWindowsToo() {
        started().burst(TrafficKind.INPUT, TrafficDirection.SENT, "序号", 1, 20);
        log.add(TrafficKind.ROOM, TrafficDirection.RECEIVED, "room");
        long version = log.version();
        log.clear();
        assertTrue(log.version() > version);
        now.addAndGet(TrafficLog.WINDOW_MILLIS);
        log.flush();
        assertTrue(log.entries().isEmpty());
        log.burst(TrafficKind.INPUT, TrafficDirection.SENT, "序号", 1, 20);
        log.disable();
        log.enable();
        now.addAndGet(TrafficLog.WINDOW_MILLIS);
        log.flush();
        assertTrue(log.entries().isEmpty());
    }

    @Test void concurrentWritersStayBoundedAndCountEveryBurst() throws Exception {
        var shared = new TrafficLog();
        shared.enable();
        int writers = 8, each = 20_000;
        var start = new CountDownLatch(1);
        var threads = new ArrayList<Thread>();
        for (int w = 0; w < writers; w++) {
            var thread = new Thread(() -> {
                try { start.await(); } catch (InterruptedException failure) { return; }
                for (int i = 0; i < each; i++) {
                    shared.add(TrafficKind.ROOM, TrafficDirection.RECEIVED, "x");
                    shared.burst(TrafficKind.SNAPSHOT, TrafficDirection.RECEIVED, "tick", i, 1);
                }
            });
            threads.add(thread);
            thread.start();
        }
        start.countDown();
        for (var thread : threads) { thread.join(); }
        assertTrue(shared.entries().size() <= TrafficLog.CAPACITY);
    }
}
