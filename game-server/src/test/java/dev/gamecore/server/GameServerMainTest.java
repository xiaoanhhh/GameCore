package dev.gamecore.server;

import dev.gamecore.server.client.GameCliClient;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

/** 用独立进程验证控制台EOF与systemd无标准输入场景；不替换游戏规则或网络管线。 */
@Timeout(35)
class GameServerMainTest {
    private static Process launch(String... options) throws IOException {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-cp", System.getProperty("gamecore.serverClasspath"), GameServerMain.class.getName(), "server", "--port=0"));
        command.addAll(List.of(options));
        return new ProcessBuilder(command).redirectErrorStream(true).start();
    }
    private static String ready(BufferedReader reader) throws Exception {
        return CompletableFuture.supplyAsync(() -> {
            try {
                StringBuilder output = new StringBuilder();
                for (String line; (line = reader.readLine()) != null;) {
                    output.append(line).append('\n');
                    if (line.startsWith("TANK_SERVER_READY")) { return line; }
                }
                throw new IllegalStateException("Server exited before READY: " + output);
            } catch (IOException failure) { throw new IllegalStateException(failure); }
        }).get(15, TimeUnit.SECONDS);
    }
    private static void stop(Process process) throws InterruptedException {
        process.destroy();
        try { assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Child server must terminate"); }
        finally {
            if (process.isAlive()) { process.destroyForcibly().waitFor(5, TimeUnit.SECONDS); }
        }
    }
    @Test void consoleModeStillStopsOnEndOfInput() throws Exception {
        Process process = launch();
        try {
            process.getOutputStream().close();
            assertTrue(process.waitFor(15, TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains("TANK_SERVER_READY"), output);
            assertTrue(output.contains("TANK_SERVER_STOPPED"), output);
        } finally { stop(process); }
    }
    @Test void daemonModeAcceptsARealClientAfterStandardInputCloses() throws Exception {
        Process process = launch("--daemon=true");
        try {
            process.getOutputStream().close();
            var reader = process.inputReader(java.nio.charset.StandardCharsets.UTF_8);
            var matcher = Pattern.compile("address=/127\\.0\\.0\\.1:(\\d+)").matcher(ready(reader));
            assertTrue(matcher.find());
            try (var client = GameCliClient.connect("127.0.0.1", Integer.parseInt(matcher.group(1)), "daemon-test")) {
                assertTrue(client.active());
                assertTrue(process.isAlive());
                assertFalse(client.join().getMatchId().isBlank());
            }
        } finally { stop(process); }
    }
    @Test void invalidDaemonValueFailsBeforeBinding() throws Exception {
        Process process = launch("--daemon=invalid");
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertNotEquals(0, process.exitValue());
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(output.contains("daemon must be true/false"), output);
            assertFalse(output.contains("TANK_SERVER_READY"), output);
        } finally { stop(process); }
    }
}
