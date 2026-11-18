package dev.gamecore.client.network;

import dev.gamecore.client.network.sync.ClientPrediction;
import dev.gamecore.client.network.sync.SnapshotInterpolator;
import dev.gamecore.network.experiment.NetworkProfile;
import dev.gamecore.network.experiment.SimulatedLink;
import dev.gamecore.protocol.TankProtocol;
import dev.gamecore.server.core.InputIntent;
import dev.gamecore.server.network.Wire;
import dev.gamecore.tank.TankGame;
import dev.gamecore.tank.TankSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static dev.gamecore.client.network.PredictionModelTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** 真GameRoom规则+虚拟纳秒时钟+同生产有界链路模型；36种配置各重复，非TCP性能基准。 */
class NetworkExperimentTest {
    private record Result(int rtt, int jitter, int drop, double mean, double p95, double maximum, long corrections,
                          double receiveHz, long expired, long late, long duplicates, boolean converged, double finalError,
                          List<TankSnapshot.Tank> finalTanks, List<TankSnapshot.Bullet> finalBullets, long finalSimulationTick,
                          List<SimulatedLink.Trace> inputTrace, List<SimulatedLink.Trace> snapshotTrace) { }
    private static final List<Result> RESULTS = new ArrayList<>();
    @TestFactory Stream<DynamicTest> reproducibleMatrix() {
        var cases = new ArrayList<DynamicTest>();
        for (int rtt : new int[]{0, 50, 100, 200}) {
            for (int jitter : new int[]{0, 20, 50}) {
                for (int drop : new int[]{0, 5, 10}) {
                    var profile = new NetworkProfile(rtt, jitter, drop, 42, 0, 0);
                    cases.add(DynamicTest.dynamicTest("rtt=" + rtt + " jitter=" + jitter + " appDrop=" + drop, () -> {
                        var first = run(profile, false); var second = run(profile, false);
                        assertEquals(first, second, "Same seed/event schedule must reproduce metrics AND traces");
                        assertTrue(first.converged()); assertEquals(0, first.finalError(), 1e-8); RESULTS.add(first);
                    }));
                }
            }
        }
        return cases.stream();
    }
    @Test void duplicateInputsAtHighLatencyAndTemporaryBlackoutRecoverAndConverge() {
        var profile = new NetworkProfile(200, 50, 10, 91, 2500, 350);
        var first = run(profile, true); assertEquals(first, run(profile, true)); assertTrue(first.converged());
        assertTrue(first.inputTrace().stream().anyMatch(trace -> trace.action().equals("BLACKOUT")));
    }
    private static Result run(NetworkProfile profile, boolean duplicate) {
        var a = new Peer(); var room = room(a, new Peer()); var prediction = new ClientPrediction(); var interpolation = new SnapshotInterpolator();
        prediction.rttNanos(profile.rttMillis() * 1_000_000L);
        var up = new SimulatedLink<InputIntent>(profile, 0x92L); var down = new SimulatedLink<TankProtocol.WorldSnapshot>(profile, 0x51L);
        var receiver = new SnapshotReceiver(id -> room.latest().world().map()); receiver.configure(a.match, 30, true);
        TankSnapshot authority = null; long sequence = 0, lastTarget = 0, nextInput = 0, serverSteps = 1;
        down.offer(Wire.snapshot(room.latest()), 0, true, "WORLD");
        for (long now = 0; now <= 8_000_000_000L; now += 5_000_000L) {
            for (var intent : up.drain(now)) { assertTrue(room.inputQueue().offer(a, intent)); }
            if (now >= serverSteps * 1_000_000_000L / 30) {
                room.step(); serverSteps++; down.offer(Wire.snapshot(room.latest()), now, true, "WORLD");
            }
            for (var world : down.drain(now)) {
                if (world.getServerTick() <= receiver.serverTick()) { continue; }
                authority = receiver.receive(world, TankGame.Result.NONE);
                prediction.reconcile(authority, world, 0, now, now); interpolation.offer(world.getServerTick(), 30, authority, now);
            }
            if (authority != null && now >= nextInput && now < 6_000_000_000L) {
                int move = now < 2_000_000_000L ? 1 : now < 4_000_000_000L ? ((now / 150_000_000L) % 2 == 0 ? 1 : -1) : now < 5_000_000_000L ? -1 : 0;
                int turn = now >= 2_000_000_000L && now < 5_000_000_000L ? ((now / 100_000_000L) % 2 == 0 ? 1 : -1) : 0;
                long target = Math.max(lastTarget, prediction.estimatedServerTick(now) + prediction.leadTicks(30)); lastTarget = target;
                var value = input(a.match, ++sequence, target, move, turn); prediction.input(value);
                up.offer(intent(value), now, true, "INPUT");
                if (duplicate && sequence % 10 == 0) { up.offer(intent(value), now, true, "DUPLICATE"); }
                nextInput = now + 50_000_000L;
            }
            if (authority != null) { interpolation.display(authority, 0, prediction.display(now), now); }
        }
        assertNotNull(authority); assertTrue(up.metrics().queued() <= SimulatedLink.CAPACITY && down.metrics().queued() <= SimulatedLink.CAPACITY);
        assertEquals(0, up.metrics().overflows()); assertEquals(0, down.metrics().overflows());
        var displayed = prediction.display(8_000_000_000L); var actual = room.latest().world().tanks().getFirst();
        double finalError = StrictMath.sqrt(displayed.position().distanceSquared(actual.position())); var metrics = prediction.metrics();
        boolean converged = finalError < 1e-8 && Math.abs(ClientPrediction.shortAngle(displayed.heading() - actual.heading())) < 1e-8 && metrics.pendingInputs() == 0;
        return new Result(profile.rttMillis(), profile.jitterMillis(), profile.dropPercent(), metrics.meanError(), metrics.p95Error(), metrics.maxError(),
                metrics.corrections(), interpolation.receiveRate(), metrics.expiredInputs(), a.errors.stream().filter("LATE_INPUT"::equals).count(),
                a.errors.stream().filter("DUPLICATE_SEQUENCE"::equals).count(), converged, finalError,
                room.latest().world().tanks(), room.latest().world().bullets(), room.latest().world().tick(), up.trace(), down.trace());
    }
    @AfterAll static void writeResults() throws IOException {
        var directory = Path.of(System.getProperty("gamecore.network.experimentOutput", "build/reports/network-experiments")); Files.createDirectories(directory);
        RESULTS.sort(Comparator.comparingInt(Result::rtt).thenComparingInt(Result::jitter).thenComparingInt(Result::drop));
        var csv = new StringBuilder("rtt_ms,jitter_ms,app_drop_pct,mean_position_error,p95_recent256,max_position_error,corrections,snapshot_hz,expired_inputs,late_inputs,duplicate_inputs,converged,final_error\n");
        for (var value : RESULTS) {
            csv.append(String.format(Locale.ROOT, "%d,%d,%d,%.9f,%.9f,%.9f,%d,%.4f,%d,%d,%d,%s,%.9f%n", value.rtt(), value.jitter(), value.drop(),
                    value.mean(), value.p95(), value.maximum(), value.corrections(), value.receiveHz(), value.expired(), value.late(), value.duplicates(), value.converged(), value.finalError()));
        }
        Files.writeString(directory.resolve("results.csv"), csv);
        if (!RESULTS.isEmpty()) {
            var chosen = RESULTS.getLast(); var trace = new StringBuilder("direction,id,type,enqueued_nanos,due_nanos,delivered_nanos,action\n");
            appendTrace(trace, "UP", chosen.inputTrace()); appendTrace(trace, "DOWN", chosen.snapshotTrace()); Files.writeString(directory.resolve("trace-200-50-10.csv"), trace);
        }
    }
    private static void appendTrace(StringBuilder output, String direction, List<SimulatedLink.Trace> traces) {
        for (var trace : traces) { output.append(direction).append(',').append(trace.id()).append(',').append(trace.label()).append(',')
                .append(trace.enqueuedNanos()).append(',').append(trace.dueNanos()).append(',').append(trace.deliveredNanos()).append(',').append(trace.action()).append('\n'); }
    }
}
