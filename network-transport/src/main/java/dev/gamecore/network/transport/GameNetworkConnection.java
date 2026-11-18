package dev.gamecore.network.transport;
import com.google.protobuf.ByteString;
import dev.gamecore.protocol.GameProtocol;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.DefaultDnsServerAddressStreamProvider;
import io.netty.channel.socket.nio.NioDatagramChannel;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** 无UI的v2连接。网络回调只写64控制事件+1最新快照邮箱；open/closeAsync不阻塞渲染线程。 */
public final class GameNetworkConnection implements AutoCloseable {
    private final EventLoopGroup io;private final boolean owned;
    private final DnsAddressResolverGroup resolver=new DnsAddressResolverGroup(NioDatagramChannel.class,DefaultDnsServerAddressStreamProvider.INSTANCE);
    private final String room,game,version,rules;private final AtomicLong requests=new AtomicLong();
    private final ArrayBlockingQueue<GameProtocol.Envelope> events=new ArrayBlockingQueue<>(64);
    /** 快照和接收时刻原子交接，避免渲染线程把旧Tick与新包的时刻拼接。 */
    public record ReceivedSnapshot(GameProtocol.Snapshot value,long arrivalNanos) { }
    private final AtomicReference<ReceivedSnapshot> latest=new AtomicReference<>();
    private final AtomicReference<GameProtocol.RoomState> roomState=new AtomicReference<>();
    private final CompletableFuture<GameProtocol.Joined> ready=new CompletableFuture<>();
    private final CompletableFuture<Void> terminated=new CompletableFuture<>();private final AtomicBoolean closing=new AtomicBoolean(),stopping=new AtomicBoolean();
    private String platformTicket="";
    private boolean interactive;
    private TrafficObserver traffic;
    private volatile Channel channel;private volatile AsyncOutbound<GameProtocol.Envelope> out;private volatile String failure="";
    private volatile GameProtocol.Joined joined;private final LongAdder received=new LongAdder(),bytes=new LongAdder();
    private volatile long rttNanos,lastSnapshotNanos;private long heartbeatRequest,heartbeatSent;
    private volatile double snapshotRate;private long rateStart,rateCount;
    private GameNetworkConnection(String room,String game,String version,String rules,EventLoopGroup shared){
        this.room=room;this.game=game;this.version=version;this.rules=rules;owned=shared==null;io=owned?IoResources.group(1,"games-client"):shared;
    }
    public static GameNetworkConnection open(String host,int port,String room,String game,String version,String rules){return open(host,port,room,game,version,rules,null);}
    public static GameNetworkConnection open(String host,int port,String room,String game,String version,String rules,EventLoopGroup shared){
        return openTicket(host,port,room,game,version,rules,"",shared);
    }
    /** 平台一次性凭证随Join发送；旧匿名端点保持不变。 */
    public static GameNetworkConnection openTicket(String host,int port,String room,String game,String version,String rules,String ticket,EventLoopGroup shared){
        return openTicket(host,port,room,game,version,rules,ticket,shared,false,null);
    }
    /** TLS上下文在调用线程构建；私有CA为显式开发配置，生产默认使用JDK信任库与SAN校验。 */
    public static GameNetworkConnection openTicket(String host,int port,String room,String game,String version,String rules,String ticket,EventLoopGroup shared,boolean tls,java.nio.file.Path ca){
        return openTicket(host,port,room,game,version,rules,ticket,shared,tls,ca,false);
    }
    /** 正式交互页面显式要求控制能力；旧服务器给出升级提示，不能静默退回缺功能的自动局。 */
    public static GameNetworkConnection openTicket(String host,int port,String room,String game,String version,String rules,String ticket,EventLoopGroup shared,boolean tls,java.nio.file.Path ca,boolean interactive){
        return openTicket(host,port,room,game,version,rules,ticket,shared,tls,ca,interactive,null);
    }
    /** traffic 非空时在业务处理器之前安装只读旁路；为空时管线与正式连接完全相同。 */
    public static GameNetworkConnection openTicket(String host,int port,String room,String game,String version,String rules,String ticket,EventLoopGroup shared,boolean tls,java.nio.file.Path ca,boolean interactive,TrafficObserver traffic){
        if(ticket==null||ticket.length()>128){throw new IllegalArgumentException("Ticket");}
        if(host.isBlank()||port<1||port>65535||!room.matches("[a-z0-9-]{1,40}")){throw new IllegalArgumentException("Endpoint");}
        if(!tls&&!ticket.isEmpty()&&!TransportTls.loopback(host)){throw new IllegalArgumentException("Public tickets require TLS");}
        final io.netty.handler.ssl.SslContext security;
        try{security=tls?TransportTls.client(ca):null;}catch(javax.net.ssl.SSLException e){throw new IllegalArgumentException("TLS configuration",e);}
        var c=new GameNetworkConnection(room,game,version,rules,shared);c.platformTicket=ticket;c.interactive=interactive;c.traffic=traffic;
        var connect=new Bootstrap().group(c.io).resolver(c.resolver).channel(NioSocketChannel.class).option(ChannelOption.CONNECT_TIMEOUT_MILLIS,3000)
            .option(ChannelOption.TCP_NODELAY,true).option(ChannelOption.WRITE_BUFFER_WATER_MARK,new WriteBufferWaterMark(8192,16384))
            .handler(new ChannelInitializer<SocketChannel>(){@Override protected void initChannel(SocketChannel ch){
                c.channel=ch;c.out=new AsyncOutbound<>(ch,()->{c.fail("SEND_OVERFLOW");ch.close();});
                if(security!=null){var ssl=security.newHandler(ch.alloc(),host,port);ssl.setHandshakeTimeoutMillis(5000);ch.pipeline().addLast("tls",ssl);}
                ch.pipeline().addLast(new ReadTimeoutHandler(10));ProtocolPipeline.install(ch.pipeline(),GameProtocol.Envelope.parser(),65536);
                if(traffic!=null){ch.pipeline().addLast("trafficTap",new TrafficTapHandler(traffic));}
                ch.pipeline().addLast(c.new Handler());
            }}).connect(host,port);
        connect.addListener(f->{if(!f.isSuccess()){c.fail("CONNECT_FAILED");c.stop();}});return c;
    }
    private GameProtocol.Envelope envelope(java.util.function.Consumer<GameProtocol.Envelope.Builder> value){var b=GameProtocol.Envelope.newBuilder().setProtocolVersion(2).setRequestId(requests.incrementAndGet());value.accept(b);return b.build();}
    private void send(GameProtocol.Envelope e){var o=out;if(o!=null&&!closing.get()){o.reliable(e);}}
    private final class Handler extends SimpleChannelInboundHandler<GameProtocol.Envelope>{
        ScheduledFuture<?> heartbeat,deadline;
        private void ping(){var message=envelope(b->b.setHeartbeat(GameProtocol.Heartbeat.getDefaultInstance()));heartbeatRequest=message.getRequestId();heartbeatSent=System.nanoTime();send(message);}
        @Override public void channelActive(ChannelHandlerContext c){
            if(closing.get()){c.close();return;}
            send(envelope(b->b.setHello(GameProtocol.Hello.newBuilder().setClientName("GameCore"))));
            deadline=c.executor().schedule(()->{if(!ready.isDone()){fail("HANDSHAKE_TIMEOUT");c.close();}},platformTicket.isEmpty()?4:8,TimeUnit.SECONDS);
            heartbeat=c.executor().scheduleAtFixedRate(this::ping,1,1,TimeUnit.SECONDS);
        }
        @Override protected void channelRead0(ChannelHandlerContext ctx,GameProtocol.Envelope e){
            bytes.add(e.getSerializedSize()+4L);
            if(e.getProtocolVersion()!=2||!e.getUnknownFields().asMap().isEmpty()){fail("PROTOCOL_VERSION");ctx.close();return;}
            if(e.hasWelcome()){
                if(joined!=null){fail("UNEXPECTED_WELCOME");ctx.close();return;}
                if(interactive&&!e.getWelcome().getSessionControls()){fail("SERVER_UPGRADE_REQUIRED");ctx.close();return;}
                // 能力验证后立即测量，入场不再先等一秒；不向已拒绝的旧服多发消息。
                ping();
                send(envelope(b->b.setJoin(GameProtocol.Join.newBuilder().setRoomId(room).setGameId(game).setGameVersion(version).setRulesVersion(rules).setCredential(e.getWelcome().getCredential()).setPlatformTicket(platformTicket).setInteractive(interactive))));
            }else if(e.hasJoined()){
                var j=e.getJoined();if(!j.getRoomId().equals(room)||!j.getGame().getGameId().equals(game)||!j.getGame().getGameVersion().equals(version)||!j.getGame().getRulesVersion().equals(rules)||j.getPlayerSlot()>=j.getConfig().getPlayers()){fail("INVALID_JOIN");ctx.close();return;}
                if(joined!=null&&!j.getMatchId().equals(joined.getMatchId())){latest.set(null);roomState.set(null);events.clear();rateStart=rateCount=0;snapshotRate=0;}
                joined=j;ready.complete(j);
            }else if(e.hasRoomState()){
                var state=e.getRoomState();
                if(!interactive||joined==null||!state.getRoomId().equals(room)||!state.getMatchId().equals(joined.getMatchId())||state.getSeatsCount()>2||state.getConfig().getOptionsCount()>2+1||state.getRevision()<=0||state.getInputEpoch()<=0){fail("INVALID_ROOM_STATE");ctx.close();return;}
                roomState.set(state);
            }else if(e.hasSnapshot()){
                var s=e.getSnapshot();if(joined==null||!s.getGameId().equals(game)||!s.getRoomId().equals(room)||!s.getMatchId().equals(joined.getMatchId())||s.getState().size()>64512){fail("INVALID_SNAPSHOT");ctx.close();return;}
                var previous=latest.get();if(previous==null||s.getServerTick()>=previous.value().getServerTick()){
                    long arrived=System.nanoTime();latest.set(new ReceivedSnapshot(s,arrived));lastSnapshotNanos=arrived;received.increment();
                    if(rateStart==0){rateStart=arrived;}else if(++rateCount>0&&arrived-rateStart>=1_000_000_000L){snapshotRate=rateCount*1_000_000_000d/(arrived-rateStart);rateStart=arrived;rateCount=0;}
                }
            }else if(e.hasHeartbeat()&&e.getRequestId()==heartbeatRequest){long sample=System.nanoTime()-heartbeatSent;if(sample>0&&sample<10_000_000_000L){rttNanos=rttNanos==0?sample:(3*rttNanos+sample)/4;}
            }else if(e.hasError()){
                if(!ready.isDone()){fail(e.getError().getCode());ctx.close();}offer(e);
            }else if(e.hasEvents()||e.hasResult()){offer(e);}
        }
        private void offer(GameProtocol.Envelope e){if(!events.offer(e)){fail("EVENT_OVERFLOW");channel.close();}}
        @Override public void channelWritabilityChanged(ChannelHandlerContext c){out.signal();}
        @Override public void channelInactive(ChannelHandlerContext c){if(heartbeat!=null){heartbeat.cancel(false);}if(deadline!=null){deadline.cancel(false);}out.clear();if(!closing.get()){fail("DISCONNECTED");}stop();}
        @Override public void exceptionCaught(ChannelHandlerContext c,Throwable e){fail(c.pipeline().get("tls")!=null&&!ready.isDone()?"TLS_OR_PROTOCOL_FAILED":"INVALID_MESSAGE");c.close();}
    }
    private void fail(String reason){if(failure.isEmpty()){failure=reason;TrafficTapHandler.report(traffic,"连接失败 "+reason);}ready.completeExceptionally(new IllegalStateException(failure));}
    private void stop(){if(!stopping.compareAndSet(false,true)){return;}resolver.close();if(owned){io.shutdownGracefully(0,3,TimeUnit.SECONDS).addListener(f->{if(f.isSuccess()){terminated.complete(null);}else{terminated.completeExceptionally(f.cause());}});}else{terminated.complete(null);}}
    public CompletableFuture<GameProtocol.Joined> ready(){return ready;}public GameProtocol.Joined joined(){return joined;}
    public GameProtocol.Snapshot latest(){var value=latest.get();return value==null?null:value.value();}
    public ReceivedSnapshot receivedSnapshot(){return latest.get();}
    public double snapshotRate(){return snapshotRate;}
    public GameProtocol.Envelope poll(){return events.poll();}
    public GameProtocol.RoomState roomState(){return roomState.get();}
    public GameProtocol.Envelope receive(Duration timeout) throws InterruptedException{return events.poll(timeout.toMillis(),TimeUnit.MILLISECONDS);}
    public boolean active(){return channel!=null&&channel.isActive()&&!closing.get();}public String failure(){return failure;}
    public int queued(){return events.size();}public long snapshots(){return received.sum();}public long bytesReceived(){return bytes.sum();}
    public long rttNanos(){return rttNanos;}public long lastSnapshotNanos(){return lastSnapshotNanos;}
    public void input(long sequence,long targetTick,byte[] command){
        var j=joined;if(j==null||command.length==0||command.length>256){throw new IllegalArgumentException("Input not ready/too large");}
        var state=roomState.get();
        send(envelope(b->b.setInput(GameProtocol.Input.newBuilder().setRoomId(room).setMatchId(j.getMatchId()).setGameId(game).setSequence(sequence).setTargetTick(targetTick).setCommand(ByteString.copyFrom(command)).setInputEpoch(state==null?0:state.getInputEpoch()))));
    }
    public void control(long sequence,GameProtocol.RoomAction action,java.util.Map<String,String> choices){
        var state=roomState.get();var j=joined;if(!interactive||state==null||j==null||choices.size()>2){throw new IllegalStateException("Controls not ready");}
        var command=GameProtocol.RoomControl.newBuilder().setRoomId(room).setMatchId(j.getMatchId()).setSequence(sequence).setRevision(state.getRevision()).setAction(action);
        choices.forEach((k,v)->command.addChoices(GameProtocol.Option.newBuilder().setKey(k).setValue(v)));send(envelope(b->b.setControl(command)));
    }
    /** 测试诊断口仍使用真实协议，服务端必须独立验证全部字段。 */
    public void sendRaw(GameProtocol.Envelope e){send(e);}
    public CompletableFuture<Void> termination(){return terminated;}
    public CompletableFuture<Void> closeAsync(){
        if(closing.compareAndSet(false,true)){
            ready.completeExceptionally(new IllegalStateException("CLOSED"));
            // 自动断线可能已终止EventLoop；不能再向该Loop的close promise添加监听器。
            // 注册中的连接由connect失败回调收束，活动连接由channelInactive收束。
            if(!stopping.get()){var ch=channel;if(ch!=null){ch.close();}else{stop();}}
        }return terminated;
    }
    @Override public void close(){IoResources.requireOutside(io);closeAsync().join();}
}
