package dev.gamecore.server.deployment;

import dev.gamecore.network.transport.TransportTls;
import dev.gamecore.online.GameRegistry;
import dev.gamecore.server.multigame.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** 严格、无脚本求值的部署配置；显式环境变量覆盖文件，未知键/重复键/越界值在启动线程拒绝。 */
public record DeploymentConfig(String mode,String host,int port,int healthPort,ServerLimits limits,boolean tls,
        Path certificate,Path privateKey,Path replayDir,Path outboxDir,String platformUrl,String loggingLevel,
        List<String> games,int roomsPerGame) {
    private static final Map<String,String> DEFAULTS=Map.ofEntries(
        Map.entry("mode","practice"),Map.entry("host","127.0.0.1"),Map.entry("port","7201"),Map.entry("healthPort","7202"),
        Map.entry("maxRooms","4"),Map.entry("maxConnections","16"),Map.entry("maxPlayersPerRoom","2"),
        Map.entry("nettyWorkerThreads","1"),Map.entry("logicThreads","1"),Map.entry("maxPendingInputs","64"),
        Map.entry("pendingPerConnection","16"),Map.entry("snapshotRate","30"),Map.entry("tank.tickRate","30"),
        Map.entry("snake.tickRate","20"),Map.entry("fight.tickRate","30"),Map.entry("tls","false"),
        Map.entry("certificate",""),Map.entry("privateKey",""),Map.entry("replayDir","replays"),Map.entry("outboxDir","outbox"),
        Map.entry("platformUrl","http://127.0.0.1:8080"),Map.entry("loggingLevel","INFO"),
        Map.entry("games","tank,snake,fight"),Map.entry("roomsPerGame","1"));
    public static DeploymentConfig load(Path file,Map<String,String> environment) throws IOException {
        if(Files.size(file)>65536){throw new IllegalArgumentException("Configuration too large");}
        var values=new HashMap<>(DEFAULTS);var seen=new HashSet<String>();
        for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)){
            line=line.strip();if(line.isEmpty()||line.startsWith("#")){continue;}int split=line.indexOf('=');
            if(split<1){throw new IllegalArgumentException("Configuration requires key=value");}
            String key=line.substring(0,split).strip();if(!DEFAULTS.containsKey(key)||!seen.add(key)){throw new IllegalArgumentException("Unknown/duplicate configuration key: "+key);}
            values.put(key,line.substring(split+1).strip());
        }
        for(String key:DEFAULTS.keySet()){String env="GAMECORE_"+key.replaceAll("([a-z])([A-Z])","$1_$2").replace('.','_').toUpperCase(Locale.ROOT);if(environment.containsKey(env)){values.put(key,environment.get(env));}}
        String host=values.get("host");if(host.isBlank()||host.length()>253){throw new IllegalArgumentException("host");}
        boolean tls=bool(values.get("tls"));if(!tls&&!TransportTls.loopback(host)){throw new IllegalArgumentException("Non-loopback deployment requires TLS");}
        if(!Set.of("practice","platform").contains(values.get("mode"))){throw new IllegalArgumentException("mode");}
        if(number(values,"maxPlayersPerRoom",2,2)!=2){throw new IllegalArgumentException("Only two-player games are supported");}
        if(!Set.of("INFO","WARNING","ERROR","DEBUG").contains(values.get("loggingLevel"))){throw new IllegalArgumentException("loggingLevel");}
        var rates=new HashMap<String,Integer>();for(String game:List.of("tank","snake","fight")){rates.put(game,number(values,game+".tickRate",1,60));}
        var limits=new ServerLimits(number(values,"maxRooms",1,128),number(values,"maxConnections",2,512),number(values,"nettyWorkerThreads",1,8),number(values,"logicThreads",1,8),number(values,"maxPendingInputs",2,256),number(values,"pendingPerConnection",1,32),number(values,"snapshotRate",1,60),rates);
        var registry=GameRegistry.builtIn();for(var f:registry.all()){f.validate(limits.config(f.type().id(),f.defaults()));}
        var games=List.of(values.get("games").split(",",-1));if(games.isEmpty()||games.size()>3||new HashSet<>(games).size()!=games.size()){throw new IllegalArgumentException("games");}games.forEach(registry::require);
        int count=number(values,"roomsPerGame",1,128);if(values.get("mode").equals("practice")&&games.size()*count>limits.maxRooms()){throw new IllegalArgumentException("Practice rooms exceed maxRooms");}
        Path base=file.toAbsolutePath().getParent();Path cert=path(base,values.get("certificate")),key=path(base,values.get("privateKey"));
        if(tls&&(cert==null||key==null||!Files.isRegularFile(cert)||!Files.isRegularFile(key))){throw new IllegalArgumentException("Readable TLS certificate/privateKey required");}
        Path replay=path(base,values.get("replayDir")),outbox=path(base,values.get("outboxDir"));if(replay==null||outbox==null){throw new IllegalArgumentException("State directories required");}
        return new DeploymentConfig(values.get("mode"),host,number(values,"port",0,65535),number(values,"healthPort",0,65535),limits,tls,cert,key,replay,outbox,values.get("platformUrl"),values.get("loggingLevel"),games,count);
    }
    private static Path path(Path base,String value){return value.isBlank()?null:base.resolve(value).normalize();}
    private static boolean bool(String value){if(!Set.of("true","false").contains(value)){throw new IllegalArgumentException("Boolean requires true/false");}return Boolean.parseBoolean(value);}
    private static int number(Map<String,String> values,String key,int min,int max){int n=Integer.parseInt(values.get(key));if(n<min||n>max){throw new IllegalArgumentException("Configuration range: "+key);}return n;}
    public List<RoomHost.Spec> rooms(){if(mode.equals("platform")){return List.of();}var registry=GameRegistry.builtIn();var result=new ArrayList<RoomHost.Spec>();for(String game:games){for(int i=1;i<=roomsPerGame;i++){result.add(new RoomHost.Spec(game+"-"+i,game,limits.config(game,registry.require(game).defaults()),42L+i));}}return List.copyOf(result);}
}
