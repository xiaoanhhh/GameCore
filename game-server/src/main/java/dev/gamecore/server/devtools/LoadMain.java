package dev.gamecore.server.devtools;
import dev.gamecore.online.GameRegistry;
import dev.gamecore.network.transport.*;
import dev.gamecore.server.multigame.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
/** 独立客户端进程负载；共享两个IO线程，单控制定时器按配置Hz发意图，结束房间成组重连。
 * 只允许回环目标，避免未经授权把开发压测发往公网服务。 */
public final class LoadMain {
    private LoadMain() { }
    private static final class Group {
        final RoomHost.Spec spec;final int count;final List<HeadlessGameClient> clients=new ArrayList<>();final Set<HeadlessGameClient> counted=new HashSet<>();
        final Set<HeadlessGameClient> failures=new HashSet<>();long retry,next;
        Group(RoomHost.Spec spec,int count){this.spec=spec;this.count=count;}
    }
    public static void main(String[] args) throws Exception{
        var o=DevToolsMain.options(args,Set.of("host","port","game","rooms","players","clients","seconds","hz","collision","output","tls","ca"));
        boolean tls=DevToolsMain.flag(o,"tls",false);Path ca=o.containsKey("ca")?Path.of(o.get("ca")):null;if(ca!=null&&!tls){throw new IllegalArgumentException("CA requires TLS");}
        String host=o.getOrDefault("host","127.0.0.1");if(!Set.of("127.0.0.1","::1").contains(host)){throw new IllegalArgumentException("Load target must be literal loopback");}
        int port=DevToolsMain.integer(o,"port",7200,1,65535),rooms=DevToolsMain.integer(o,"rooms",3,1,128),players=DevToolsMain.integer(o,"players",2,1,8),clients=DevToolsMain.integer(o,"clients",rooms*players,1,256),seconds=DevToolsMain.integer(o,"seconds",10,1,3600),hz=DevToolsMain.integer(o,"hz",20,1,60);
        if(clients>rooms*players){throw new IllegalArgumentException("Clients exceed configured seats");}
        var games=GameRegistry.builtIn();var specs=DevToolsMain.specs(games,o.getOrDefault("game","all"),rooms,players,DevToolsMain.flag(o,"collision",true));
        var groups=new ArrayList<Group>();int remaining=clients;for(var spec:specs){int count=Math.min(players,remaining);if(count>0){groups.add(new Group(spec,count));remaining-=count;}}
        long[] counts=new long[9]; // attempts, joins, failed handshakes, inputs, snapshots, bytes, completed rounds, errors, disconnects
        var errorCodes=new TreeMap<String,Long>();
        var io=IoResources.group(2,"load-io");var executor=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"load-controller"));var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        long start=System.nanoTime();
        Runnable collect=()->{long now=System.nanoTime();try{
            for(var group:groups){
                if(group.clients.isEmpty()&&now>=group.retry){for(int i=0;i<group.count;i++){group.clients.add(new HeadlessGameClient(host,port,group.spec.id(),games.require(group.spec.game()),io,tls,ca));counts[0]++;}}
                boolean ended=false;
                for(var client:group.clients){var n=client.network();if(n.ready().isCompletedExceptionally()){if(group.failures.add(client)){counts[2]++;}ended=true;}
                    if(n.joined()!=null&&group.counted.add(client)){counts[1]++;}
                    for(int i=0;i<64;i++){var e=n.poll();if(e==null){break;}if(e.hasError()){counts[7]++;String code=e.getError().getCode();if(code.length()>64||errorCodes.size()>=32&&!errorCodes.containsKey(code)){code="OTHER";}errorCodes.merge(code,1L,Long::sum);}if(e.hasResult()){ended=true;}}
                    var latest=n.latest();if(latest!=null&&Set.of("FINISHED","ABORTED","FAILED").contains(latest.getStatus())){ended=true;}
                    if(n.joined()!=null&&!n.active()){counts[8]++;ended=true;}
                }
                if(ended){counts[6]++;for(var c:group.clients){counts[4]+=c.network().snapshots();counts[5]+=c.network().bytesReceived();c.network().closeAsync();}group.clients.clear();group.counted.clear();group.failures.clear();group.retry=now+250_000_000L;continue;}
                if(now>=group.next){for(var c:group.clients){if(c.step(true)){counts[3]++;}}group.next=now+1_000_000_000L/hz;}
            }
        }catch(Throwable t){failure.compareAndSet(null,t);}};
        try{
            executor.scheduleWithFixedDelay(collect,0,5,TimeUnit.MILLISECONDS);new CountDownLatch(1).await(seconds,TimeUnit.SECONDS);
        }finally{
            executor.shutdown();if(!executor.awaitTermination(10,TimeUnit.SECONDS)){executor.shutdownNow();}
            for(var group:groups){for(var c:group.clients){counts[4]+=c.network().snapshots();counts[5]+=c.network().bytesReceived();c.close();}}IoResources.stop(io);
        }
        if(failure.get()!=null){throw new IllegalStateException("Load controller failed",failure.get());}
        double elapsed=(System.nanoTime()-start)/1e9;var report=new LinkedHashMap<String,Object>();String[] keys={"connectionAttempts","joined","connectionFailures","inputsSent","snapshotsReceived","bytesReceived","completedOrRestartedGroups","serverErrors","unexpectedDisconnects"};for(int i=0;i<keys.length;i++){report.put(keys[i],counts[i]);}
        report.put("tls",tls);report.put("serverErrorCodes",Map.copyOf(errorCodes));report.put("elapsedSeconds",elapsed);report.put("requestedSeconds",seconds);report.put("concurrentClients",clients);report.put("rooms",rooms);report.put("playersPerRoom",players);report.put("inputHz",hz);report.put("game",o.getOrDefault("game","all"));report.put("complexCollisionScenario",DevToolsMain.flag(o,"collision",true));report.put("connectionFailureRatio",counts[0]==0?0:(double)counts[2]/counts[0]);report.put("snapshotRatePerSecond",counts[4]/elapsed);
        Path output=Path.of(o.getOrDefault("output","build/reports/performance/client.json")).toAbsolutePath();Files.createDirectories(output.getParent());Files.writeString(output,Json.encode(report));System.out.println("LOAD_COMPLETE "+Json.encode(report));
    }
}
