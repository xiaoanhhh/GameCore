package dev.gamecore.lwjgl3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;

/** 验收真正ZIP解压产物：清空系统Java入口，用随包JVM/正式exe运行，始终只清理自有进程。 */
public final class WindowsPortableSmokeTest {
    private WindowsPortableSmokeTest() { }
    public static void main(String[] args) throws Exception {
        Path report = Path.of(args[1]); Files.createDirectories(report);
        Files.deleteIfExists(report.resolve("result.txt"));
        Path run = report.resolve("run-" + UUID.randomUUID());
        Path relocated = run.resolve("模拟另一台电脑/解压 文件夹"); Files.createDirectories(relocated);
        unpack(Path.of(args[0]), relocated);
        Path app = relocated.resolve("GameCore"), runtime = app.resolve("runtime"), bundledJava = runtime.resolve("bin/java.exe");
        if (!Files.isRegularFile(bundledJava) || !Files.isDirectory(runtime.resolve("legal"))) { throw new IllegalStateException("Bundled runtime/notice missing"); }
        // '*'属于Java启动器的classpath语法，不是Windows文件名，不能交给Path.resolve。
        String classpath = app.resolve("app") + java.io.File.separator + "*" + java.io.File.pathSeparator + args[2];
        Path probe = run.resolve("runtime-result.txt");
        execute(List.of(bundledJava.toString(), "-cp", classpath, PortableRuntimeProbe.class.getName(), runtime.toString(), probe.toString()), run, run.resolve("runtime.log"), Map.of(), 25);
        require(probe, "PORTABLE_RUNTIME_OK");
        Path exeReport = run.resolve("native-exe"); Files.createDirectories(exeReport);
        execute(List.of(app.resolve("GameCore.exe").toString()), run, run.resolve("native-exe.log"),
                Map.of("JAVA_TOOL_OPTIONS", "\"-javaagent:" + args[3] + "=" + exeReport + "\"",
                       "GAMECORE_PORTABLE_EXPECTED_RUNTIME", runtime.toString()), 50);
        require(exeReport.resolve("result.txt"), "PORTABLE_EXE_OK");
        System.out.println("PORTABLE_CASE_OK native-exe-and-cloud");
        List<String> scenarios = List.of("DesktopSmokeTest", "SnakeSmokeTest", "UiScaleSmokeTest", "TankSmokeTest", "FightSmokeTest", "GameWindowSmokeTest");
        for (String scenario : scenarios) {
            Path folder = run.resolve(scenario); Files.createDirectories(folder.resolve("tmp"));
            execute(List.of(bundledJava.toString(), "-Xmx512m", "-Dgamecore.offline=true", "-Djava.io.tmpdir=" + folder.resolve("tmp"),
                    "-cp", classpath, "dev.gamecore.lwjgl3." + scenario, folder.toString()), run, folder.resolve("process.log"), Map.of(), 125);
            if (!Files.isRegularFile(folder.resolve("result.txt"))) { throw new IllegalStateException("No result from " + scenario); }
            System.out.println("PORTABLE_CASE_OK " + scenario);
        }
        Files.writeString(report.resolve("result.txt"), "WINDOWS_PORTABLE_OK\nRelocatedUnicodeAndSpaces=true\nSystemJavaRemoved=true\nBundledRuntimeProbe=true\nNativeExeAndCloud=true\nSixLocalGraphicsCases=true\nRun=" + run + "\n");
    }

    private static void execute(List<String> command, Path directory, Path log, Map<String,String> additions, long seconds) throws Exception {
        var builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        var environment = builder.environment();
        for (String key : List.of("JAVA_HOME","JRE_HOME","CLASSPATH","JAVA_OPTS","GAME_CORE_OPTS","JDK_JAVA_OPTIONS","JAVA_TOOL_OPTIONS","GAMECORE_PLATFORM_URL","GAMECORE_OFFLINE")) { environment.remove(key); }
        environment.put("PATH", Path.of(environment.getOrDefault("SystemRoot", "C:\\Windows"), "System32").toString());
        environment.putAll(additions);
        Process process = builder.start();
        try {
            if (!process.waitFor(seconds, TimeUnit.SECONDS)) { throw new IllegalStateException("Timed out: " + command.getFirst()); }
            if (process.exitValue() != 0) { throw new IllegalStateException("Process failed (" + process.exitValue() + "): " + log); }
        } finally {
            if (process.isAlive()) {
                var children = process.descendants().toList();
                children.forEach(ProcessHandle::destroy);
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) { children.forEach(ProcessHandle::destroyForcibly); process.destroyForcibly().waitFor(5, TimeUnit.SECONDS); }
            }
        }
    }

    private static void require(Path file, String marker) throws IOException {
        if (!Files.isRegularFile(file) || !Files.readString(file).contains(marker)) { throw new IllegalStateException("Missing " + marker + ": " + file); }
    }

    private static void unpack(Path archive, Path target) throws IOException {
        long bytes = 0; int entries = 0;
        try (var input = new ZipInputStream(Files.newInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            byte[] buffer = new byte[65536];
            while ((entry = input.getNextEntry()) != null) {
                if (++entries > 10000) { throw new IOException("Too many ZIP entries"); }
                Path destination = target.resolve(entry.getName()).normalize();
                if (!destination.startsWith(target) || destination.equals(target)) { throw new IOException("Unsafe ZIP path"); }
                if (entry.isDirectory()) { Files.createDirectories(destination); continue; }
                Files.createDirectories(destination.getParent());
                try (var output = Files.newOutputStream(destination)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        bytes += count; if (bytes > 1024L * 1024 * 1024) { throw new IOException("ZIP exceeds test budget"); }
                        output.write(buffer, 0, count);
                    }
                }
            }
        }
    }
}
