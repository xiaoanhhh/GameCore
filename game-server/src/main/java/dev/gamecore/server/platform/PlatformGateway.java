package dev.gamecore.server.platform;
import dev.gamecore.platform.api.*;
import dev.gamecore.platform.api.PlatformMessages.*;
import dev.gamecore.online.GameRegistry;
import dev.gamecore.online.runtime.*;
import dev.gamecore.api.online.*;
import dev.gamecore.protocol.GameProtocol;
import dev.gamecore.server.multigame.*;
import com.google.protobuf.ByteString;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** 平台交接适配：HTTP/磁盘只在有界后台执行器。Room结束仅offer稳定值，权威结果可幂等重传。 */
public final class PlatformGateway implements AutoCloseable {
    private static final class Live {
        final Admission admission;final long born=System.nanoTime();final AtomicBoolean checking=new AtomicBoolean();volatile boolean ended;
        Live(Admission a){admission=a;}
    }
    private final PlatformHttpClient http;private final Path directory;private final GameRegistry games;
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),r->new Thread(r,"platform-handoff"),new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService writer=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"platform-outbox"));
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"platform-lease"));
    private final ArrayBlockingQueue<Finish> results=new ArrayBlockingQueue<>(128);
    private final ConcurrentMap<String,Live> live=new ConcurrentHashMap<>();
    private final Set<CompletableFuture<?>> pending=ConcurrentHashMap.newKeySet();
    private final AtomicInteger diskPending=new AtomicInteger();private final LongAdder delivered=new LongAdder(),failures=new LongAdder();
    private final AtomicBoolean stopped=new AtomicBoolean();
    private volatile RoomHost rooms;private volatile boolean closing;private long nextCheck;
    private int nextAllocations;
    public PlatformGateway(String url,String key,Path directory,GameRegistry games) throws IOException {
        if(key==null||key.length()<32){throw new IllegalArgumentException("GAME_SERVER_KEY required");}
        this.games=games;this.directory=directory.toAbsolutePath().normalize();Files.createDirectories(this.directory);
        if(Files.isSymbolicLink(this.directory)){throw new IllegalArgumentException("Outbox symlink");}
        diskPending.set(files().size());
        http=new PlatformHttpClient(url);http.token(key);writer.scheduleWithFixedDelay(this::pump,0,1,TimeUnit.SECONDS);timer.scheduleWithFixedDelay(this::heartbeat,0,2,TimeUnit.SECONDS);
    }
    public void attach(RoomHost host){if(rooms!=null){throw new IllegalStateException("Gateway already attached");}rooms=host;}
    public CompletableFuture<Admission> admit(String ticket,String connection){
        var answer=new CompletableFuture<Admission>();pending.add(answer);answer.whenComplete((a,e)->pending.remove(answer));
        if(closing||ticket==null||!ticket.matches("[A-Za-z0-9_-]{43}")){answer.completeExceptionally(new IllegalArgumentException("AUTH_REQUIRED"));return answer;}
        try{workers.execute(()->{try{
            synchronized(live){if(live.size()+diskPending.get()>=64){throw new IllegalStateException("Platform backlog");}}
            var a=http.internal("/api/internal/tickets/redeem",new Redeem(ticket,connection),Admission.class);
            var f=games.require(a.gameId());if(!f.type().gameVersion().equals(a.gameVersion())||!f.type().rulesVersion().equals(a.rulesVersion())||a.slot()<0||a.slot()>1){throw new IllegalArgumentException("Game version");}
            synchronized(live){if(!live.containsKey(a.matchId())&&live.size()+diskPending.get()>=64){throw new IllegalStateException("Platform backlog");}live.putIfAbsent(a.matchId(),new Live(a));}
            rooms.ensure(new RoomHost.Spec(a.roomId(),a.gameId(),f.defaults(),a.seed()));answer.complete(a);
        }catch(RuntimeException e){answer.completeExceptionally(new IllegalStateException("PLATFORM_ADMISSION_FAILED"));}});}catch(RejectedExecutionException e){answer.completeExceptionally(new IllegalStateException("PLATFORM_BUSY"));}return answer;
    }
    public RoomActor.Observer observer(){return new RoomActor.Observer(){
        String room,match;GameFactory<?,?> factory;Admission admitted;
        @Override public void started(String r,String m,GameFactory<?,?> f,GameRuleConfig c,long seed,GameStep initial){room=r;match=m;factory=f;var value=live.get(m);if(value!=null){admitted=value.admission;}}
        @Override public CompletableFuture<String> nextMatch(String previous){
            var result=new CompletableFuture<String>();pending.add(result);result.whenComplete((a,e)->pending.remove(result));var identity=admitted;
            if(closing||identity==null||!identity.matchId().equals(previous)){result.completeExceptionally(new IllegalStateException("No admitted match"));return result;}
            synchronized(live){if(live.size()+diskPending.get()+nextAllocations>=64){result.completeExceptionally(new IllegalStateException("Platform backlog"));return result;}nextAllocations++;}
            try{workers.execute(()->{try{
                var next=awaitNext(previous,result);
                var a=new Admission(identity.roomId(),identity.gameId(),identity.gameVersion(),identity.rulesVersion(),next.matchId(),identity.slot(),identity.userId(),identity.seed());
                var record=new Live(a);live.putIfAbsent(a.matchId(),record);
                if(result.isDone()||closing){record.ended=true;if(!results.offer(new Finish(a.matchId(),"ADMISSION_DISCONNECTED",""))){throw new IllegalStateException("Result capacity");}}
                else{result.complete(a.matchId());}
            }catch(RuntimeException failure){java.util.logging.Logger.getLogger(PlatformGateway.class.getName()).warning("Rematch allocation failed: "+(failure instanceof PlatformHttpClient.Failure error?error.status()+"/"+error.getMessage():failure.getClass().getSimpleName()));result.completeExceptionally(new IllegalStateException("REMATCH_UNAVAILABLE"));}finally{synchronized(live){nextAllocations--;}}});}
            catch(RejectedExecutionException failure){synchronized(live){nextAllocations--;}result.completeExceptionally(new IllegalStateException("PLATFORM_BUSY"));}return result;
        }
        @Override public void abandoned(String roomId,String matchId){var record=live.get(matchId);if(record==null||record.ended){return;}record.ended=true;if(!results.offer(new Finish(matchId,"ADMISSION_DISCONNECTED",""))){throw new IllegalStateException("Reserved result capacity violated");}}
        @Override public void ended(String reason,GameStep last){
            var record=live.get(match);if(record==null||record.ended){return;}record.ended=true;
            var snapshot=GameProtocol.Snapshot.newBuilder().setRoomId(room).setMatchId(match).setGameId(factory.type().id()).setServerTick(last.tick()).setSimulationTick(last.tick())
                .setStatus(reason.equals("RULES")?"FINISHED":"ABORTED").setState(ByteString.copyFrom(last.snapshot()))
                .setStateHash(ByteString.copyFrom(StateHashes.hash(factory.type().id(),factory.type().rulesVersion(),last.snapshot()))).build();
            // 最多64个活跃/未确认比赛，而邮箱128；每比赛只结束一次，容量在准入时预留。
            if(!results.offer(new Finish(match,reason,Base64.getEncoder().encodeToString(snapshot.toByteArray())))){throw new IllegalStateException("Reserved result capacity violated");}
        }
    };}
    /** 仅有界后台工作者等待正常 outbox 窗口，最长4秒；Room 保持 RESETTING，不阻塞模拟线程。 */
    private NextMatch awaitNext(String previous,CompletableFuture<String> request){
        long deadline=System.nanoTime()+4_000_000_000L;
        for(;;){
            if(closing||request.isDone()||Thread.currentThread().isInterrupted()){throw new IllegalStateException("REMATCH_CANCELLED");}
            try{return http.internal("/api/internal/matches/"+previous+"/next",null,NextMatch.class);}
            catch(PlatformHttpClient.Failure failure){
                if(failure.status()!=409||!failure.getMessage().equals("RESULT_PENDING")||System.nanoTime()>=deadline){throw failure;}
                java.util.concurrent.locks.LockSupport.parkNanos(100_000_000L);
            }
        }
    }
    private List<Path> files() throws IOException {try(var stream=Files.list(directory)){var list=stream.filter(p->p.getFileName().toString().matches("[a-f0-9-]{36}\\.pending")&&Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).limit(129).toList();if(list.size()>128){throw new IOException("Outbox capacity");}return list;}}
    private void persist() throws IOException {
        for(Finish value;(value=results.peek())!=null;){Path path=directory.resolve(value.matchId()+".pending");
            if(!path.getParent().equals(directory)||!value.matchId().matches("[a-f0-9-]{36}")){throw new IOException("Outbox identity");}
            Path temp=directory.resolve(value.matchId()+".tmp");if(Files.isSymbolicLink(temp)||Files.isSymbolicLink(path)){throw new IOException("Outbox link");}byte[] bytes=PlatformHttpClient.JSON.writeValueAsBytes(value);
            try(var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){var buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining()){channel.write(buffer);}channel.force(true);}
            Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);results.poll();diskPending.incrementAndGet();
            var record=live.remove(value.matchId());if(record!=null&&rooms!=null){try{rooms.retire(record.admission.roomId(),value.matchId());}catch(RuntimeException ignored){ }}
        }
    }
    private void pump(){
        try{persist();int budget=8;for(var path:files()){
            if(budget--<=0||closing){break;}if(Files.size(path)>100000){failures.increment();continue;}
            var value=PlatformHttpClient.JSON.readValue(Files.readAllBytes(path),Finish.class);
            try{http.internal("/api/internal/results",value,Match.class);Files.delete(path);diskPending.decrementAndGet();delivered.increment();}
            catch(PlatformHttpClient.Failure e){failures.increment();break;}
        }}catch(IOException|RuntimeException e){failures.increment();}
    }
    private void heartbeat(){
        long now=System.nanoTime();if(rooms==null||closing||now<nextCheck){return;}nextCheck=now+2_000_000_000L;
        for(var record:live.values()){
            if(record.ended||!record.checking.compareAndSet(false,true)){continue;}
            try{workers.execute(()->{try{
                var reply=http.internal("/api/internal/matches/"+record.admission.matchId()+"/active",null,Active.class);
                if(!record.ended&&(!reply.active()||System.nanoTime()-record.born>125_000_000_000L&&rooms.require(record.admission.roomId()).metrics().status().equals("WAITING"))){
                    rooms.destroyIfMatch(record.admission.roomId(),record.admission.matchId());live.remove(record.admission.matchId(),record);
                }
            }catch(RuntimeException ignored){ /* 平台暂时不可用不阻塞已开始的权威模拟。 */ }finally{record.checking.set(false);}});}catch(RejectedExecutionException e){record.checking.set(false);}
        }
    }
    public record Active(boolean active) { }
    public long delivered(){return delivered.sum();}public long failures(){return failures.sum();}public int queued(){return results.size()+diskPending.get();}
    public boolean terminated(){return workers.isTerminated()&&writer.isTerminated()&&timer.isTerminated();}
    @Override public void close(){if(!stopped.compareAndSet(false,true)){return;}closing=true;pending.forEach(f->f.completeExceptionally(new IllegalStateException("CLOSED")));workers.shutdownNow();writer.shutdownNow();timer.shutdownNow();
        try{workers.awaitTermination(6,TimeUnit.SECONDS);writer.awaitTermination(6,TimeUnit.SECONDS);timer.awaitTermination(6,TimeUnit.SECONDS);persist();}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(IOException e){throw new IllegalStateException("Outbox persistence failed",e);}finally{http.close();}}
}
