package dev.gamecore.client.network;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** 测试专用真实TCP中继：每方向延迟100ms；阻塞读写只在测试线程，不在生产EventLoop。
 * 保持长度帧顺序、128项上限；没有丢包/UDP或TCP重传行为的虚假声称。 */
final class DelayedTcpRelay implements AutoCloseable {
    private final ServerSocket listener;private final java.util.List<Socket> sockets=new CopyOnWriteArrayList<>();
    private final ExecutorService readers=Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger pending=new AtomicInteger();private final AtomicBoolean closed=new AtomicBoolean();
    private record Frame(byte[] bytes,long due) { }
    private final int delayMillis,jitterMillis;
    DelayedTcpRelay(int target) throws IOException{
        this(target,100,0);
    }
    DelayedTcpRelay(int target,int delayMillis,int jitterMillis) throws IOException{
        this.delayMillis=delayMillis;this.jitterMillis=jitterMillis;
        listener=new ServerSocket(0,8,InetAddress.getLoopbackAddress());readers.submit(()->{try{while(!closed.get()){
            var downstream=listener.accept();var upstream=new Socket("127.0.0.1",target);downstream.setTcpNoDelay(true);upstream.setTcpNoDelay(true);sockets.add(downstream);sockets.add(upstream);
            readers.submit(()->forward(downstream,upstream));readers.submit(()->forward(upstream,downstream));
        }}catch(IOException e){if(!closed.get()){throw new UncheckedIOException(e);}}});
    }
    int port(){return listener.getLocalPort();}
    private void forward(Socket source,Socket destination){try{var in=new DataInputStream(source.getInputStream());
        var queue=new ArrayBlockingQueue<Frame>(128);int frame=0;
        readers.submit(()->{try{var out=new DataOutputStream(destination.getOutputStream());while(!closed.get()){
            var item=queue.take();long wait=item.due()-System.nanoTime();if(wait>0){TimeUnit.NANOSECONDS.sleep(wait);}
            try{if(!closed.get()){out.writeInt(item.bytes().length);out.write(item.bytes());out.flush();}}finally{pending.decrementAndGet();}
        }}catch(IOException ignored){/* 真实连接处理断开。 */}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        while(!closed.get()){
            int size=in.readInt();if(size<1||size>65536){throw new IOException("Frame size");}byte[] bytes=in.readNBytes(size);if(bytes.length!=size||pending.incrementAndGet()>128){throw new IOException("Relay capacity");}
            // 每方向保持TCP字节顺序，抖动只能推迟后续帧，不能伪造成乱序的Protobuf流。
            long due=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(delayMillis+(frame++%3)*jitterMillis/2);
            if(!queue.offer(new Frame(bytes,due))){pending.decrementAndGet();throw new IOException("Relay queue full");}
        }
    }catch(IOException|RejectedExecutionException ignored){/* 测试关闭套接字解除阻塞读。 */}}
    @Override public void close(){if(!closed.compareAndSet(false,true)){return;}try{listener.close();}catch(IOException ignored){ }for(var s:sockets){try{s.close();}catch(IOException ignored){ }}readers.shutdownNow();try{readers.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
