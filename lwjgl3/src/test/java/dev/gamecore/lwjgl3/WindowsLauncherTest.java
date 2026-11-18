package dev.gamecore.lwjgl3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 真正经 cmd 启动生成的 bat；探针代替图形入口，检查长目录、参数、依赖和退出码。 */
@EnabledOnOs(OS.WINDOWS)
class WindowsLauncherTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(ints = {0, 37})
    void launchesFromLongPathAndPreservesArgumentsAndExitCode(int exitCode) throws Exception {
        Path install = Files.createDirectories(temporary.resolve("安装目录 with spaces-" + "x".repeat(55)));
        Path bin = Files.createDirectories(install.resolve("bin"));
        Path lib = Files.createDirectories(install.resolve("lib"));
        Path script = Files.copy(Path.of(System.getProperty("gamecore.test.windowsLauncher")), bin.resolve("GameCore.bat"));
        Path source = temporary.resolve("Lwjgl3Launcher.java");
        Files.writeString(source, """
                package dev.gamecore.lwjgl3;
                public final class Lwjgl3Launcher {
                    public static void main(String[] args) throws Exception {
                        java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]),
                            args[1] + "\\n" + System.getProperty("launcher.test.option") + "\\n" + support.Marker.value());
                        System.exit(Integer.parseInt(args[2]));
                    }
                }
                """);
        Path marker = temporary.resolve("Marker.java");
        Files.writeString(marker, "package support; public final class Marker { public static String value() { return \"extra-jar-loaded\"; } }");
        Path classes = Files.createDirectories(temporary.resolve("classes"));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "-d", classes.toString(), source.toString(), marker.toString()));
        jar(lib.resolve("lwjgl3-0.1.0-SNAPSHOT.jar"), classes, "dev/gamecore/lwjgl3/Lwjgl3Launcher.class");
        jar(lib.resolve("additional-runtime.jar"), classes, "support/Marker.class");
        // 依赖数量与深目录组合曾触发 cmd 限制；这些有效空 JAR 使回归场景保持该规模。
        for (int index = 0; index < 80; index++) {
            try (var stream = new JarOutputStream(Files.newOutputStream(lib.resolve("dependency-" + index + ".jar")))) {
                stream.finish();
            }
        }
        Path result = install.resolve("result.txt");
        Path log = temporary.resolve("launcher.log");
        ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/d", "/c", "call", script.toString(), result.toString(),
                "argument with spaces", Integer.toString(exitCode));
        builder.directory(temporary.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        builder.environment().put("JAVA_OPTS", "-Dlauncher.test.option=forwarded");
        builder.environment().remove("GAME_CORE_OPTS");
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Launcher timed out");
            // cmd 的本地化诊断可能采用 OEM 编码；即使不是 UTF-8，也应保留原始失败原因。
            assertEquals(exitCode, process.exitValue(), new String(Files.readAllBytes(log), StandardCharsets.UTF_8));
            assertEquals("argument with spaces\nforwarded\nextra-jar-loaded", Files.readString(result));
        } finally {
            if (process.isAlive()) { process.destroyForcibly().waitFor(5, TimeUnit.SECONDS); }
        }
    }

    private static void jar(Path target, Path classes, String entry) throws IOException {
        try (var stream = new JarOutputStream(Files.newOutputStream(target))) {
            stream.putNextEntry(new JarEntry(entry));
            Files.copy(classes.resolve(entry), stream);
            stream.closeEntry();
        }
    }
}
