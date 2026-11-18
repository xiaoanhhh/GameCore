package dev.gamecore.server.devtools;
import dev.gamecore.api.online.*;
import dev.gamecore.online.*;
import dev.gamecore.online.runtime.*;
import dev.gamecore.protocol.ReplayProtocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class ReplayTest {
    @TempDir Path directory;
    private static <I,S> Path record(GameFactory<I,S> factory,Path directory) throws Exception{
        var store=new ReplayStore(directory);try(store;var game=GameInstance.create(factory,factory.defaults(),42)){
            var recorder=store.recorder(20);recorder.started("unused-room","unused-match",factory,factory.defaults(),42,game.initial());game.start();long seq=0;GameStep frame=game.initial();
            for(int tick=1;tick<=200;tick++){
                var inputs=new ArrayList<AppliedInput>();var snapshot=factory.snapshots().decode(frame.snapshot());for(int p=0;p<2;p++){byte[] command=factory.inputs().encode(factory.bot(snapshot,p,tick,true));inputs.add(new AppliedInput(p,tick,++seq,command));}
                frame=game.step(inputs);recorder.step(frame);if(frame.state()==dev.gamecore.api.session.SessionState.FINISHED){break;}
            }recorder.ended("TEST",frame);
        }assertEquals(0,store.failed());assertEquals(1,store.completed());assertTrue(store.terminated());try(var files=Files.list(directory)){return files.filter(p->p.toString().endsWith(".gcr")).findFirst().orElseThrow();}
    }
    @Test void allGamesVerifyAndPlaybackSupportsPauseStepSeekFastForward() throws Exception{
        for(var factory:GameRegistry.builtIn().all()){
            var path=record(factory,directory.resolve(factory.type().id()));var player=new ReplayPlayer(path,GameRegistry.builtIn());assertEquals(0,player.frame().tick());assertTrue(player.paused());
            var one=player.step();assertEquals(1,one.tick());byte[] bytes=one.snapshot();bytes[0]^=1;assertFalse(Arrays.equals(bytes,player.frame().snapshot()));
            var end=player.seek(player.length());assertEquals(player.end().getFinalTick(),end.tick());var hash=end.hash();player.restart();player.speed(32);player.play();for(int i=0;i<200&&!player.paused();i++){player.update(.5);}assertEquals(hash,player.frame().hash());
            player.pause();long before=player.frame().tick();player.update(.5);assertEquals(before,player.frame().tick());assertThrows(IllegalArgumentException.class,()->player.seek(-1));assertThrows(IllegalArgumentException.class,()->player.speed(Double.NaN));
        }
    }
    private static List<ReplayProtocol.Record> read(Path path) throws Exception{var result=new ArrayList<ReplayProtocol.Record>();try(var in=new DataInputStream(Files.newInputStream(path))){while(in.available()>0){int size=in.readInt();result.add(ReplayProtocol.Record.parseFrom(in.readNBytes(size)));}}return result;}
    private static void write(Path path,List<ReplayProtocol.Record> records) throws Exception{try(var out=new DataOutputStream(Files.newOutputStream(path))){for(var r:records){out.writeInt(r.getSerializedSize());r.writeTo(out);}}}
    @Test void corruptHashOrderingVersionEventsAndTruncationAreRejected() throws Exception{
        var file=record(new dev.gamecore.online.tank.TankFactory(),directory);var original=read(file);var bad=directory.resolve("bad.gcr");
        var mutated=new ArrayList<>(original);var frame=mutated.get(1).getFrame();mutated.set(1,ReplayProtocol.Record.newBuilder().setFrame(frame.toBuilder().setStateHash(com.google.protobuf.ByteString.copyFrom(new byte[32]))).build());write(bad,mutated);assertThrows(IOException.class,()->new ReplayPlayer(bad,GameRegistry.builtIn()));
        mutated=new ArrayList<>(original);mutated.set(1,ReplayProtocol.Record.newBuilder().setFrame(frame.toBuilder().setInputTick(99)).build());write(bad,mutated);assertThrows(IOException.class,()->new ReplayPlayer(bad,GameRegistry.builtIn()));
        mutated=new ArrayList<>(original);mutated.set(1,ReplayProtocol.Record.newBuilder().setFrame(frame.toBuilder().addDisplayEvents(dev.gamecore.protocol.GameProtocol.GameEvent.newBuilder().setTick(1).setKind("HEALTH_CHANGED").setCurrent(0))).build());write(bad,mutated);assertThrows(IOException.class,()->new ReplayPlayer(bad,GameRegistry.builtIn()));
        mutated=new ArrayList<>(original);mutated.set(0,original.getFirst().toBuilder().setHeader(original.getFirst().getHeader().toBuilder().setGame(original.getFirst().getHeader().getGame().toBuilder().setRulesVersion("tank/999"))).build());write(bad,mutated);assertThrows(IOException.class,()->new ReplayPlayer(bad,GameRegistry.builtIn()));
        mutated=new ArrayList<>(original);mutated.removeLast();write(bad,mutated);assertThrows(IOException.class,()->new ReplayPlayer(bad,GameRegistry.builtIn()));
        try(var out=new DataOutputStream(Files.newOutputStream(bad))){out.writeInt(ReplayStore.MAX_RECORD+1);}assertThrows(IOException.class,()->new ReplayPlayer(bad,GameRegistry.builtIn()));
    }
    @Test void noCredentialFieldsAndBoundedSlowDiskCannotBlockSimulation() throws Exception{
        assertTrue(ReplayProtocol.ReplayHeader.getDescriptor().getFields().stream().noneMatch(f->f.getName().matches(".*(password|token|credential|address|name).*")));
        var gate=new CountDownLatch(1);var entered=new CountDownLatch(1);var store=new ReplayStore(directory,1,()->{entered.countDown();try{gate.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        var f=new dev.gamecore.online.tank.TankFactory();try(var game=GameInstance.create(f,f.defaults(),1)){
            var r=store.recorder(20);r.started("room","secret-must-not-be-recorded",f,f.defaults(),1,game.initial());assertTrue(entered.await(2,TimeUnit.SECONDS));game.start();for(int i=0;i<100;i++){var frame=game.step(List.of());r.step(frame);}assertEquals(100,game.initial().tick());assertTrue(store.queued()<=1);assertTrue(store.failed()>0);r.ended("TEST",game.initial());
        }finally{gate.countDown();store.close();}assertTrue(store.terminated());assertEquals(0,store.completed());
    }
    @Test void retentionOnlyRemovesOwnOldFiles() throws Exception{
        Path own=directory.resolve("tank-12345678-1234-1234-1234-123456789abc.gcr"),other=directory.resolve("keep-my-data.txt");Files.writeString(own,"old");Files.writeString(other,"keep");Files.setLastModifiedTime(own,java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()-java.time.Duration.ofDays(8).toMillis()));
        try(var store=new ReplayStore(directory)){assertEquals(0,store.queued());assertFalse(Files.exists(own));assertTrue(Files.exists(other));}
    }
    @Test void interactiveConsoleRunsBoundedCommandsWithoutReapplyingRules() throws Exception{
        var file=record(new dev.gamecore.online.tank.TankFactory(),directory);var player=new ReplayPlayer(file,GameRegistry.builtIn());var bytes=new ByteArrayOutputStream();
        try(var output=new PrintStream(bytes, true,java.nio.charset.StandardCharsets.UTF_8)){
            DevToolsMain.replayConsole(player,new StringReader("step\nseek 20\nspeed 4\npause\nrestart\nseek 5\nshow\nquit\n"),output);
        }assertEquals(5,player.frame().tick());String text=bytes.toString(java.nio.charset.StandardCharsets.UTF_8);assertTrue(text.contains("REPLAY_TICK 20"));assertTrue(text.contains("REPLAY_TICK 5"));assertFalse(text.contains("REJECTED"));
    }
    @Test void blockedWriterAndRepeatedMatchesCannotGrowRecordingIndexWithoutLimit() throws Exception{
        var gate=new CountDownLatch(1);var entered=new CountDownLatch(1);var store=new ReplayStore(directory,1,()->{entered.countDown();try{gate.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});var f=new dev.gamecore.online.tank.TankFactory();
        try(var game=GameInstance.create(f,f.defaults(),42)){
            var first=store.recorder(20);first.started("room","match",f,f.defaults(),42,game.initial());assertTrue(entered.await(2,TimeUnit.SECONDS));
            for(int i=0;i<1000;i++){var observer=store.recorder(20);observer.started("room","match",f,f.defaults(),42,game.initial());}
            assertTrue(store.activeRecordings()<=128);assertTrue(store.queued()<=1);assertTrue(store.failed()>0);
        }finally{gate.countDown();store.close();}assertEquals(0,store.activeRecordings());
    }
}
