package dev.gamecore.server.devtools;
import com.google.protobuf.ByteString;
import dev.gamecore.api.online.*;
import dev.gamecore.online.ProtocolValues;
import dev.gamecore.online.runtime.*;
import dev.gamecore.protocol.ReplayProtocol;
import dev.gamecore.server.multigame.RoomActor;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** 有界异步录像：Room只创建不可变记录并offer；唯一写线程执行磁盘IO和保留策略。
 * 满队列/超限明确标记失败，未封口文件保持.part，绝不伪装完整录像。 */
public final class ReplayStore implements AutoCloseable {
    public static final int MAX_RECORD=65_536,MAX_TICKS=100_000;public static final long MAX_FILE=64L*1024*1024;
    private static final java.util.regex.Pattern OWN=java.util.regex.Pattern.compile("[a-z0-9-]{1,32}-[0-9a-f-]{36}\\.gcr(?:\\.part)?");
    private final Path directory;private final ArrayBlockingQueue<Runnable> jobs;private final Thread writer;private final Runnable beforeWrite;
    private final List<Sink> sinks=new CopyOnWriteArrayList<>();private final AtomicBoolean closing=new AtomicBoolean();
    private final Semaphore recordingSlots=new Semaphore(128);
    private final LongAdder completed=new LongAdder(),failed=new LongAdder();
    public ReplayStore(Path directory) throws IOException{this(directory,256);}
    public ReplayStore(Path directory,int capacity) throws IOException{
        this(directory,capacity,()->{});
    }
    /** 包内测试可阻塞写线程，验证Room不会等待慢磁盘；生产无阻塞注入。 */
    ReplayStore(Path directory,int capacity,Runnable beforeWrite) throws IOException{
        if(capacity<1||capacity>256){throw new IllegalArgumentException("Replay queue capacity");}
        this.directory=directory.toAbsolutePath().normalize();Files.createDirectories(this.directory);if(Files.isSymbolicLink(this.directory)){throw new IOException("Replay directory must not be symbolic link");}
        this.beforeWrite=beforeWrite;jobs=new ArrayBlockingQueue<>(capacity);cleanup();writer=new Thread(this::run,"replay-writer");writer.start();
    }
    private final class Sink {
        final Path part,done;final AtomicBoolean bad=new AtomicBoolean(),retired=new AtomicBoolean();OutputStream stream;long bytes;boolean ended;
        Sink(String game){String file=game+"-"+UUID.randomUUID()+".gcr";done=directory.resolve(file);part=directory.resolve(file+".part");}
        void fail(){if(bad.compareAndSet(false,true)){failed.increment();}}
        void write(ReplayProtocol.Record value){
            if(bad.get()){release();return;}
            try{if(ended){throw new IOException("Already ended");}if(stream==null){stream=new BufferedOutputStream(Files.newOutputStream(part,StandardOpenOption.CREATE_NEW));}
                int size=value.getSerializedSize();if(size<1||size>MAX_RECORD||bytes+size+4>MAX_FILE){throw new IOException("Replay size limit");}
                var out=new DataOutputStream(stream);out.writeInt(size);value.writeTo(out);bytes+=size+4L;
                if(value.hasEnd()){stream.close();stream=null;ended=true;Files.move(part,done,StandardCopyOption.ATOMIC_MOVE);completed.increment();retire();cleanup();}
            }catch(IOException|RuntimeException e){fail();release();}
        }
        void retire(){if(retired.compareAndSet(false,true)){sinks.remove(this);recordingSlots.release();}}
        void release(){if(stream!=null){try{stream.close();}catch(IOException ignored){/* 失败文件不封口。 */}stream=null;}retire();}
    }
    private void offer(Sink sink,ReplayProtocol.Record value){if(closing.get()||sink.bad.get()||value.getSerializedSize()>MAX_RECORD||!jobs.offer(()->sink.write(value))){sink.fail();}}
    public RoomActor.Observer recorder(int interval){
        if(interval<1||interval>600){throw new IllegalArgumentException("Snapshot interval");}
        return new RoomActor.Observer(){Sink sink;GameFactory<?,?> factory;GameStep last;
            @Override public void started(String room,String match,GameFactory<?,?> f,GameRuleConfig config,long seed,GameStep initial){
                factory=f;last=initial;if(closing.get()||!recordingSlots.tryAcquire()){failed.increment();sink=null;return;}
                sink=new Sink(f.type().id());sinks.add(sink);
                var header=ReplayProtocol.ReplayHeader.newBuilder().setFormatVersion(1).setGame(ProtocolValues.type(f.type())).setConfig(ProtocolValues.config(config)).setRandomSeed(seed)
                    .setInitialWorldState(ByteString.copyFrom(initial.snapshot())).setInitialHash(hash(initial)).setSnapshotInterval(interval);
                offer(sink,ReplayProtocol.Record.newBuilder().setHeader(header).build());
            }
            private ByteString hash(GameStep s){return ByteString.copyFrom(StateHashes.hash(factory.type().id(),factory.type().rulesVersion(),s.snapshot()));}
            @Override public void step(GameStep s){if(sink==null||sink.bad.get()){return;}if(s.tick()!=last.tick()+1||s.tick()>MAX_TICKS){sink.fail();return;}last=s;
                var frame=ReplayProtocol.ReplayFrame.newBuilder().setInputTick(s.tick()).setStateHash(hash(s)).addAllDisplayEvents(s.events());
                for(var i:s.inputs()){frame.addInputs(ReplayProtocol.ReplayInput.newBuilder().setPlayer(i.player()).setSourceSequence(i.sourceSequence()).setCanonicalSequence(i.canonicalSequence()).setCommand(ByteString.copyFrom(i.command())));}
                if(s.tick()%interval==0||s.state()==dev.gamecore.api.session.SessionState.FINISHED){frame.setPeriodicSnapshot(ByteString.copyFrom(s.snapshot()));}
                offer(sink,ReplayProtocol.Record.newBuilder().setFrame(frame).build());
            }
            @Override public void ended(String reason,GameStep s){if(sink!=null){offer(sink,ReplayProtocol.Record.newBuilder().setEnd(ReplayProtocol.ReplayEnd.newBuilder().setFinalTick(last.tick()).setStateHash(hash(last)).setReason(reason)).build());sink=null;}}
        };
    }
    private void run(){try{while(!closing.get()||!jobs.isEmpty()){
        var job=jobs.poll(100,TimeUnit.MILLISECONDS);if(job!=null){beforeWrite.run();job.run();}for(var sink:sinks){if(sink.bad.get()){sink.release();}}
    }}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{for(var sink:sinks){sink.fail();sink.release();}}}
    /** 只清理本目录中本格式自有文件；7天/最多16份，总完成文件不超过1GiB，孤立part保留1天诊断。 */
    public void cleanup() throws IOException{
        try(var paths=Files.list(directory)){var files=paths.filter(p->OWN.matcher(p.getFileName().toString()).matches()&&Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).sorted(Comparator.comparingLong(ReplayStore::modified).reversed()).toList();
            int kept=0;long total=0,now=System.currentTimeMillis();for(var p:files){boolean part=p.toString().endsWith(".part");long age=now-modified(p);if(part){if(age>Duration.ofDays(1).toMillis()&&!sinks.stream().anyMatch(s->s.part.equals(p))){Files.deleteIfExists(p);}continue;}
                long size=Files.size(p);if(age>Duration.ofDays(7).toMillis()||kept>=16||total+size>1024L*1024*1024){Files.deleteIfExists(p);}else{kept++;total+=size;}}
        }
    }
    private static long modified(Path p){try{return Files.getLastModifiedTime(p,LinkOption.NOFOLLOW_LINKS).toMillis();}catch(IOException e){return 0;}}
    public int queued(){return jobs.size();}public int activeRecordings(){return sinks.size();}public long completed(){return completed.sum();}public long failed(){return failed.sum();}public boolean terminated(){return !writer.isAlive();}
    @Override public void close(){if(Thread.currentThread()==writer){throw new IllegalStateException("Writer cannot join self");}closing.set(true);try{writer.join(10000);if(writer.isAlive()){throw new IllegalStateException("Replay close timeout");}}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
