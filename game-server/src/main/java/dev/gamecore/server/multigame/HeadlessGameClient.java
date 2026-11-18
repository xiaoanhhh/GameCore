package dev.gamecore.server.multigame;
import dev.gamecore.api.online.GameFactory;
import dev.gamecore.network.transport.*;
import dev.gamecore.protocol.GameProtocol;
import java.util.concurrent.*;
/** 非图形机器人；行为由游戏注册器提供，连接与输入仍走真实TCP与服务器校验。 */
public final class HeadlessGameClient implements AutoCloseable {
    private final GameFactory<?,?> factory;private final GameNetworkConnection network;private long sequence,steps;
    public HeadlessGameClient(String host,int port,String room,GameFactory<?,?> factory){this(host,port,room,factory,null);}
    public HeadlessGameClient(String host,int port,String room,GameFactory<?,?> factory,io.netty.channel.EventLoopGroup shared){this(host,port,room,factory,shared,false,null);}
    public HeadlessGameClient(String host,int port,String room,GameFactory<?,?> factory,io.netty.channel.EventLoopGroup shared,boolean tls,java.nio.file.Path ca){this.factory=factory;var t=factory.type();network=GameNetworkConnection.openTicket(host,port,room,t.id(),t.gameVersion(),t.rulesVersion(),"",shared,tls,ca);}
    private static <I,S> byte[] command(GameFactory<I,S> factory,GameProtocol.Snapshot snapshot,int slot,long step,boolean active){return factory.inputs().encode(factory.bot(factory.snapshots().decode(snapshot.getState().toByteArray()),slot,step,active));}
    public boolean step(boolean active){var s=network.latest();var j=network.joined();if(s==null||j==null||!s.getStatus().equals("RUNNING")||!network.active()){return false;}
        int tps=j.getConfig().getRoomTps();long period=1_000_000_000L/tps,age=Math.max(0,System.nanoTime()-network.lastSnapshotNanos());long transit=network.rttNanos()==0?100_000_000L:network.rttNanos()+2*period;
        long lead=Math.min(Math.max(2,tps/2),Math.max(2,(transit+age+period-1)/period));network.input(++sequence,s.getServerTick()+lead,command(factory,s,j.getPlayerSlot(),steps++,active));return true;}
    public GameNetworkConnection network(){return network;}
    @Override public void close(){network.close();}
}
