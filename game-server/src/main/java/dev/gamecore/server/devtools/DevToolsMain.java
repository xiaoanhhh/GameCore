package dev.gamecore.server.devtools;
import dev.gamecore.online.GameRegistry;
import dev.gamecore.api.online.*;
import dev.gamecore.server.multigame.*;
import jdk.jfr.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** 本地工具组合入口；文件、JFR和等待全在控制面，调试器没有公网管理协议。 */
public final class DevToolsMain {
    private DevToolsMain() { }
    public static Map<String,String> options(String[] args,Set<String> allowed){var values=new TreeMap<String,String>();for(int i=1;i<args.length;i++){int at=args[i].indexOf('=');if(!args[i].startsWith("--")||at<3){throw new IllegalArgumentException("Use --key=value");}String key=args[i].substring(2,at);if(!allowed.contains(key)||values.putIfAbsent(key,args[i].substring(at+1))!=null){throw new IllegalArgumentException("Invalid option "+key);}}return values;}
    public static int integer(Map<String,String> o,String key,int fallback,int min,int max){int value=Integer.parseInt(o.getOrDefault(key,""+fallback));if(value<min||value>max){throw new IllegalArgumentException("Range: "+key);}return value;}
    public static boolean flag(Map<String,String> o,String key,boolean fallback){String value=o.getOrDefault(key,""+fallback);if(!Set.of("true","false").contains(value)){throw new IllegalArgumentException("Boolean: "+key);}return Boolean.parseBoolean(value);}
    public static List<RoomHost.Spec> specs(GameRegistry registry,String game,int count,int players,boolean complex){
        var factories=game.equals("all")?registry.all():List.of(registry.require(game));var result=new ArrayList<RoomHost.Spec>();var counters=new HashMap<String,Integer>();
        for(int i=0;i<count;i++){var f=factories.get(i%factories.size());var options=new TreeMap<>(f.defaults().options());
            // 性能场景的游戏专属配置在组合工厂处选择，不进入Room/网络基础设施。
            Map<String,Map<String,String>> scenarios=complex?Map.of("tank",Map.of("map","crossfire"),"snake",Map.of("map","obstacles"),"fight",Map.of("map","BAMBOO")):
                Map.of("tank",Map.of("map","open"),"snake",Map.of("map","clear"),"fight",Map.of("map","NEON"));
            options.putAll(scenarios.getOrDefault(f.type().id(),Map.of()));int n=counters.merge(f.type().id(),1,Integer::sum);var config=new GameRuleConfig(players,f.defaults().roomTps(),options);f.validate(config);
            result.add(new RoomHost.Spec(f.type().id()+"-"+n,f.type().id(),config,42+i));
        }return List.copyOf(result);
    }
    public static void main(String[] args) throws Exception{
        if(args[0].equals("load")){LoadMain.main(args);return;}if(args[0].equals("jfr-report")){JfrReport.main(args);return;}
        if(args[0].equals("replay")){
            var o=options(args,Set.of("file","tick","speed","interactive"));var player=new ReplayPlayer(Path.of(Objects.requireNonNull(o.get("file"),"--file required")),GameRegistry.builtIn());
            long tick=Long.parseLong(o.getOrDefault("tick","0"));player.seek(tick);player.speed(Double.parseDouble(o.getOrDefault("speed","1")));
            System.out.println("REPLAY_VERIFIED game="+player.header().getGame().getGameId()+" ticks="+player.length()+" tick="+player.frame().tick()+" hash="+player.frame().hash());
            if(flag(o,"interactive",false)){replayConsole(player,new java.io.InputStreamReader(System.in,java.nio.charset.StandardCharsets.UTF_8),System.out);}return;
        }
        var o=options(args,Set.of("host","port","game","rooms","players","seconds","collision","metrics","replay-dir","jfr"));
        String bind=o.getOrDefault("host","127.0.0.1");if(!Set.of("127.0.0.1","::1").contains(bind)){throw new IllegalArgumentException("Tools server requires literal loopback");}
        int port=integer(o,"port",7200,0,65535),seconds=integer(o,"seconds",20,1,3600),rooms=integer(o,"rooms",3,1,128),players=integer(o,"players",2,1,8);
        var games=GameRegistry.builtIn();var specs=specs(games,o.getOrDefault("game","all"),rooms,players,flag(o,"collision",true));
        Path metrics=Path.of(o.getOrDefault("metrics","build/reports/performance/server.json")).toAbsolutePath();Files.createDirectories(metrics.getParent());
        try(var store=o.containsKey("replay-dir")?new ReplayStore(Path.of(o.get("replay-dir"))):null;
            var server=NettyMultiGameServer.start(bind,port,games,specs,true,s->store==null?new RoomActor.Observer(){}:store.recorder(120));
            var recording=o.containsKey("jfr")?new Recording(Configuration.getConfiguration("profile")):null){
            if(recording!=null){Path file=Path.of(o.get("jfr")).toAbsolutePath();Files.createDirectories(file.getParent());recording.setMaxSize(64L*1024*1024);recording.setMaxAge(Duration.ofMinutes(5));recording.setDestination(file);recording.start();}
            var telemetry=new ServerTelemetry(server);System.out.println("TOOLS_SERVER_READY port="+server.address().getPort()+" pid="+ProcessHandle.current().pid());System.out.flush();
            var wait=new CountDownLatch(1);for(int i=0;i<seconds;i++){telemetry.sample();wait.await(1,TimeUnit.SECONDS);}telemetry.sample();
            var report=new LinkedHashMap<>(telemetry.report());report.put("configuredRooms",rooms);report.put("playersPerRoom",players);report.put("complexCollisionScenario",flag(o,"collision",true));report.put("singleServerJvm",true);
            report.put("replayCompletedBeforeClose",store==null?0:store.completed());report.put("replayFailed",store==null?0:store.failed());
            Files.writeString(metrics,Json.encode(report));if(recording!=null){recording.stop();}
        }System.out.println("TOOLS_SERVER_STOPPED");
    }
    /** 播放控制与显示时钟只操作已验证索引；标准输入线程只投递有界命令。 */
    static void replayConsole(ReplayPlayer player,java.io.Reader input,java.io.PrintStream output) throws Exception{
        var commands=new ArrayBlockingQueue<String>(32);
        Thread.ofPlatform().daemon(true).name("replay-console-input").start(()->{try{var reader=new java.io.BufferedReader(input);for(String line;(line=reader.readLine())!=null;){if(line.length()<=256){commands.put(line);}}commands.put("quit");}catch(java.io.IOException|InterruptedException e){Thread.currentThread().interrupt();commands.offer("quit");}});
        output.println("REPLAY_COMMANDS play pause step seek <tick> speed <0.25..32> restart show quit");long previous=System.nanoTime(),nextPrint=previous;
        for(;;){String command=commands.poll(50,TimeUnit.MILLISECONDS);long now=System.nanoTime();player.update(Math.min(1,(now-previous)/1e9));previous=now;
            if(command!=null){var parts=command.strip().split(" +");try{switch(parts[0]){
                case "quit"->{return;}case "play"->player.play();case "pause"->player.pause();case "step"->player.step();
                case "seek"->player.seek(Long.parseLong(parts[1]));case "speed"->player.speed(Double.parseDouble(parts[1]));case "restart"->player.restart();case "show"->{ }
                default->throw new IllegalArgumentException("Unknown command");
            }}catch(IllegalArgumentException|IndexOutOfBoundsException invalid){output.println("REPLAY_COMMAND_REJECTED");}
                output.println("REPLAY_TICK "+player.frame().tick()+" paused="+player.paused()+" hash="+player.frame().hash());
            }else if(!player.paused()&&now>=nextPrint){output.println("REPLAY_TICK "+player.frame().tick()+" hash="+player.frame().hash());nextPrint=now+1_000_000_000L;}
        }
    }
}
