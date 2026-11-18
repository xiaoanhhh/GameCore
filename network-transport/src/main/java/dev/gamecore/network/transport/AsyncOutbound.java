package dev.gamecore.network.transport;
import com.google.protobuf.MessageLite;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.*;
/** 游戏无关背压：可靠控制128项、最新快照1份、至多9条在途，只有一个唤醒任务。
 * 发送缓慢只合并快照；可靠事件溢出关闭连接。生成消息已不可变，ByteBuf由Pipeline管理。 */
public final class AsyncOutbound<M extends MessageLite>{
    public static final int CAPACITY=128;
    private final Channel channel;private final Runnable fatal;private final ArrayBlockingQueue<M> reliable=new ArrayBlockingQueue<>(CAPACITY);
    private final AtomicReference<M> latest=new AtomicReference<>();private final AtomicBoolean scheduled=new AtomicBoolean();
    private final LongAdder bytes=new LongAdder(),messages=new LongAdder();private boolean busy;private volatile boolean closeAfter;
    public AsyncOutbound(Channel channel,Runnable fatal){this.channel=channel;this.fatal=fatal;}
    public void reliable(M value){if(!reliable.offer(value)){fatal.run();}else{signal();}}
    public void snapshot(M value){latest.set(value);signal();}
    /** 新 Joined 发布前移除尚未发送的旧局快照；已在途消息仍由单个 EventLoop 保持顺序。 */
    public void clearSnapshot(){latest.set(null);}
    public void closeAfterFlush(){closeAfter=true;signal();channel.eventLoop().schedule(()->{channel.close();},1,java.util.concurrent.TimeUnit.SECONDS);}
    public void signal(){if(!scheduled.compareAndSet(false,true)){return;}try{channel.eventLoop().execute(()->{scheduled.set(false);drain();});}catch(RuntimeException e){scheduled.set(false);fatal.run();}}
    private void drain(){
        if(!channel.isActive()){clear();return;}if(busy||!channel.isWritable()){return;}
        // 可靠 Joined/配置必须先于该世代快照；有控制积压时保留最新快照，不越过控制屏障。
        var batch=new java.util.ArrayList<M>(9);for(int i=0;i<8;i++){var m=reliable.poll();if(m==null){break;}batch.add(m);}var snapshot=reliable.isEmpty()?latest.getAndSet(null):null;if(snapshot!=null){batch.add(snapshot);}
        if(batch.isEmpty()){if(closeAfter){channel.close();}return;}busy=true;ChannelFuture last=null;
        for(var m:batch){last=channel.write(m);last.addListener(f->{if(!f.isSuccess()){fatal.run();}else{bytes.add(m.getSerializedSize()+4L);messages.increment();}});}channel.flush();
        java.util.Objects.requireNonNull(last).addListener(f->{busy=false;if(!f.isSuccess()){fatal.run();}else if(!reliable.isEmpty()||latest.get()!=null||closeAfter){signal();}});
    }
    public int queued(){return reliable.size();}public long bytes(){return bytes.sum();}public long messages(){return messages.sum();}
    public void clear(){reliable.clear();latest.set(null);}
}
