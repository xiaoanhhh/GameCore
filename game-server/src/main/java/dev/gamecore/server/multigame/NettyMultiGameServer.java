package dev.gamecore.server.multigame;
import dev.gamecore.online.GameRegistry;
import dev.gamecore.online.ProtocolValues;
import dev.gamecore.protocol.GameProtocol;
import dev.gamecore.network.transport.*;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.*;
import io.netty.channel.socket.*;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import java.util.function.Function;

/** v2多游戏端点。IO仅做协议校验、限流、邮箱交接；游戏选择只查询注册表。 */
public final class NettyMultiGameServer implements AutoCloseable {
    private final EventLoopGroup accept,io;
    private final ChannelGroup channels;
    private final AtomicInteger connections=new AtomicInteger();private final LongAdder bytesOut=new LongAdder(),messagesOut=new LongAdder(),bytesIn=new LongAdder();
    private dev.gamecore.server.platform.PlatformGateway platform;
    private final GameRegistry games;private final RoomHost host;private Channel listener;private volatile boolean closed;
    private NettyMultiGameServer(GameRegistry games,List<RoomHost.Spec> rooms,boolean dev,Function<RoomHost.Spec,RoomActor.Observer> observers,ServerLimits limits){
        this.games=games;this.host=new RoomHost(games,rooms,limits.logicThreads(),dev,observers,limits);
        accept=IoResources.group(1,"games-accept");io=IoResources.group(limits.nettyWorkerThreads(),"games-io");channels=new DefaultChannelGroup("games-connections",io.next(),true);
    }
    public static NettyMultiGameServer start(String bind,int port,GameRegistry registry,List<RoomHost.Spec> rooms,boolean dev) throws InterruptedException {
        return start(bind,port,registry,rooms,dev,s->new RoomActor.Observer(){});
    }
    public static NettyMultiGameServer start(String bind,int port,GameRegistry registry,List<RoomHost.Spec> rooms,boolean dev,Function<RoomHost.Spec,RoomActor.Observer> observers) throws InterruptedException {
        return startInternal(bind,port,registry,rooms,dev,observers,null);
    }
    public static NettyMultiGameServer startPlatform(String bind,int port,GameRegistry registry,boolean dev,dev.gamecore.server.platform.PlatformGateway gateway,Function<RoomHost.Spec,RoomActor.Observer> observers) throws InterruptedException {
        return startInternal(bind,port,registry,List.of(),dev,observers,gateway);
    }
    private static NettyMultiGameServer startInternal(String bind,int port,GameRegistry registry,List<RoomHost.Spec> rooms,boolean dev,Function<RoomHost.Spec,RoomActor.Observer> observers,dev.gamecore.server.platform.PlatformGateway gateway) throws InterruptedException {
        return startConfigured(bind,port,registry,rooms,dev,observers,gateway,ServerLimits.legacy(),null);
    }
    /** 发行包入口显式提供限额和TLS；历史实验重载继续采用原默认值。 */
    public static NettyMultiGameServer startConfigured(String bind,int port,GameRegistry registry,List<RoomHost.Spec> rooms,boolean dev,Function<RoomHost.Spec,RoomActor.Observer> observers,dev.gamecore.server.platform.PlatformGateway gateway,ServerLimits limits,io.netty.handler.ssl.SslContext tls) throws InterruptedException {
        if(port<0||port>65535||bind.isBlank()){throw new IllegalArgumentException("Address");}
        // 调试只接受字面回环地址，避免DNS变化把控制能力带到公网。
        if(dev&&!Set.of("127.0.0.1","::1").contains(bind)){throw new IllegalArgumentException("Development requires literal loopback");}
        var server=new NettyMultiGameServer(registry,rooms,dev,observers,limits);server.platform=gateway;if(gateway!=null){gateway.attach(server.host);}
        try{server.listener=new ServerBootstrap().group(server.accept,server.io).channel(NioServerSocketChannel.class)
            .option(ChannelOption.SO_BACKLOG,128).childOption(ChannelOption.TCP_NODELAY,true)
            .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,new WriteBufferWaterMark(32768,65536))
            .childHandler(new ChannelInitializer<SocketChannel>(){
                @Override protected void initChannel(SocketChannel ch){
                    server.channels.add(ch);if(server.connections.incrementAndGet()>limits.maxConnections()){server.connections.decrementAndGet();ch.close();return;}
                    var peer=new Peer(ch);ch.closeFuture().addListener(f->{server.connections.decrementAndGet();server.bytesOut.add(peer.out.bytes());server.messagesOut.add(peer.out.messages());});
                    if(tls!=null){var ssl=tls.newHandler(ch.alloc());ssl.setHandshakeTimeoutMillis(5000);ch.pipeline().addLast("tls",ssl);}
                    ch.pipeline().addLast("readTimeout",new ReadTimeoutHandler(10));ch.pipeline().addLast("writeTimeout",new WriteTimeoutHandler(3));
                    ProtocolPipeline.install(ch.pipeline(),GameProtocol.Envelope.parser(),ProtocolValues.MAX_FRAME);
                    ch.pipeline().addLast("games",server.new Handler(peer));
                }
            }).bind(new InetSocketAddress(bind,port)).sync().channel();return server;
        }catch(InterruptedException|RuntimeException|Error e){server.close();throw e;}
    }
    private static final class Peer implements RoomPeer {
        final Channel channel;final String id=UUID.randomUUID().toString(),credential=UUID.randomUUID().toString();
        final AtomicInteger pending=new AtomicInteger();final AsyncOutbound<GameProtocol.Envelope> out;
        volatile boolean joined,rejected;RoomActor room;int slot=-1;String match="";boolean interactive;
        @Override public boolean interactive(){return interactive;}
        @Override public int reservedSlot(){return slot;}@Override public String reservedMatch(){return match;}
        Peer(Channel c){channel=c;out=new AsyncOutbound<>(c,()->c.close());}
        @Override public String id(){return id;}@Override public boolean active(){return channel.isActive();}@Override public AtomicInteger pending(){return pending;}
        @Override public void reliable(GameProtocol.Envelope e){if(e.hasJoined()){out.clearSnapshot();joined=true;}out.reliable(e);}
        @Override public void snapshot(GameProtocol.Envelope e){out.snapshot(e);}
        @Override public void disconnect(){rejected=true;out.closeAfterFlush();}
        void fail(String code){if(rejected){return;}rejected=true;error(code,"Request rejected",0);out.closeAfterFlush();}
    }
    private final class Handler extends SimpleChannelInboundHandler<GameProtocol.Envelope>{
        final Peer peer;boolean hello,requested;long epoch=System.nanoTime();int inputs,messages,beats,controls;
        Handler(Peer p){peer=p;}
        @Override public void channelActive(ChannelHandlerContext c){c.executor().schedule(()->{if(!peer.joined){peer.fail("JOIN_TIMEOUT");}},platform==null?3:8,TimeUnit.SECONDS);}
        @Override protected void channelRead0(ChannelHandlerContext ctx,GameProtocol.Envelope e){
            if(peer.rejected){return;}
            bytesIn.add(e.getSerializedSize()+4L);
            if(System.nanoTime()-epoch>=1_000_000_000L){epoch=System.nanoTime();inputs=messages=beats=controls=0;}
            if(++messages>240){peer.fail("RATE_LIMIT");return;}
            if(e.getProtocolVersion()!=ProtocolValues.VERSION){peer.fail("PROTOCOL_VERSION");return;}
            if(e.getRequestId()<=0||!e.getUnknownFields().asMap().isEmpty()){peer.fail("INVALID_ENVELOPE");return;}
            try{
                if(e.hasHello()&&!hello){
                    ProtocolValues.known(e.getHello());if(e.getHello().getClientName().isBlank()||e.getHello().getClientName().length()>64){throw new IllegalArgumentException();}hello=true;
                    var welcome=GameProtocol.Welcome.newBuilder().setConnectionId(peer.id).setCredential(peer.credential).setSessionControls(true);
                    games.all().forEach(g->welcome.addGames(ProtocolValues.type(g.type())));host.rooms().forEach(r->welcome.addRooms(r.info()));
                    peer.reliable(ProtocolValues.envelope(e.getRequestId(),b->b.setWelcome(welcome)));
                }else if(e.hasJoin()&&hello&&!requested){
                    requested=true;var j=e.getJoin();ProtocolValues.known(j);peer.interactive=j.getInteractive();
                    if(!j.getCredential().equals(peer.credential)){peer.fail("CREDENTIAL");return;}
                    if(platform!=null){
                        platform.admit(j.getPlatformTicket(),peer.id).whenComplete((admission,failure)->{
                            if(!ctx.channel().isActive()){return;}
                            try{ctx.executor().execute(()->{
                                if(!peer.active()||peer.rejected){return;}
                                if(failure!=null||!admission.roomId().equals(j.getRoomId())||!admission.gameId().equals(j.getGameId())||!admission.gameVersion().equals(j.getGameVersion())||!admission.rulesVersion().equals(j.getRulesVersion())){peer.fail("PLATFORM_ADMISSION_FAILED");return;}
                                peer.slot=admission.slot();peer.match=admission.matchId();peer.room=host.require(admission.roomId());
                                if(!peer.room.join(peer,e.getRequestId())){peer.fail("CONTROL_QUEUE_FULL");}
                            });}catch(java.util.concurrent.RejectedExecutionException ignored){ }
                        });return;
                    }
                    if(!j.getPlatformTicket().isEmpty()){peer.fail("PLATFORM_DISABLED");return;}
                    var room=host.require(j.getRoomId());var type=room.factory().type();
                    if(!type.id().equals(j.getGameId())){peer.fail("WRONG_GAME");return;}
                    if(!type.gameVersion().equals(j.getGameVersion())||!type.rulesVersion().equals(j.getRulesVersion())){peer.fail("GAME_VERSION");return;}
                    peer.room=room;if(!room.join(peer,e.getRequestId())){peer.fail("CONTROL_QUEUE_FULL");}
                }else if(e.hasControl()&&peer.joined&&peer.interactive){
                    if(++controls>16){peer.fail("CONTROL_RATE");return;}
                    var value=e.getControl();ProtocolValues.known(value);
                    if(value.getAction()==GameProtocol.RoomAction.ACTION_NONE||value.getAction()==GameProtocol.RoomAction.UNRECOGNIZED||value.getChoicesCount()>2
                            ||value.getRoomId().length()>40||value.getMatchId().length()>64||value.getAction()!=GameProtocol.RoomAction.SELECT&&value.getChoicesCount()!=0){throw new IllegalArgumentException();}
                    for(var choice:value.getChoicesList()){ProtocolValues.known(choice);if(choice.getKey().isEmpty()||choice.getKey().length()>32||choice.getValue().isEmpty()||choice.getValue().length()>64){throw new IllegalArgumentException();}}
                    if(!peer.room.control(peer,value)){peer.error("CONTROL_QUEUE_FULL","Control capacity reached",0);}
                }else if(e.hasInput()&&peer.joined){
                    if(++inputs>120){peer.fail("INPUT_RATE");return;}var input=e.getInput();ProtocolValues.known(input);
                    if(input.getCommand().size()>ProtocolValues.MAX_INPUT||input.getCommand().isEmpty()){throw new IllegalArgumentException();}
                    // 纯值解码可在IO进行，世界只能由Room验证和推进。
                    peer.room.factory().inputs().decode(input.getCommand().toByteArray());
                    if(!peer.room.input(peer,input)){peer.error("INPUT_QUEUE_FULL","Input capacity reached",input.getSequence());}
                }else if(e.hasHeartbeat()&&hello){ProtocolValues.known(e.getHeartbeat());if(++beats>4){peer.fail("HEARTBEAT_RATE");return;}peer.reliable(ProtocolValues.envelope(e.getRequestId(),b->b.setHeartbeat(e.getHeartbeat())));}
                else{peer.fail("UNEXPECTED_PAYLOAD");}
            }catch(IllegalArgumentException failure){peer.error("BAD_REQUEST","Invalid game, input or configuration",e.hasInput()?e.getInput().getSequence():0);if(!peer.joined){peer.out.closeAfterFlush();}}
        }
        @Override public void channelWritabilityChanged(ChannelHandlerContext c){peer.out.signal();}
        @Override public void channelInactive(ChannelHandlerContext c){peer.out.clear();}
        @Override public void exceptionCaught(ChannelHandlerContext c,Throwable e){c.close();}
    }
    public InetSocketAddress address(){return (InetSocketAddress)listener.localAddress();}public RoomHost rooms(){return host;}public int connections(){return connections.get();}
    public long bytesIn(){return bytesIn.sum();}
    // 活跃连接计数器在Channel自身，不为每Tick扫描/等待IO线程。
    public long bytesOut(){return bytesOut.sum()+channels.stream().mapToLong(c->{var h=c.pipeline().get(Handler.class);return h==null?0:h.peer.out.bytes();}).sum();}
    public boolean terminated(){return accept.isTerminated()&&io.isTerminated()&&host.terminated()&&channels.isEmpty();}
    public boolean healthy(){return !closed&&listener!=null&&listener.isActive()&&host.healthy();}
    @Override public synchronized void close(){
        IoResources.requireOutside(accept,io);if(closed){return;}closed=true;
        try{if(listener!=null){IoResources.await(listener.close());}
            for(var c:channels){var h=c.pipeline().get(Handler.class);if(h!=null){h.peer.error("SERVER_SHUTDOWN","Server is shutting down; please return to lobby",0);}}
            host.close();
            // Room先产生终局和Outbox事件，再给可靠消息有限冲刷窗口；绝不在EventLoop等待。
            var remaining=new java.util.concurrent.CountDownLatch(channels.size());for(var c:channels){c.closeFuture().addListener(f->remaining.countDown());var h=c.pipeline().get(Handler.class);if(h!=null){h.peer.disconnect();}else{c.close();}}
            try{remaining.await(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            IoResources.await(channels.close());}
        finally{try{IoResources.stop(io);}finally{IoResources.stop(accept);}}
    }
}
