package dev.gamecore.server.multigame;
import com.google.protobuf.ByteString;
import dev.gamecore.api.online.*;
import dev.gamecore.api.session.SessionState;
import dev.gamecore.online.ProtocolValues;
import dev.gamecore.online.runtime.*;
import dev.gamecore.protocol.GameProtocol;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** 单写入者房间。网络只offer有界邮箱；同一分片线程创建、推进、销毁GameSession。 */
public final class RoomActor implements AutoCloseable {
    public interface Observer {
        default void started(String room,String match,GameFactory<?,?> factory,GameRuleConfig config,long seed,GameStep initial) { }
        default void step(GameStep step) { }
        default void ended(String reason,GameStep last) { }
        default void abandoned(String room,String match) { }
        /** 只发起异步分配；Room 泵轮询完成值，绝不等待 HTTP/数据库。 */
        default CompletableFuture<String> nextMatch(String previous){return CompletableFuture.completedFuture(UUID.randomUUID().toString());}
    }
    public record Metrics(String room,String game,String match,String status,long tick,long coreTick,int players,int entities,int queued,
            long ticks,long overruns,long skippedDeadlines,long simulationNanos,long snapshotNanos,String hash,boolean paused,List<Long> recentTickNanos,
            long snapshotCount,long snapshotBytes,int snapshotSize) { }
    private record Incoming(RoomPeer peer,GameProtocol.Input input) { }
    private static final class Seat {
        final RoomPeer peer; final int slot; final TreeMap<Long,GameProtocol.Input> future=new TreeMap<>();
        long received,executed,lastTarget,lastApplied,lastControl; byte[] held;
        boolean ready,resume,restart,skip;
        Seat(RoomPeer p,int i){peer=p;slot=i;}
    }
    private final String id; private final GameFactory<?,?> factory; private volatile GameRuleConfig config; private final GameRuleConfig baseConfig; private final long seed,period;
    private Boolean interactive;
    private long revision=1,inputEpoch=1,openingElapsed,openingStarted,nextRoomPublish;
    private CompletableFuture<String> nextMatch;
    private boolean retireWhenEmpty;
    private String previousMatch="";
    private volatile GameProtocol.RoomState roomState;
    private final boolean development; private final Observer observer;
    private final ArrayBlockingQueue<Runnable> control=new ArrayBlockingQueue<>(64);
    private final ArrayBlockingQueue<Incoming> inbox;
    private final int pendingLimit,snapshotRate;
    private final List<Seat> seats=new ArrayList<>(); private final ArrayDeque<Long> durations=new ArrayDeque<>();
    private Thread owner; private GameInstance game; private GameStep last; private List<GameProtocol.GameEvent> delivery=List.of(); private String match="",status="WAITING";
    private long tick,canonical,nextDue,ticks,overruns,skipped,simulationNanos,snapshotNanos,snapshotCount,snapshotBytes,nextTelemetry; private int snapshotSize;
    private List<Long> durationSample=List.of(); private boolean closed,paused,recording;private volatile boolean accepting=true;
    private volatile GameProtocol.Snapshot published; private volatile Metrics metrics;
    public RoomActor(String id,GameFactory<?,?> factory,GameRuleConfig config,long seed,boolean development,Observer observer){
        this(id,factory,config,seed,development,observer,ServerLimits.legacy());
    }
    public RoomActor(String id,GameFactory<?,?> factory,GameRuleConfig config,long seed,boolean development,Observer observer,ServerLimits limits){
        if(!id.matches("[a-z0-9-]{1,40}")){throw new IllegalArgumentException("Room id");}factory.validate(config);
        this.id=id;this.factory=factory;this.config=config;this.baseConfig=config;this.seed=seed;this.development=development;this.observer=observer;
        inbox=new ArrayBlockingQueue<>(limits.maxPendingInputs());pendingLimit=limits.pendingPerConnection();snapshotRate=limits.snapshotRate()==0?config.roomTps():Math.min(limits.snapshotRate(),config.roomTps());
        period=1_000_000_000L/config.roomTps(); updateMetrics();
    }
    public String id(){return id;} public GameFactory<?,?> factory(){return factory;} public GameRuleConfig config(){return config;}
    public GameRuleConfig initialConfig(){return baseConfig;}
    public GameProtocol.RoomState roomState(){return roomState;}
    /** 终态落盘后保留交互双方以便再来一局；最后连接离开才释放平台房间。 */
    public void retire(String expected){check();if(!match.equals(expected)&&!(game==null&&previousMatch.equals(expected))){return;}retireWhenEmpty=true;if(!Boolean.TRUE.equals(interactive)||seats.isEmpty()){close();}}
    public boolean control(RoomPeer peer,GameProtocol.RoomControl value){
        if(!accepting){return false;}Runnable action=()->controlOwned(peer,value);if(!control.offer(action)){return false;}if(!accepting){control.remove(action);return false;}return true;
    }
    public Metrics metrics(){return metrics;} public GameProtocol.Snapshot latest(){return published;}
    public GameProtocol.RoomInfo info(){var m=metrics;return GameProtocol.RoomInfo.newBuilder().setRoomId(id).setGame(ProtocolValues.type(factory.type())).setConfig(ProtocolValues.config(config)).setPlayers(m.players()).setStatus(m.status()).build();}
    public boolean join(RoomPeer peer,long request){if(!accepting){return false;}Runnable action=()->joinOwned(peer,request);if(!control.offer(action)){return false;}if(!accepting){control.remove(action);return false;}return true;}
    public boolean input(RoomPeer peer,GameProtocol.Input input){
        if(!accepting){return false;}
        if(peer.pending().incrementAndGet()>pendingLimit){peer.pending().decrementAndGet();return false;}
        var entry=new Incoming(peer,input);if(!inbox.offer(entry)){peer.pending().decrementAndGet();return false;}
        if(!accepting){if(inbox.remove(entry)){peer.pending().decrementAndGet();}return false;}return true;
    }
    public CompletableFuture<Metrics> debug(boolean pause,boolean step){
        return debug(pause,step?1:0);
    }
    /** 开发控制面可有限快进；每批最多60步，仍在同一owner上依序运行规则。 */
    public CompletableFuture<Metrics> debug(boolean pause,int steps){
        var result=new CompletableFuture<Metrics>();
        if(!accepting){result.completeExceptionally(new IllegalStateException("Room closed"));return result;}
        if(steps<0||steps>60){result.completeExceptionally(new IllegalArgumentException("Debug batch limit"));return result;}
        if(!development){result.completeExceptionally(new IllegalStateException("Local development only"));return result;}
        Runnable action=()->{paused=pause;if(steps>0&&paused&&status.equals("RUNNING")){drainInputs();for(int i=0;i<steps&&status.equals("RUNNING");i++){advance();}}updateMetrics();result.complete(metrics);};
        if(!control.offer(action)){result.completeExceptionally(new IllegalStateException("Control queue full"));}else if(!accepting&&control.remove(action)){result.completeExceptionally(new IllegalStateException("Room closed"));}return result;
    }
    private void check(){if(owner==null){owner=Thread.currentThread();}else if(owner!=Thread.currentThread()){throw new IllegalStateException("Room owner violated");}}
    private void joinOwned(RoomPeer peer,long request){
        if(closed||!peer.active()){return;} if(seats.stream().anyMatch(s->s.peer==peer)){peer.error("ALREADY_JOINED","Connection already joined",0);return;}
        if(!status.equals("WAITING")||seats.size()>=config.players()){peer.error("ROOM_FULL","Room is not available",0);return;}
        if(interactive!=null&&interactive!=peer.interactive()){peer.error("SESSION_CONTROLS_REQUIRED","Client capabilities differ",0);return;}
        interactive=peer.interactive();
        if(game==null){game=GameInstance.create(factory,config,seed);last=game.initial();match=peer.reservedMatch().isEmpty()?UUID.randomUUID().toString():peer.reservedMatch();tick=0;canonical=0;}
        if(!peer.reservedMatch().isEmpty()&&!match.equals(peer.reservedMatch())){peer.error("MATCH_CONFLICT","Match unavailable",0);return;}
        int slot=peer.reservedSlot();if(slot<0){slot=0;while(hasSlot(slot)){slot++;}}
        if(slot>=config.players()||hasSlot(slot)){peer.error("SLOT_CONFLICT","Slot unavailable",0);return;}var seat=new Seat(peer,slot);seats.add(seat);seats.sort(Comparator.comparingInt(s->s.slot));
        var joined=GameProtocol.Joined.newBuilder().setRoomId(id).setMatchId(match).setPlayerSlot(slot).setGame(ProtocolValues.type(factory.type())).setConfig(ProtocolValues.config(config));
        peer.reliable(ProtocolValues.envelope(request,b->b.setJoined(joined)));
        if(seats.size()==config.players()&&Boolean.TRUE.equals(interactive)){status="SETUP";}
        else if(seats.size()==config.players()){
            observer.started(id,match,factory,config,seed,last);recording=true;game.start();last=game.initial();status="RUNNING";nextDue=System.nanoTime()+period;
        }publish();
    }
    private boolean hasSlot(int slot){return seats.stream().anyMatch(s->s.slot==slot);}
    private void accept(Incoming value){
        var peer=value.peer();var input=value.input();Seat seat=seats.stream().filter(s->s.peer==peer).findFirst().orElse(null);
        String error=null;
        if(seat==null||!peer.active()||!status.equals("RUNNING")){error="NOT_RUNNING";}
        else if(!input.getRoomId().equals(id)||!input.getMatchId().equals(match)||!input.getGameId().equals(factory.type().id())){error="WRONG_GAME_OR_MATCH";}
        else if(input.getSequence()<=0||input.getSequence()<=seat.received){error="DUPLICATE_INPUT";}
        else if(input.getTargetTick()<=tick){error="LATE_INPUT";}
        else if(input.getTargetTick()>tick+Math.max(2,config.roomTps()/2)||input.getTargetTick()<seat.lastTarget){error="FUTURE_INPUT";}
        else if(input.getCommand().isEmpty()||input.getCommand().size()>ProtocolValues.MAX_INPUT){error="BAD_INPUT";}
        else if(Boolean.TRUE.equals(interactive)&&input.getInputEpoch()!=inputEpoch){error="STALE_INPUT_EPOCH";}
        else {try{game.validateInput(input.getCommand().toByteArray());}catch(IllegalArgumentException e){error="BAD_INPUT";}}
        if(error!=null){peer.pending().decrementAndGet();peer.error(error,"Input rejected",input.getSequence());return;}
        seat.received=input.getSequence();seat.lastTarget=input.getTargetTick();var replaced=seat.future.put(input.getTargetTick(),input);if(replaced!=null){peer.pending().decrementAndGet();}
    }
    public void runDue(long now){
        check();if(closed){return;}
        try{
            Runnable action;for(int i=0;i<64&&(action=control.poll())!=null;i++){action.run();}
            for(var seat:List.copyOf(seats)){if(!seat.peer.active()){leave(seat);}}
            if(retireWhenEmpty&&seats.isEmpty()){close();return;}
            interactiveDue(now);
            if(drainInputs()>0){updateMetrics();}
            if(paused){nextDue=now+period;return;}
            int catchup=0;while(status.equals("RUNNING")&&now>=nextDue&&catchup++<3){advance();nextDue+=period;}
            if(status.equals("RUNNING")&&now>=nextDue){skipped+=(now-nextDue)/period+1;nextDue=now+period;}
        }catch(RuntimeException failure){finish("FAILED","ROOM_FAILURE");updateMetrics();}
    }
    private int drainInputs(){int count=0;Incoming input;for(int i=0;i<256&&(input=inbox.poll())!=null;i++){accept(input);count++;}return count;}
    private void advance(){
        long started=System.nanoTime();long next=tick+1;Map<Seat,GameProtocol.Input> selected=new LinkedHashMap<>();
        for(var seat:seats){while(!seat.future.isEmpty()&&seat.future.firstKey()<=next){var item=seat.future.pollFirstEntry().getValue();seat.peer.pending().decrementAndGet();selected.put(seat,item);}}
        int substeps=factory.type().simulationTps()/config.roomTps();var events=new ArrayList<GameProtocol.GameEvent>();
        for(int part=0;part<substeps;part++){
            var inputs=new ArrayList<AppliedInput>();
            for(var seat:seats){
                var input=part==0?selected.get(seat):null;
                if(input!=null){seat.held=input.getCommand().toByteArray();seat.lastApplied=next;}
                byte[] command=(input!=null)?input.getCommand().toByteArray():(next-seat.lastApplied<=Math.max(1,config.roomTps()/2)?seat.held:game.neutral());
                if(command!=null){inputs.add(new AppliedInput(seat.slot,input==null?0:input.getSequence(),++canonical,command));seat.held=game.held(command);}
            }
            last=game.step(inputs);events.addAll(last.events());simulationNanos+=last.simulationNanos();snapshotNanos+=last.snapshotNanos();observer.step(last);
            if(last.state()==SessionState.FINISHED){break;}
        }
        delivery=List.copyOf(events);tick=next;selected.forEach((seat,input)->seat.executed=input.getSequence());ticks++;
        long elapsed=System.nanoTime()-started;if(elapsed>period){overruns++;}durations.addLast(elapsed);if(durations.size()>2048){durations.removeFirst();}
        if(last.state()==SessionState.FINISHED){finish("FINISHED","RULES");}
        else if(tick*snapshotRate/config.roomTps()>(tick-1)*snapshotRate/config.roomTps()){publish();}
        else{publishEvents();updateMetrics();}
    }
    /** 所有比赛控制在 Room owner 上执行；配置、准备和输入世代一起提交。 */
    private void controlOwned(RoomPeer peer,GameProtocol.RoomControl value){
        if(closed){return;}
        Seat seat=seats.stream().filter(s->s.peer==peer).findFirst().orElse(null);
        if(seat==null||!peer.active()||!Boolean.TRUE.equals(interactive)||!value.getRoomId().equals(id)||!value.getMatchId().equals(match)
                ||value.getSequence()<=0||value.getSequence()<=seat.lastControl){peer.error("STALE_CONTROL","Control identity/sequence",0);return;}
        seat.lastControl=value.getSequence();
        try{
            if(nextMatch!=null){throw new IllegalArgumentException("Reset pending");}
            switch(value.getAction()){
                case SELECT -> {
                    if(!Set.of("WAITING","SETUP").contains(status)){throw new IllegalArgumentException("Selection phase");}
                    Map<String,String> choices=new TreeMap<>();
                    for(var option:value.getChoicesList()){if(choices.putIfAbsent(option.getKey(),option.getValue())!=null){throw new IllegalArgumentException("Duplicate choice");}}
                    var selected=factory.select(config,seat.slot,choices);
                    if(!selected.equals(config)){
                        config=selected;for(var s:seats){s.ready=false;}game.close();game=GameInstance.create(factory,config,seed);last=game.initial();revision++;clearInputs();
                    }
                }
                case READY -> {
                    if(!Set.of("WAITING","SETUP").contains(status)||value.getRevision()!=revision){throw new IllegalArgumentException("Ready revision");}
                    seat.ready=true;
                    if(seats.size()==config.players()&&seats.stream().allMatch(s->s.ready)){beginOpening();return;}
                }
                case UNREADY -> {if(!Set.of("WAITING","SETUP").contains(status)){throw new IllegalArgumentException("Ready phase");}seat.ready=false;}
                case PAUSE -> pauseOwned();
                case RESUME -> {
                    if(!paused){throw new IllegalArgumentException("Resume phase");}seat.resume=true;
                    if(seats.size()==config.players()&&seats.stream().allMatch(s->s.resume)){
                        paused=false;openingStarted=System.nanoTime();
                        if(status.equals("PAUSED")){game.resume();last=game.initial();status="RUNNING";}
                        for(var s:seats){s.resume=false;}revision++;clearInputs();nextDue=System.nanoTime()+period;
                    }
                }
                case RESTART -> {
                    if(seats.size()!=config.players()){throw new IllegalArgumentException("Restart players");}
                    if(Set.of("SETUP","WAITING").contains(status)){for(var s:seats){s.ready=false;}revision++;}
                    else{
                        if(Set.of("RUNNING","OPENING").contains(status)&&!paused){pauseOwned();}
                        seat.restart=true;
                        if(seats.stream().allMatch(s->s.restart)){
                            if(recording){finish("ABORTED","RESTARTED");}
                            clearInputs();paused=false;status="RESETTING";revision++;
                            nextMatch=observer.nextMatch(match);
                        }
                    }
                }
                case CANCEL_RESTART -> {for(var s:seats){s.restart=false;}revision++;}
                case SKIP_OPENING -> {
                    if(!status.equals("OPENING")||paused){throw new IllegalArgumentException("Opening phase");}
                    seat.skip=true;if(seats.stream().allMatch(s->s.skip)){beginCombat();return;}
                }
                default -> throw new IllegalArgumentException("Unknown control");
            }
            publish();
        }catch(IllegalArgumentException invalid){peer.error("CONTROL_REJECTED","Phase, ownership or selection changed",0);publishRoom();}
    }
    private void clearInputs(){
        Incoming item;while((item=inbox.poll())!=null){item.peer.pending().decrementAndGet();}
        for(var s:seats){release(s);s.lastTarget=tick;s.lastApplied=0;}inputEpoch++;
    }
    private void pauseOwned(){
        if(!Set.of("RUNNING","OPENING","FINISHED").contains(status)){throw new IllegalArgumentException("Pause phase");}
        if(paused){return;}
        if(status.equals("OPENING")){openingElapsed=Math.min(factory.openingMillis(),openingElapsed+Math.max(0,(System.nanoTime()-openingStarted)/1_000_000));}
        if(status.equals("RUNNING")){game.pause();last=game.initial();status="PAUSED";}
        paused=true;for(var s:seats){s.resume=false;}revision++;clearInputs();
    }
    private void beginOpening(){
        for(var s:seats){s.ready=s.resume=s.restart=s.skip=false;}
        observer.started(id,match,factory,config,seed,last);recording=true;
        openingElapsed=0;openingStarted=System.nanoTime();paused=false;revision++;clearInputs();
        if(factory.openingMillis()==0){beginCombat();}else{status="OPENING";publish();}
    }
    private void beginCombat(){
        openingElapsed=factory.openingMillis();game.start();last=game.initial();status="RUNNING";paused=false;
        for(var s:seats){s.skip=false;}revision++;clearInputs();nextDue=System.nanoTime()+period;publish();
    }
    private void interactiveDue(long now){
        if(nextMatch!=null&&nextMatch.isDone()){
            var result=nextMatch;nextMatch=null;
            try{
                String next=result.join();if(next==null||!next.matches("[a-zA-Z0-9-]{1,64}")||next.equals(match)){throw new IllegalArgumentException("Next match identity");}
                if(seats.size()!=config.players()||seats.stream().anyMatch(s->!s.peer.active())){observer.abandoned(id,next);return;}
                game.close();game=GameInstance.create(factory,config,seed);last=game.initial();match=next;tick=canonical=0;openingElapsed=0;paused=false;status="SETUP";revision++;clearInputs();
                for(var s:seats){s.ready=s.resume=s.restart=s.skip=false;s.received=s.executed=s.lastTarget=s.lastApplied=0;sendJoined(s,0);}
                publish();
            }catch(RuntimeException failure){status="ABORTED";for(var s:seats){s.restart=false;s.peer.error("REMATCH_UNAVAILABLE","Retry after result delivery",0);}publish();}
        }
        if(Boolean.TRUE.equals(interactive)&&status.equals("OPENING")&&!paused){
            long elapsed=openingElapsed+Math.max(0,(now-openingStarted)/1_000_000);
            if(elapsed>=factory.openingMillis()){beginCombat();}
            else if(now>=nextRoomPublish){publishRoom();nextRoomPublish=now+50_000_000L;}
        }
    }
    private void sendJoined(Seat seat,long request){
        var joined=GameProtocol.Joined.newBuilder().setRoomId(id).setMatchId(match).setPlayerSlot(seat.slot).setGame(ProtocolValues.type(factory.type())).setConfig(ProtocolValues.config(config));
        seat.peer.reliable(ProtocolValues.envelope(request,b->b.setJoined(joined)));
    }
    private void publishRoom(){
        if(!Boolean.TRUE.equals(interactive)||last==null){return;}
        long elapsed=openingElapsed+(status.equals("OPENING")&&!paused?Math.max(0,(System.nanoTime()-openingStarted)/1_000_000):0);
        var state=GameProtocol.RoomState.newBuilder().setRoomId(id).setMatchId(match).setPhase(status).setConfig(ProtocolValues.config(config)).setRevision(revision).setInputEpoch(inputEpoch)
                .setPaused(paused).setOpeningMillis((int)Math.min(factory.openingMillis(),elapsed)).setOpeningSeed(seed);
        for(var s:seats){state.addSeats(GameProtocol.RoomSeat.newBuilder().setSlot(s.slot).setReady(s.ready).setResume(s.resume).setRestart(s.restart).setSkip(s.skip));}
        var next=state.build();if(next.equals(roomState)){return;}roomState=next;for(var s:seats){s.peer.roomState(ProtocolValues.envelope(0,b->b.setRoomState(roomState)));}
    }
    private void leave(Seat seat){
        if(status.equals("WAITING")||status.equals("SETUP")){observer.abandoned(id,match);}
        seats.remove(seat);release(seat);
        if(Set.of("RUNNING","PAUSED","OPENING","RESETTING","SETUP").contains(status)){finish("ABORTED","PLAYER_DISCONNECTED");}
        if(seats.isEmpty()){reset();updateMetrics();}else{publish();}
    }
    private void release(Seat seat){seat.peer.pending().addAndGet(-seat.future.size());seat.future.clear();seat.held=null;}
    private void finish(String state,String reason){
        status=state;paused=false;for(var seat:seats){release(seat);}publish();
        if(published!=null){var result=GameProtocol.Result.newBuilder().setRoomId(id).setMatchId(match).setOutcome(last.outcome()).setReason(reason).setFinalSnapshot(published);
            for(var seat:seats){seat.peer.reliable(ProtocolValues.envelope(0,b->b.setResult(result)));}}
        if(recording){recording=false;observer.ended(reason,last);}
    }
    private void publish(){
        publishRoom();
        if(last==null){updateMetrics();return;}var b=GameProtocol.Snapshot.newBuilder().setRoomId(id).setMatchId(match).setGameId(factory.type().id()).setServerTick(tick).setSimulationTick(last.tick()).setStatus(status)
            .setState(ByteString.copyFrom(last.snapshot())).setStateHash(ByteString.copyFrom(StateHashes.hash(factory.type().id(),factory.type().rulesVersion(),last.snapshot())));
        for(var s:seats){b.addAcks(GameProtocol.Ack.newBuilder().setPlayerSlot(s.slot).setReceived(s.received).setExecuted(s.executed));}
        published=b.build();var envelope=ProtocolValues.envelope(0,e->e.setSnapshot(published));snapshotSize=envelope.getSerializedSize()+4;snapshotCount++;snapshotBytes+=snapshotSize;
        for(var s:seats){s.peer.snapshot(envelope);}
        publishEvents();
        updateMetrics();
    }
    private void publishEvents(){if(!delivery.isEmpty()){var events=GameProtocol.EventBatch.newBuilder().setRoomId(id).setMatchId(match).addAllEvents(delivery);delivery=List.of();for(var s:seats){s.peer.reliable(ProtocolValues.envelope(0,e->e.setEvents(events)));}}}
    private void updateMetrics(){long now=System.nanoTime();if(now>=nextTelemetry){durationSample=List.copyOf(durations);nextTelemetry=now+1_000_000_000L;}
        metrics=new Metrics(id,factory.type().id(),match,status,tick,last==null?0:last.tick(),seats.size(),last==null?0:last.entities(),inbox.size()+seats.stream().mapToInt(s->s.future.size()).sum(),ticks,overruns,skipped,simulationNanos,snapshotNanos,published==null?"":StateHashes.hex(published.getStateHash().toByteArray()),paused,durationSample,snapshotCount,snapshotBytes,snapshotSize);}
    private void reset(){
        if(nextMatch!=null){var allocation=nextMatch;nextMatch=null;if(!allocation.cancel(false)&&!allocation.isCompletedExceptionally()){String allocated=allocation.getNow(null);if(allocated!=null){observer.abandoned(id,allocated);}}}
        if(game!=null){game.close();game=null;}last=null;published=null;roomState=null;if(!match.isEmpty()){previousMatch=match;}match="";status="WAITING";tick=canonical=0;paused=false;interactive=null;config=baseConfig;revision++;inputEpoch++;openingElapsed=0;
    }
    @Override public void close(){check();if(closed){return;}accepting=false;if(game!=null&&Set.of("WAITING","SETUP").contains(status)){observer.abandoned(id,match);}if(game!=null&&!Set.of("FINISHED","ABORTED","FAILED").contains(status)){finish("ABORTED","ROOM_CLOSED");}closed=true;for(var s:seats){release(s);s.peer.disconnect();}seats.clear();Incoming item;while((item=inbox.poll())!=null){item.peer.pending().decrementAndGet();}Runnable action;while((action=control.poll())!=null){action.run();}reset();status="CLOSED";updateMetrics();}
}
