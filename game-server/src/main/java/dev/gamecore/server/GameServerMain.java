package dev.gamecore.server;

import dev.gamecore.protocol.TankProtocol;
import dev.gamecore.server.client.GameCliClient;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** 服务端/无图形客户端共用入口；控制台与后台生命周期等待均在IO/Room执行域外。 */
public final class GameServerMain {
    private record Controls(TankProtocol.MoveDirection move, TankProtocol.TurnDirection turn, Double aim, boolean fire) { }
    private GameServerMain() { }
    public static void main(String[] arguments) throws Exception {
        if(arguments.length>0&&arguments[0].equals("serve")){dev.gamecore.server.deployment.DeploymentMain.main(arguments);return;}
        if(arguments.length>0&&arguments[0].equals("platform")){dev.gamecore.server.platform.PlatformServerMain.main(arguments);return;}
        if (arguments.length > 0 && java.util.Set.of("tools-server", "load", "replay", "jfr-report").contains(arguments[0])) { dev.gamecore.server.devtools.DevToolsMain.main(arguments); return; }
        if (arguments.length > 0 && java.util.Set.of("multi", "bot").contains(arguments[0])) { dev.gamecore.server.multigame.MultiGameMain.main(arguments); return; }
        if (arguments.length == 0 || arguments[0].equals("--help")) {
            System.out.println("multi --host=127.0.0.1 --port=7200 [--dev=true]; bot --game=snake --room=snake-1 --seconds=10");
            System.out.println("tools-server --rooms=3 --game=all --seconds=20 --metrics=server.json [--jfr=run.jfr] [--replay-dir=replays]");
            System.out.println("load --clients=6 --rooms=3 --game=all --seconds=10 --hz=20 --output=client.json; replay --file=match.gcr --tick=10");
            System.out.println("server --host=127.0.0.1 --port=7100 --tps=30 --map=crossfire --seed=42 [--daemon=true]");
            System.out.println("client --host=127.0.0.1 --port=7100 --name=P1 [--auto=drive] [--seconds=3]");
            System.out.println("Client: move forward/back/idle; turn left/right/idle; aim degrees/off; fire on/off; /quit"); return;
        }
        Map<String, String> options = options(arguments);
        if (arguments[0].equals("server")) { serve(options); }
        else if (arguments[0].equals("client") && !options.containsKey("daemon")) { play(options); }
        else { throw new IllegalArgumentException("Expected server or client"); }
    }
    private static Map<String, String> options(String[] arguments) {
        Map<String, String> result = new HashMap<>();
        for (int index = 1; index < arguments.length; index++) {
            var argument = arguments[index]; int separator = argument.indexOf('=');
            if (!argument.startsWith("--") || separator < 3 || separator == argument.length() - 1) { throw new IllegalArgumentException("Use --key=value"); }
            String key = argument.substring(2, separator);
            if (!Set.of("host", "port", "tps", "map", "seed", "name", "auto", "seconds", "daemon").contains(key)
                    || result.putIfAbsent(key, argument.substring(separator + 1)) != null) { throw new IllegalArgumentException("Invalid option: " + key); }
        }
        return result;
    }
    private static void serve(Map<String, String> options) throws Exception {
        String daemonOption = options.getOrDefault("daemon", "false");
        if (!daemonOption.equals("true") && !daemonOption.equals("false")) { throw new IllegalArgumentException("daemon must be true/false"); }
        boolean daemon = daemonOption.equals("true");
        var settings = new ServerSettings(options.getOrDefault("host", "127.0.0.1"), Integer.parseInt(options.getOrDefault("port", "7100")),
                Integer.parseInt(options.getOrDefault("tps", "30")), options.getOrDefault("map", "crossfire"), Long.parseLong(options.getOrDefault("seed", "42")), 60, 15);
        try (var server = NettyGameServer.start(settings)) {
            var hook = new Thread(() -> stopServer(server, daemon), "tank-server-shutdown"); Runtime.getRuntime().addShutdownHook(hook);
            try {
                System.out.println("TANK_SERVER_READY address=" + server.address() + " tps=" + settings.tps() + " simulationTps=60");
                if (daemon) {
                    // systemd的标准输入为/dev/null；后台模式等待JVM信号，由shutdown hook收束房间与网络资源。
                    new CountDownLatch(1).await();
                    return;
                }
                var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                for (String line; (line = input.readLine()) != null;) {
                    if (line.equals("/quit")) { break; }
                    if (line.equals("/metrics")) { System.out.println("METRICS " + server.metrics() + " connections=" + server.connectionCount() + " queued=" + server.queuedInputs()); }
                }
            } finally { removeHook(hook); }
        }
        System.out.println("TANK_SERVER_STOPPED");
    }
    private static void stopServer(NettyGameServer server, boolean daemon) {
        server.close();
        if (daemon) { System.out.println("TANK_SERVER_STOPPED"); }
    }
    private static void play(Map<String, String> options) throws Exception {
        boolean drive = "drive".equals(options.get("auto"));
        if (options.containsKey("auto") && !drive) { throw new IllegalArgumentException("Unknown auto mode"); }
        int seconds = Integer.parseInt(options.getOrDefault("seconds", "0"));
        if (seconds < 0 || seconds > 3600) { throw new IllegalArgumentException("seconds must be 0..3600"); }
        var controls = new AtomicReference<>(new Controls(drive ? TankProtocol.MoveDirection.FORWARD : TankProtocol.MoveDirection.MOVE_IDLE,
                drive ? TankProtocol.TurnDirection.LEFT : TankProtocol.TurnDirection.TURN_IDLE, null, drive));
        var quit = new AtomicBoolean();
        try (var client = GameCliClient.connect(options.getOrDefault("host", "127.0.0.1"), Integer.parseInt(options.getOrDefault("port", "7100")), options.getOrDefault("name", "player"))) {
            var hook = new Thread(client::close, "tank-client-shutdown"); Runtime.getRuntime().addShutdownHook(hook);
            System.out.println("TANK_CLIENT_JOINED slot=" + client.join().getPlayerSlot() + " match=" + client.join().getMatchId());
            Thread console = Thread.ofPlatform().daemon(true).name("tank-cli-input").start(() -> readControls(controls, quit));
            long start = System.nanoTime(), nextSend = start, nextPrint = start, sequence = 0;
            try {
                while (client.active() && !quit.get() && (seconds == 0 || System.nanoTime() - start < seconds * 1_000_000_000L)) {
                    var message = client.receive(Duration.ofMillis(20));
                    if (message != null) {
                        if (message.hasMatchResult()) { System.out.println("MATCH_RESULT " + message.getMatchResult().getOutcome() + " reason=" + message.getMatchResult().getReason()); break; }
                        if (message.hasError()) { System.out.println("ERROR " + message.getError().getCode()); }
                        if (message.hasGameEvent()) { System.out.println("EVENT " + message.getGameEvent().getKind() + " tick=" + message.getGameEvent().getServerTick()); }
                    }
                    var snapshot = client.latest(); long now = System.nanoTime();
                    if (snapshot != null && now >= nextPrint) {
                        var self = snapshot.getPlayers(client.join().getPlayerSlot());
                        System.out.println("WORLD tick=" + snapshot.getServerTick() + " simulationTick=" + snapshot.getSimulationTick()
                                + " state=" + snapshot.getState() + " x=" + self.getX() + " y=" + self.getY() + " hp=" + self.getHealth()
                                + " ack=" + snapshot.getInputAcks(client.join().getPlayerSlot()).getSequence());
                        nextPrint = now + 1_000_000_000L;
                    }
                    if (snapshot != null && snapshot.getState() == TankProtocol.RoomState.RUNNING && now >= nextSend) {
                        var value = controls.get(); var input = TankProtocol.PlayerInput.newBuilder().setMatchId(client.join().getMatchId()).setSequence(++sequence)
                                .setTargetTick(snapshot.getServerTick() + Math.max(2, snapshot.getServerTps() / 10)).setMove(value.move()).setTurn(value.turn()).setFire(value.fire());
                        if (value.aim() != null) { input.setAimDegrees(value.aim()); }
                        client.sendInput(input.build()); nextSend = now + 100_000_000L;
                    }
                }
            } finally { console.interrupt(); removeHook(hook); }
        }
        System.out.println("TANK_CLIENT_STOPPED");
    }
    private static void readControls(AtomicReference<Controls> controls, AtomicBoolean quit) {
        var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        try {
            for (String line; (line = input.readLine()) != null;) {
                if (line.equals("/quit")) { quit.set(true); return; }
                String[] parts = line.trim().split("\\s+", 2);
                try {
                    if (parts.length != 2) { throw new IllegalArgumentException("Expected command value"); }
                    var old = controls.get();
                    switch (parts[0]) {
                        case "move" -> controls.set(new Controls(switch (parts[1]) { case "forward" -> TankProtocol.MoveDirection.FORWARD; case "back" -> TankProtocol.MoveDirection.BACKWARD; case "idle" -> TankProtocol.MoveDirection.MOVE_IDLE; default -> throw new IllegalArgumentException("Bad move"); }, old.turn(), old.aim(), old.fire()));
                        case "turn" -> controls.set(new Controls(old.move(), switch (parts[1]) { case "left" -> TankProtocol.TurnDirection.LEFT; case "right" -> TankProtocol.TurnDirection.RIGHT; case "idle" -> TankProtocol.TurnDirection.TURN_IDLE; default -> throw new IllegalArgumentException("Bad turn"); }, null, old.fire()));
                        case "aim" -> {
                            Double aim = parts[1].equals("off") ? null : Double.valueOf(parts[1]);
                            if (aim != null && (!Double.isFinite(aim) || aim < 0 || aim >= 360)) { throw new IllegalArgumentException("Bad aim"); }
                            controls.set(new Controls(old.move(), TankProtocol.TurnDirection.TURN_IDLE, aim, old.fire()));
                        }
                        case "fire" -> {
                            if (!parts[1].equals("on") && !parts[1].equals("off")) { throw new IllegalArgumentException("Use on/off"); }
                            controls.set(new Controls(old.move(), old.turn(), old.aim(), parts[1].equals("on")));
                        }
                        default -> throw new IllegalArgumentException("Unknown control");
                    }
                } catch (IllegalArgumentException invalid) { System.err.println("CONTROL_REJECTED " + invalid.getMessage()); }
            }
            quit.set(true);
        } catch (IOException failure) { quit.set(true); }
    }
    private static void removeHook(Thread hook) {
        try { Runtime.getRuntime().removeShutdownHook(hook); } catch (IllegalStateException ignored) { /* JVM关闭时由hook完成幂等清理。 */ }
    }
}
