package dev.gamecore.server.multigame;
import dev.gamecore.online.GameRegistry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
/** 多游戏控制台入口；控制面允许等待，EventLoop与Room不读取控制台或文件。 */
public final class MultiGameMain {
    private MultiGameMain() { }
    public static void main(String[] args) throws Exception{
        var options=new TreeMap<String,String>();for(int i=1;i<args.length;i++){int at=args[i].indexOf('=');if(!args[i].startsWith("--")||at<3||options.putIfAbsent(args[i].substring(2,at),args[i].substring(at+1))!=null){throw new IllegalArgumentException("Use --key=value");}}
        var games=GameRegistry.builtIn();String host=options.getOrDefault("host","127.0.0.1");int port=Integer.parseInt(options.getOrDefault("port","7200"));
        if(args[0].equals("multi")){
            if(!Set.of("host","port","dev","daemon","replay","replay-dir").containsAll(options.keySet())){throw new IllegalArgumentException("Unknown option");}
            boolean dev=flag(options,"dev"),daemon=flag(options,"daemon");
            String recordOption=options.getOrDefault("replay","true");if(!Set.of("true","false").contains(recordOption)){throw new IllegalArgumentException("replay must be true/false");}
            try(var replay=Boolean.parseBoolean(recordOption)?new dev.gamecore.server.devtools.ReplayStore(java.nio.file.Path.of(options.getOrDefault("replay-dir","build/replays"))):null;
                var server=NettyMultiGameServer.start(host,port,games,RoomHost.defaults(games),dev,s->replay==null?new RoomActor.Observer(){}:replay.recorder(120))){
                var hook=new Thread(server::close,"games-shutdown");Runtime.getRuntime().addShutdownHook(hook);
                try{System.out.println("MULTI_SERVER_READY "+server.address()+" rooms="+server.rooms().rooms().stream().map(RoomActor::id).toList());
                    if(daemon){new CountDownLatch(1).await();}else{var in=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));for(String line;(line=in.readLine())!=null;){
                        if(line.equals("/quit")){break;}
                        if(line.equals("/rooms")){for(var r:server.rooms().rooms()){var m=r.metrics();System.out.println("ROOM id="+m.room()+" game="+m.game()+" tick="+m.tick()+" entities="+m.entities()+" queued="+m.queued()+" hash="+m.hash()+" simulationNanos="+m.simulationNanos()+" snapshotNanos="+m.snapshotNanos());}continue;}
                        if(dev){try{var debugger=new dev.gamecore.server.devtools.TickDebugger(server.rooms(),true);String[] parts=line.strip().split(" +");if(parts.length>=2){
                            if(parts[0].equals("/pause")){debugger.pause(parts[1]).get(3,TimeUnit.SECONDS);}
                            else if(parts[0].equals("/resume")){debugger.resume(parts[1]).get(3,TimeUnit.SECONDS);}
                            else if(parts[0].equals("/step")){int count=parts.length==3?Integer.parseInt(parts[2]):1;if(count<1||count>6000){throw new IllegalArgumentException("Step count 1..6000");}for(int i=0;i<count;i+=60){debugger.advance(parts[1],Math.min(60,count-i)).get(3,TimeUnit.SECONDS);}}
                            else{throw new IllegalArgumentException("Unknown command");}System.out.println("DEBUG_DONE "+parts[1]);
                        }}catch(Exception e){System.out.println("DEBUG_REJECTED "+e.getClass().getSimpleName());}}
                    }}
                }finally{try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){}}
            }
        }else if(args[0].equals("bot")){
            if(!Set.of("host","port","game","room","seconds","hz").containsAll(options.keySet())){throw new IllegalArgumentException("Unknown option");}
            String game=options.getOrDefault("game","snake");int seconds=Integer.parseInt(options.getOrDefault("seconds","10")),hz=Integer.parseInt(options.getOrDefault("hz","10"));
            if(seconds<1||seconds>3600||hz<1||hz>60){throw new IllegalArgumentException("Duration/frequency");}
            try(var client=new HeadlessGameClient(host,port,options.getOrDefault("room",game+"-1"),games.require(game))){
                System.out.println("JOINED "+client.network().ready().get(5,TimeUnit.SECONDS));long end=System.nanoTime()+seconds*1_000_000_000L,next=0;
                while(System.nanoTime()<end&&client.network().active()){
                    var event=client.network().receive(java.time.Duration.ofMillis(10));if(event!=null){System.out.println("RESPONSE "+event.getPayloadCase());if(event.hasResult()){System.out.println(event.getResult());break;}}
                    long now=System.nanoTime();if(now>=next){client.step(true);next=now+1_000_000_000L/hz;}
                }System.out.println("SNAPSHOTS "+client.network().snapshots()+" bytes="+client.network().bytesReceived());
            }
        }else{throw new IllegalArgumentException("Expected multi or bot");}
    }
    private static boolean flag(Map<String,String> options,String name){String v=options.getOrDefault(name,"false");if(!v.equals("true")&&!v.equals("false")){throw new IllegalArgumentException(name+" must be true/false");}return Boolean.parseBoolean(v);}
}
