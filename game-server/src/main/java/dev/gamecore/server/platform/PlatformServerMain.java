package dev.gamecore.server.platform;
import dev.gamecore.online.*;
import dev.gamecore.api.online.*;
import dev.gamecore.online.runtime.GameStep;
import dev.gamecore.server.multigame.*;
import dev.gamecore.server.devtools.ReplayStore;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
/** 平台模式为独立显式启动命令；密钥仅从环境读取，不进入命令行/录像/日志。 */
public final class PlatformServerMain {
    private PlatformServerMain() { }
    public static void main(String[] args) throws Exception {
        var options=new HashMap<String,String>();for(int i=1;i<args.length;i++){String[] pair=args[i].split("=",2);if(pair.length!=2||!pair[0].startsWith("--")||options.putIfAbsent(pair[0].substring(2),pair[1])!=null){throw new IllegalArgumentException("Use --key=value");}}
        if(!Set.of("host","port","platform-url","outbox","replay-dir").containsAll(options.keySet())){throw new IllegalArgumentException("Unknown option");}
        var games=GameRegistry.builtIn();
        try(var replay=new ReplayStore(Path.of(options.getOrDefault("replay-dir","build/platform-replays")));
            var gateway=new PlatformGateway(options.getOrDefault("platform-url","http://127.0.0.1:8080"),System.getenv("GAME_SERVER_KEY"),Path.of(options.getOrDefault("outbox","build/platform-outbox")),games);
            var server=NettyMultiGameServer.startPlatform(options.getOrDefault("host","127.0.0.1"),Integer.parseInt(options.getOrDefault("port","7201")),games,false,gateway,s->combine(replay.recorder(120),gateway.observer()))){
            var hook=new Thread(()->shutdown(server,gateway,replay),"platform-game-shutdown");Runtime.getRuntime().addShutdownHook(hook);
            try{System.out.println("PLATFORM_GAME_SERVER_READY "+server.address());new CountDownLatch(1).await();}finally{try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){ }}
        }
    }
    private static void shutdown(NettyMultiGameServer server,PlatformGateway gateway,ReplayStore replay){try{server.close();}finally{try{gateway.close();}finally{replay.close();}}}
    public static RoomActor.Observer combine(RoomActor.Observer a,RoomActor.Observer b){return new RoomActor.Observer(){
        @Override public java.util.concurrent.CompletableFuture<String> nextMatch(String previous){return b.nextMatch(previous);}
        @Override public void started(String room,String match,GameFactory<?,?> f,GameRuleConfig c,long seed,GameStep initial){a.started(room,match,f,c,seed,initial);b.started(room,match,f,c,seed,initial);}
        @Override public void step(GameStep step){a.step(step);b.step(step);}
        @Override public void abandoned(String room,String match){a.abandoned(room,match);b.abandoned(room,match);}
        @Override public void ended(String reason,GameStep last){a.ended(reason,last);b.ended(reason,last);}
    };}
}
