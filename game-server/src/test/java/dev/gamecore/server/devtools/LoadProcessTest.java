package dev.gamecore.server.devtools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
/** 真正的独立服务器/机器人JVM验证CLI及落盘指标，不把进程内微基准当容量测试。 */
class LoadProcessTest {
    @TempDir(cleanup=org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS) Path directory;
    private Process start(String log,String... args) throws Exception{
        var command=new java.util.ArrayList<String>();command.add(Path.of(System.getProperty("java.home"),"bin","java").toString());command.add("-Xmx256m");command.add("-cp");command.add(System.getProperty("gamecore.serverClasspath"));command.add("dev.gamecore.server.GameServerMain");command.addAll(java.util.List.of(args));
        return new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(directory.resolve(log).toFile()).start();
    }
    @Test void independentServerAndBotsProduceMeasurementsAndReplay() throws Exception{
        var log=directory.resolve("server.log");var server=start("server.log","tools-server","--port=0","--rooms=3","--seconds=30","--metrics="+directory.resolve("server.json"),"--replay-dir="+directory.resolve("replays"));
        try{
            int port=0;long end=System.nanoTime()+20_000_000_000L;while(System.nanoTime()<end&&server.isAlive()){
                if(Files.exists(log)){var text=Files.readString(log);var matcher=java.util.regex.Pattern.compile("TOOLS_SERVER_READY port=(\\d+)").matcher(text);if(matcher.find()){port=Integer.parseInt(matcher.group(1));break;}}java.util.concurrent.locks.LockSupport.parkNanos(2_000_000);
            }assertTrue(port>0,Files.exists(log)?Files.readString(log):"No server log");var client=start("client.log","load","--port="+port,"--rooms=3","--clients=6","--seconds=3","--hz=20","--output="+directory.resolve("client.json"));
            try{assertTrue(client.waitFor(20,TimeUnit.SECONDS),"Client timeout; logs="+directory);assertEquals(0,client.exitValue(),Files.readString(directory.resolve("client.log")));}finally{if(client.isAlive()){client.destroyForcibly();}}
            assertTrue(server.waitFor(40,TimeUnit.SECONDS));assertEquals(0,server.exitValue(),Files.readString(log));assertTrue(Files.readString(directory.resolve("server.json")).contains("tickP99Micros"));assertTrue(Files.readString(directory.resolve("client.json")).contains("\"connectionFailures\":0"));assertTrue(Files.readString(directory.resolve("client.json")).contains("serverErrorCodes"));
            try(var files=Files.list(directory.resolve("replays"))){var completed=files.filter(p->p.toString().endsWith(".gcr")).toList();assertFalse(completed.isEmpty());for(var p:completed){assertTrue(new ReplayPlayer(p,dev.gamecore.online.GameRegistry.builtIn()).length()>0);}}
        }finally{if(server.isAlive()){server.destroyForcibly();server.waitFor(5,TimeUnit.SECONDS);}}
    }
    @Test void loadRefusesPublicTargetsBeforeConnection() throws Exception{
        var process=start("denied.log","load","--host=203.0.113.10","--seconds=1");try{assertTrue(process.waitFor(5,TimeUnit.SECONDS));assertNotEquals(0,process.exitValue());assertTrue(Files.readString(directory.resolve("denied.log")).contains("literal loopback"));}finally{if(process.isAlive()){process.destroyForcibly();}}
    }
}
