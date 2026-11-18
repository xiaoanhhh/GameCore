package dev.gamecore.server.deployment;

import dev.gamecore.online.GameRegistry;
import dev.gamecore.network.transport.TransportTls;
import dev.gamecore.server.devtools.ReplayStore;
import dev.gamecore.server.multigame.*;
import dev.gamecore.server.platform.*;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.logging.*;

/** 无图形发行入口；SIGTERM由进程控制线程收束健康、Room、网络、结果Outbox与录像。 */
public final class DeploymentMain {
    private DeploymentMain() { }
    public static void main(String[] args) throws Exception {
        if(args.length<2||args.length>3||!args[1].startsWith("--config=")||(args.length==3&&!args[2].equals("--check"))){throw new IllegalArgumentException("serve --config=/path/server.properties [--check]");}
        var config=DeploymentConfig.load(Path.of(args[1].substring(9)),System.getenv());
        var level=switch(config.loggingLevel()){case "DEBUG"->Level.FINE;case "WARNING"->Level.WARNING;case "ERROR"->Level.SEVERE;default->Level.INFO;};Logger.getLogger("").setLevel(level);for(var h:Logger.getLogger("").getHandlers()){h.setLevel(level);}
        var tls=config.tls()?TransportTls.server(config.certificate(),config.privateKey()):null;
        String key=System.getenv("GAME_SERVER_KEY");if(config.mode().equals("platform")&&(key==null||key.length()<32||key.startsWith("REPLACE_"))){throw new IllegalArgumentException("GAME_SERVER_KEY requires a real secret of at least 32 characters");}
        if(config.mode().equals("platform")){try(var validation=new dev.gamecore.platform.api.PlatformHttpClient(config.platformUrl())){validation.token(""); /* 仅校验地址与资源生命周期，不发送HTTP。 */ }}
        // --check不绑定端口、不修改状态目录、不连接平台；用于升级前预检。
        if(args.length==3){System.out.println("CONFIG_OK mode="+config.mode()+" tls="+config.tls());return;}
        var games=GameRegistry.builtIn();
        try(var replay=new ReplayStore(config.replayDir());var gateway=config.mode().equals("platform")?new PlatformGateway(config.platformUrl(),key,config.outboxDir(),games):null;
            var server=NettyMultiGameServer.startConfigured(config.host(),config.port(),games,config.rooms(),false,s->gateway==null?replay.recorder(120):PlatformServerMain.combine(replay.recorder(120),gateway.observer()),gateway,config.limits(),tls);
            var health=new HealthEndpoint(server,config.healthPort())){
            var hook=new Thread(()->shutdown(health,server,gateway,replay),"game-deployment-shutdown");Runtime.getRuntime().addShutdownHook(hook);
            try{System.out.println("GAMECORE_READY address="+server.address()+" tls="+config.tls()+" health=127.0.0.1:"+health.port()+" mode="+config.mode());new CountDownLatch(1).await();}
            finally{try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){ /* JVM退出由hook负责。 */ }}
        }
    }
    private static void shutdown(HealthEndpoint health,NettyMultiGameServer server,PlatformGateway gateway,ReplayStore replay){
        health.drain();System.out.println("GAMECORE_DRAINING");
        try{server.close();}finally{try{if(gateway!=null){gateway.close();}}finally{try{replay.close();}finally{health.close();System.out.println("GAMECORE_STOPPED");}}}
    }
}
