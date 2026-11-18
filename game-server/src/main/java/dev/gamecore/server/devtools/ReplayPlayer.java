package dev.gamecore.server.devtools;
import dev.gamecore.online.*;
import dev.gamecore.online.runtime.*;
import dev.gamecore.protocol.ReplayProtocol;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** 先从初始配置重模拟并验证，再在64MiB只读播放索引上暂停/单步/跳转。
 * 展示事件从不再次投递给Session；公开周期快照不冒充隐藏World检查点。 */
public final class ReplayPlayer {
    public record Frame(long tick,byte[] snapshot,String hash,List<dev.gamecore.protocol.GameProtocol.GameEvent> displayEvents){
        public Frame{snapshot=snapshot.clone();displayEvents=List.copyOf(displayEvents);}@Override public byte[] snapshot(){return snapshot.clone();}
    }
    private final ReplayProtocol.ReplayHeader header;private final ReplayProtocol.ReplayEnd end;private final List<Frame> frames;
    private int position;private boolean paused=true;private double speed=1,credit;
    public ReplayPlayer(Path file,GameRegistry registry) throws IOException{
        if(Files.size(file)>ReplayStore.MAX_FILE){throw new IOException("Replay file too large");}
        var records=new ArrayList<ReplayProtocol.ReplayFrame>();ReplayProtocol.ReplayHeader h;ReplayProtocol.ReplayEnd footer=null;
        try(var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))){
            long[] budget={0};var first=read(in,budget);if(first==null||!first.hasHeader()){throw new IOException("Missing header");}h=first.getHeader();ProtocolValues.known(h);
            for(ReplayProtocol.Record r;(r=read(in,budget))!=null;){
                if(footer!=null){throw new IOException("Trailing data");}
                if(r.hasFrame()){if(records.size()>=ReplayStore.MAX_TICKS){throw new IOException("Too many ticks");}records.add(r.getFrame());}
                else if(r.hasEnd()){footer=r.getEnd();}else{throw new IOException("Repeated header");}
            }
        }
        if(footer==null||h.getFormatVersion()!=1||h.getSnapshotInterval()<1||h.getSnapshotInterval()>600){throw new IOException("Incomplete or unsupported replay");}
        header=h;end=footer;frames=verify(registry,records);
    }
    private static ReplayProtocol.Record read(DataInputStream in,long[] budget) throws IOException{
        int first=in.read();if(first<0){return null;}int size=(first<<24)|(in.readUnsignedByte()<<16)|(in.readUnsignedByte()<<8)|in.readUnsignedByte();
        budget[0]+=size+4L;if(size<1||size>ReplayStore.MAX_RECORD||budget[0]>ReplayStore.MAX_FILE){throw new IOException("Record/file size");}byte[] bytes=in.readNBytes(size);if(bytes.length!=size){throw new EOFException("Truncated record");}
        var coded=com.google.protobuf.CodedInputStream.newInstance(bytes);coded.setRecursionLimit(16);coded.setSizeLimit(ReplayStore.MAX_RECORD);var r=ReplayProtocol.Record.parseFrom(coded);ProtocolValues.known(r);return r;
    }
    private List<Frame> verify(GameRegistry registry,List<ReplayProtocol.ReplayFrame> records) throws IOException{
        try{
            var factory=registry.require(header.getGame().getGameId());if(!ProtocolValues.type(factory.type()).equals(header.getGame())){throw new IllegalArgumentException("Game/rules version mismatch");}
            var config=ProtocolValues.config(header.getConfig());factory.validate(config);var result=new ArrayList<Frame>();long bytes=0,sequence=0;
            try(var game=GameInstance.create(factory,config,header.getRandomSeed())){
                var initial=game.initial();byte[] hash=StateHashes.hash(factory.type().id(),factory.type().rulesVersion(),initial.snapshot());
                if(!Arrays.equals(initial.snapshot(),header.getInitialWorldState().toByteArray())||!Arrays.equals(hash,header.getInitialHash().toByteArray())){throw new IllegalArgumentException("Initial state mismatch");}
                result.add(new Frame(0,initial.snapshot(),StateHashes.hex(hash),List.of()));bytes+=initial.snapshot().length;game.start();
                for(var record:records){
                    ProtocolValues.known(record);if(record.getInputTick()!=result.size()||record.getInputsCount()>config.players()||record.getDisplayEventsCount()>128||record.getStateHash().size()!=32){throw new IllegalArgumentException("Tick/order/count invalid");}
                    var applied=new ArrayList<AppliedInput>();var players=new HashSet<Integer>();
                    for(var input:record.getInputsList()){
                        ProtocolValues.known(input);if(input.getPlayer()>=config.players()||input.getPlayer()<0||!players.add(input.getPlayer())||input.getCanonicalSequence()<=sequence||input.getCommand().size()>ProtocolValues.MAX_INPUT||input.getCommand().isEmpty()){throw new IllegalArgumentException("Input order/bounds invalid");}
                        sequence=input.getCanonicalSequence();applied.add(new AppliedInput(input.getPlayer(),input.getSourceSequence(),sequence,input.getCommand().toByteArray()));
                    }
                    var step=game.step(applied);hash=StateHashes.hash(factory.type().id(),factory.type().rulesVersion(),step.snapshot());
                    if(!Arrays.equals(hash,record.getStateHash().toByteArray())){throw new IllegalArgumentException("State hash mismatch at tick "+step.tick());}
                    if(record.hasPeriodicSnapshot()&&!Arrays.equals(step.snapshot(),record.getPeriodicSnapshot().toByteArray())){throw new IllegalArgumentException("Periodic snapshot mismatch");}
                    if(!step.events().equals(record.getDisplayEventsList())){throw new IllegalArgumentException("Display event mismatch");}
                    bytes+=step.snapshot().length;if(bytes>64L*1024*1024){throw new IllegalArgumentException("Playback index exceeds 64MiB");}
                    // 已核验的周期快照也可直接作为该位置的显示索引；均不恢复内部World。
                    byte[] snapshot=record.hasPeriodicSnapshot()?record.getPeriodicSnapshot().toByteArray():step.snapshot();
                    result.add(new Frame(step.tick(),snapshot,StateHashes.hex(hash),record.getDisplayEventsList()));
                }
                ProtocolValues.known(end);if(end.getFinalTick()!=result.size()-1||!Arrays.equals(hash,end.getStateHash().toByteArray())||!end.getReason().matches("[A-Z_]{1,40}")){throw new IllegalArgumentException("Footer mismatch");}
            }return List.copyOf(result);
        }catch(IllegalArgumentException|IllegalStateException e){throw new IOException("Replay verification failed: "+e.getMessage(),e);}
    }
    public ReplayProtocol.ReplayHeader header(){return header;}public ReplayProtocol.ReplayEnd end(){return end;}
    public Frame frame(){return frames.get(position);}public long length(){return frames.size()-1L;}public boolean paused(){return paused;}
    public void pause(){paused=true;}public void play(){paused=false;}public void restart(){position=0;credit=0;paused=true;}
    public Frame step(){paused=true;position=Math.min(position+1,frames.size()-1);return frame();}
    public Frame seek(long tick){if(tick<0||tick>=frames.size()){throw new IllegalArgumentException("Seek outside replay");}position=(int)tick;credit=0;return frame();}
    public void speed(double value){if(!Double.isFinite(value)||value<.25||value>32){throw new IllegalArgumentException("Playback speed");}speed=value;}
    public Frame update(double seconds){if(!Double.isFinite(seconds)||seconds<0||seconds>1){throw new IllegalArgumentException("Playback delta");}if(!paused){credit+=seconds*header.getGame().getSimulationTps()*speed;int advance=(int)credit;credit-=advance;position=Math.min(position+advance,frames.size()-1);if(position==frames.size()-1){paused=true;}}return frame();}
}
