package dev.gamecore.server.multigame;
import dev.gamecore.api.online.*;
import dev.gamecore.online.GameRegistry;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
/** 有界房间目录与固定分片执行器；房间不跨线程迁移，销毁在所属分片收束。 */
public final class RoomHost implements AutoCloseable {
    public record Spec(String id,String game,GameRuleConfig config,long seed) { }
    private final ConcurrentMap<String,RoomActor> rooms=new ConcurrentHashMap<>();
    private final List<ScheduledExecutorService> executors=new ArrayList<>();
    private final ServerLimits limits;private final java.util.concurrent.atomic.AtomicLongArray progress;
    private final GameRegistry registry;private final boolean development;private final Function<Spec,RoomActor.Observer> observers;private boolean closed;private final Set<Thread> owners=ConcurrentHashMap.newKeySet();
    public RoomHost(GameRegistry games,List<Spec> specs,int shardCount,boolean development,Function<Spec,RoomActor.Observer> observers){
        this(games,specs,shardCount,development,observers,ServerLimits.legacy());
    }
    public RoomHost(GameRegistry games,List<Spec> specs,int shardCount,boolean development,Function<Spec,RoomActor.Observer> observers,ServerLimits limits){
        this.limits=limits;progress=new java.util.concurrent.atomic.AtomicLongArray(shardCount);
        this.registry=games;this.development=development;this.observers=observers;
        if(specs.size()>limits.maxRooms()||shardCount<1||shardCount>8){throw new IllegalArgumentException("Room/shard limit");}
        // 全部配置先验证，启动前失败不留下线程或半个目录。
        var staged=new ArrayList<RoomActor>();for(var spec:specs){var factory=games.require(spec.game());staged.add(new RoomActor(spec.id(),factory,limits.config(spec.game(),spec.config()),spec.seed(),development,observers.apply(spec),limits));}
        if(staged.stream().map(RoomActor::id).distinct().count()!=staged.size()){throw new IllegalArgumentException("Duplicate room");}
        for(int i=0;i<shardCount;i++){final int index=i;executors.add(Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"game-room-"+index);owners.add(t);return t;}));}
        for(var room:staged){rooms.put(room.id(),room);}
        for(int i=0;i<shardCount;i++){int shard=i;executors.get(i).scheduleWithFixedDelay(()->{
            long now=System.nanoTime();for(var room:rooms.values()){if(shard(room.id())==shard){room.runDue(now);if(room.metrics().status().equals("CLOSED")){rooms.remove(room.id(),room);}}}progress.set(shard,System.nanoTime());
        },0,1,TimeUnit.MILLISECONDS);}
    }
    public static List<Spec> defaults(GameRegistry games){return games.all().stream().map(g->new Spec(g.type().id()+"-1",g.type().id(),g.defaults(),42)).toList();}
    public RoomActor require(String id){var r=rooms.get(id);if(r==null){throw new IllegalArgumentException("Unknown room");}return r;}
    public List<RoomActor> rooms(){return rooms.values().stream().sorted(Comparator.comparing(RoomActor::id)).toList();}
    /** 平台后台验证后按需注册Actor；Session仍由房间分片owner创建。 */
    public synchronized RoomActor ensure(Spec spec){
        spec=new Spec(spec.id(),spec.game(),limits.config(spec.game(),spec.config()),spec.seed());
        if(closed){throw new IllegalStateException("Room host closed");}
        var existing=rooms.get(spec.id());if(existing!=null){if(!existing.factory().type().id().equals(spec.game())||!existing.initialConfig().equals(spec.config())){throw new IllegalArgumentException("Room configuration conflict");}return existing;}
        if(rooms.size()>=limits.maxRooms()){throw new IllegalStateException("Room limit");}
        var room=new RoomActor(spec.id(),registry.require(spec.game()),spec.config(),spec.seed(),development,observers.apply(spec),limits);
        rooms.put(spec.id(),room);return room;
    }
    public CompletableFuture<Void> destroy(String id){
        return destroyIfMatch(id,null);
    }
    /** 旧租约/旧回调不能销毁同房的新比赛，条件检查和销毁一起在 Room owner 执行。 */
    public CompletableFuture<Void> destroyIfMatch(String id,String match){
        var result=new CompletableFuture<Void>();var room=require(id);executors.get(shard(id)).execute(()->{try{if(match==null||room.metrics().match().equals(match)){room.close();rooms.remove(id,room);}result.complete(null);}catch(RuntimeException e){result.completeExceptionally(e);}});return result;
    }
    public void retire(String id,String match){var room=require(id);executors.get(shard(id)).execute(()->{room.retire(match);if(room.metrics().status().equals("CLOSED")){rooms.remove(id,room);}});}
    public boolean terminated(){return executors.stream().allMatch(ExecutorService::isTerminated);}
    // 分片由ID稳定计算，不保留另一张容易泄漏/与销毁重建竞争的索引表。
    private int shard(String id){return Math.floorMod(id.hashCode(),executors.size());}
    public int scheduledRooms(){return rooms.size();}
    /** 只读存活检查：每个逻辑分片必须在最近五秒内完成一次泵循环。 */
    public boolean healthy(){long now=System.nanoTime();for(int i=0;i<progress.length();i++){if(progress.get(i)==0||now-progress.get(i)>5_000_000_000L){return false;}}return !terminated();}
    @Override public synchronized void close(){
        if(closed){return;}closed=true;
        if(owners.contains(Thread.currentThread())){throw new IllegalStateException("Cannot wait for room owner");}
        try{for(var room:rooms()){executors.get(shard(room.id())).submit(room::close).get(10,TimeUnit.SECONDS);}rooms.clear();}
        catch(Exception e){throw new IllegalStateException("Room close failed",e);}
        finally{for(var executor:executors){executor.shutdownNow();try{executor.awaitTermination(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}}
    }
}
