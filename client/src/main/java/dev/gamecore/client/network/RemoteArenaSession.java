package dev.gamecore.client.network;
import dev.gamecore.api.online.GameFactory;
import dev.gamecore.network.transport.GameNetworkConnection;
import dev.gamecore.online.runtime.StateHashes;
import dev.gamecore.protocol.GameProtocol;
import java.util.Arrays;
import dev.gamecore.client.ui.PlayerText;
/** 渲染线程拥有的多游戏远端会话。只消费不可变网络值；不创建第二份权威模拟。 */
public final class RemoteArenaSession implements AutoCloseable {
    private final Thread owner=Thread.currentThread();
    private void check(){if(Thread.currentThread()!=owner){throw new IllegalStateException("Remote session render owner violated");}}
    private final GameFactory<?,?> factory;private final GameNetworkConnection network;private GameProtocol.Snapshot snapshot;
    private String status="CONNECTING",notice="正在连接",settlement="";private long sequence;private boolean closed;
    private final ArenaInputSchedule inputSchedule=new ArenaInputSchedule();
    private long snapshotArrival,rejectedInputs;
    private GameProtocol.Snapshot consumed;
    private String match="",controlError="";private long controlSequence,readyRevision=-1;
    private GameProtocol.RoomState roomState;
    private java.util.Map<String,String> pendingReady;
    private final java.util.List<dev.gamecore.api.event.GameEvent> events=new java.util.ArrayList<>();
    private long eventTick=-1;private String inputError="";
    public RemoteArenaSession(GameFactory<?,?> factory,String host,int port,String room){
        this(factory,host,port,room,"");
    }
    public RemoteArenaSession(GameFactory<?,?> factory,String host,int port,String room,String ticket){
        this(factory,host,port,room,ticket,false);
    }
    public RemoteArenaSession(GameFactory<?,?> factory,String host,int port,String room,String ticket,boolean tls){
        this(factory,host,port,room,ticket,tls,false);
    }
    public RemoteArenaSession(GameFactory<?,?> factory,String host,int port,String room,String ticket,boolean tls,boolean interactive){
        this(factory,host,port,room,ticket,tls,interactive,null);
    }
    /** traffic 只在开发账户登录时非空，其余情况下连接不安装旁路。 */
    public RemoteArenaSession(GameFactory<?,?> factory,String host,int port,String room,String ticket,boolean tls,boolean interactive,dev.gamecore.network.transport.TrafficObserver traffic){
        this.factory=factory;var t=factory.type();String ca=System.getProperty("gamecore.tls.ca","");network=GameNetworkConnection.openTicket(host,port,room,t.id(),t.gameVersion(),t.rulesVersion(),ticket,null,tls,ca.isBlank()?null:java.nio.file.Path.of(ca),interactive,traffic);
    }
    public void update(java.util.function.Supplier<byte[]> commands,long now){
        update(commands,now,()->{ });
    }
    /** 切换输入世代先在渲染线程清锁存，再允许采样；旧按键不能被重新编码成新世代输入。 */
    public void update(java.util.function.Supplier<byte[]> commands,long now,Runnable clearInput){
        check();
        if(closed){return;}
        events.clear();
        try{
            var joined=network.joined();
            if(joined!=null&&!joined.getMatchId().equals(match)){
                match=joined.getMatchId();snapshot=consumed=null;roomState=null;settlement=controlError=inputError="";pendingReady=null;readyRevision=-1;sequence=0;inputSchedule.reset();snapshotArrival=rejectedInputs=0;eventTick=-1;status="WAITING";
            }
            var state=network.roomState();
            if(state!=null&&state.getMatchId().equals(match)&&!state.equals(roomState)){
                validateRoom(state);
                if(roomState==null||state.getInputEpoch()!=roomState.getInputEpoch()){clearInput.run();inputSchedule.reset();}
                roomState=state;controlError="";
            }
            var received=network.receivedSnapshot();if(received!=null&&received.value()!=consumed){consumed=received.value();apply(consumed);if(snapshot==consumed){snapshotArrival=received.arrivalNanos();}}
            for(int i=0;i<64;i++){var e=network.poll();if(e==null){break;}if(e.hasResult()&&e.getResult().getMatchId().equals(match)){apply(e.getResult().getFinalSnapshot());settlement="比赛结束："+PlayerText.result(e.getResult().getOutcome());notice=settlement;}else if(e.hasEvents()&&e.getEvents().getMatchId().equals(match)){acceptEvents(e.getEvents());}else if(e.hasError()){if(e.getError().getInputSequence()>0){inputError=e.getError().getCode();rejectedInputs++;if(inputError.equals("LATE_INPUT")){inputSchedule.lateInput();}}else{pendingReady=null;controlError=notice=PlayerText.error(e.getError().getCode());}}}
            if(roomState!=null){
                status=roomState.getPhase();notice=roomNotice(roomState);
                if(pendingReady!=null&&java.util.Set.of("WAITING","SETUP").contains(status)){
                    var choices=dev.gamecore.online.ProtocolValues.config(roomState.getConfig()).options();
                    if(choices.entrySet().containsAll(pendingReady.entrySet())){pendingReady=null;control(GameProtocol.RoomAction.READY);}
                }
            }
            if(!network.failure().isEmpty()){if(!terminal()){status="DISCONNECTED";notice="连接已断开，请检查网络后返回大厅重新进入";}}
            else if(network.joined()!=null&&snapshot==null){status="WAITING";notice="已加入，等待另一位玩家";}
            if(network.failure().equals("SERVER_UPGRADE_REQUIRED")){status="ERROR";notice="服务器需要升级后才能使用选角、选图等完整功能";}
            if(!settlement.isEmpty()&&status.equals("FINISHED")){notice=settlement;}
            if(!controlError.isEmpty()){notice=controlError;}
            if(status.equals("RUNNING")&&(roomState==null||!roomState.getPaused())&&snapshot!=null){
                long target=inputSchedule.target(now,network.joined().getConfig().getRoomTps(),snapshot.getServerTick(),snapshotArrival,network.rttNanos(),false);
                if(target>0){byte[] command=commands.get();if(command!=null){factory.inputs().decode(command);network.input(++sequence,target,command);}}
            }
        }catch(RuntimeException invalid){status="ERROR";notice="收到无效游戏数据，已安全断开";network.closeAsync();}
    }
    private void apply(GameProtocol.Snapshot value){
        if(!value.getMatchId().equals(match)){return;}
        if(snapshot!=null&&(value.getServerTick()<snapshot.getServerTick()||terminal()&&value.getStatus().equals("RUNNING"))){return;}
        if(!value.getGameId().equals(factory.type().id())||!Arrays.equals(StateHashes.hash(factory.type().id(),factory.type().rulesVersion(),value.getState().toByteArray()),value.getStateHash().toByteArray())){throw new IllegalArgumentException("State identity/hash");}
        String outcome=outcome(factory.snapshots(),value.getState().toByteArray());snapshot=value;status=value.getStatus();notice=switch(status){case "RUNNING"->"比赛进行中";case "WAITING"->"等待另一位玩家";case "FINISHED"->"比赛结束："+PlayerText.result(outcome);case "ABORTED","FAILED"->"比赛已结束或中断，可返回大厅";default->PlayerText.roomState(status);};
    }
    private static <S> String outcome(dev.gamecore.api.online.GameSnapshotCodec<S> codec,byte[] bytes){return codec.outcome(codec.decode(bytes));}
    public boolean terminal(){return switch(status){case "FINISHED","ABORTED","FAILED","ERROR","DISCONNECTED","CLOSED"->true;default->false;};}
    public GameProtocol.Snapshot snapshot(){return snapshot;}public String status(){return status;}public String notice(){return notice;}
    public String match(){return match;}
    public GameProtocol.RoomState roomState(){return roomState;}
    public java.util.List<dev.gamecore.api.event.GameEvent> events(){return java.util.List.copyOf(events);}
    public String inputError(){return inputError;}
    public long snapshotArrivalNanos(){return snapshotArrival;}
    public long rejectedInputs(){return rejectedInputs;}
    /** 详情页诊断来自实际连接计数；RTT不是服务器CPU耗时，年龄也不是精确单向延迟。 */
    public String diagnostics(long now){return "RTT "+(network.rttNanos()==0?"测量中":network.rttNanos()/1_000_000+" ms")
            +" · 快照 "+String.format(java.util.Locale.ROOT,"%.1f",network.snapshotRate())+" /秒"
            +"\n快照年龄 "+(snapshot==null?"--":Math.max(0,now-snapshotArrival)/1_000_000+" ms")+" · 余量 "+inputSchedule.marginTicks()+"步"
            +"\n输入拒绝 "+rejectedInputs+(inputError.isEmpty()?"":" ("+inputError+")");}
    private void acceptEvents(GameProtocol.EventBatch value){
        dev.gamecore.online.ProtocolValues.known(value);if(!value.getRoomId().equals(network.joined().getRoomId())){throw new IllegalArgumentException("Event room");}
        long last=value.getEventsList().stream().mapToLong(GameProtocol.GameEvent::getTick).max().orElse(-1);if(last<=eventTick){return;}
        for(var event:value.getEventsList()){if(events.size()>=1024){throw new IllegalArgumentException("Event capacity");}events.add(dev.gamecore.online.ProtocolValues.event(event));}eventTick=last;
    }
    private void validateRoom(GameProtocol.RoomState value){
        dev.gamecore.online.ProtocolValues.known(value);dev.gamecore.online.ProtocolValues.known(value.getConfig());
        for(var option:value.getConfig().getOptionsList()){dev.gamecore.online.ProtocolValues.known(option);}
        factory.validate(dev.gamecore.online.ProtocolValues.config(value.getConfig()));
        if(!java.util.Set.of("WAITING","SETUP","OPENING","RUNNING","PAUSED","FINISHED","ABORTED","FAILED","RESETTING").contains(value.getPhase())||value.getOpeningMillis()<0||value.getOpeningMillis()>factory.openingMillis()){throw new IllegalArgumentException("Invalid room phase");}
        var slots=new java.util.HashSet<Integer>();for(var seat:value.getSeatsList()){dev.gamecore.online.ProtocolValues.known(seat);if(seat.getSlot()<0||seat.getSlot()>1||!slots.add(seat.getSlot())){throw new IllegalArgumentException("Invalid room seat");}}
    }
    private String roomNotice(GameProtocol.RoomState value){
        String text=switch(value.getPhase()){
            case "WAITING" -> "等待另一位玩家，可先完成选择";
            case "SETUP" -> "选择完成后准备 · "+value.getSeatsList().stream().filter(GameProtocol.RoomSeat::getReady).count()+" / 2 已准备";
            case "OPENING" -> "登场演出 · Enter / Space 双方同意跳过";
            case "PAUSED" -> "已暂停 · 双方点击继续后恢复";
            case "RESETTING" -> "正在准备新一局，请稍候";
            case "RUNNING" -> "比赛进行中";
            case "FINISHED" -> "比赛结束，可再来一局";
            default -> "比赛已中断，可重开或返回大厅";
        };
        if(value.getSeatsList().stream().anyMatch(GameProtocol.RoomSeat::getRestart)){text="收到重开请求 · 点击重新开始同意，或取消请求";}
        else if(value.getPaused()&&!value.getPhase().equals("PAUSED")){text="演出已暂停 · 双方点击继续后恢复";}
        return text;
    }
    public void control(GameProtocol.RoomAction action){control(action,java.util.Map.of());}
    public void control(GameProtocol.RoomAction action,java.util.Map<String,String> choices){check();controlError="";if(action==GameProtocol.RoomAction.READY&&roomState!=null){readyRevision=roomState.getRevision();}network.control(++controlSequence,action,choices);}
    /** 已发出且未被新 revision 作废的准备请求；自动准备据此避免重复提交。 */
    public boolean readyRequested(){return roomState!=null&&(pendingReady!=null||readyRevision==roomState.getRevision());}
    /** 连接/控制错误必须原样展示，不能被游戏状态文案覆盖。 */
    public boolean alerting(){return !controlError.isEmpty()||switch(status){case "ABORTED","FAILED","ERROR","DISCONNECTED","CLOSED"->true;default->false;};}
    /** 等服务端选择确认后再以新 revision 准备，不能让连点越过配置提交。 */
    public void select(java.util.Map<String,String> choices,boolean ready){
        check();if(roomState==null){return;}
        if(dev.gamecore.online.ProtocolValues.config(roomState.getConfig()).options().entrySet().containsAll(choices.entrySet())){if(ready){control(GameProtocol.RoomAction.READY);}return;}
        pendingReady=ready?java.util.Map.copyOf(choices):null;control(GameProtocol.RoomAction.SELECT,choices);
    }
    public GameNetworkConnection network(){return network;}
    public void release(byte[] neutral){check();if(snapshot!=null&&status.equals("RUNNING")&&network.active()&&neutral!=null){long target=inputSchedule.target(System.nanoTime(),network.joined().getConfig().getRoomTps(),snapshot.getServerTick(),snapshotArrival,network.rttNanos(),true);if(target>0){network.input(++sequence,target,neutral);}}}
    @Override public void close(){check();if(!closed){closed=true;status="CLOSED";network.closeAsync();}}
}
