package dev.gamecore.server.multigame;
import dev.gamecore.online.*;
import dev.gamecore.online.tank.TankFactory;
import dev.gamecore.online.snake.SnakeFactory;
import dev.gamecore.protocol.GameProtocol;
import dev.gamecore.tank.TankCommand;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class RoomActorTest {
    static final class Peer implements RoomPeer {
        final String id=UUID.randomUUID().toString();final AtomicInteger pending=new AtomicInteger();final List<GameProtocol.Envelope> messages=new ArrayList<>();boolean active=true;
        public String id(){return id;}public boolean active(){return active;}public AtomicInteger pending(){return pending;}public void reliable(GameProtocol.Envelope e){messages.add(e);}public void snapshot(GameProtocol.Envelope e){messages.add(e);}
    }
    static RoomActor room(){var f=new TankFactory();return new RoomActor("tank-1",f,f.defaults(),42,true,new RoomActor.Observer(){});}
    static void join(RoomActor r,Peer... peers){for(var p:peers){assertTrue(r.join(p,1));}r.runDue(System.nanoTime());}
    static void step(RoomActor r){var future=r.debug(true,true);r.runDue(System.nanoTime());future.join();}
    static GameProtocol.Input input(RoomActor r,long sequence,long tick,byte[] command){return GameProtocol.Input.newBuilder().setRoomId(r.id()).setMatchId(r.latest().getMatchId()).setGameId(r.factory().type().id()).setSequence(sequence).setTargetTick(tick).setCommand(ByteString.copyFrom(command)).build();}
    @Test void ackOnlyAfterExecutionAndDuplicatesDoNotRepeat(){try(var r=room()){var a=new Peer();var b=new Peer();join(r,a,b);var before=new TankFactory().snapshots().decode(r.latest().getState().toByteArray());
        var command=new TankFactory().inputs().encode(new TankCommand(1,0,true));var message=input(r,1,2,command);assertTrue(r.input(a,message));assertTrue(r.input(a,message));step(r);
        assertEquals(1,r.latest().getAcks(0).getReceived());assertEquals(0,r.latest().getAcks(0).getExecuted());step(r);assertEquals(1,r.latest().getAcks(0).getExecuted());
        var after=new TankFactory().snapshots().decode(r.latest().getState().toByteArray());assertNotEquals(before.tanks().getFirst().position(),after.tanks().getFirst().position());assertEquals(1,after.bullets().size());assertEquals(0,a.pending.get());assertTrue(a.messages.stream().anyMatch(e->e.hasError()&&e.getError().getCode().equals("DUPLICATE_INPUT")));}}
    @Test void lateFutureAndCrossGameAreRejected(){try(var r=room()){var a=new Peer();join(r,a,new Peer());byte[] value=new TankFactory().inputs().encode(TankCommand.IDLE);
        r.input(a,input(r,1,0,value));r.input(a,input(r,2,100,value));r.input(a,input(r,3,1,value).toBuilder().setGameId("snake").build());step(r);
        assertEquals(0,r.latest().getAcks(0).getExecuted());assertEquals(0,a.pending.get());assertEquals(3,a.messages.stream().filter(GameProtocol.Envelope::hasError).count());}}
    @Test void inputQueueAndHistoryHaveHardLimits(){try(var r=room()){var a=new Peer();join(r,a,new Peer());byte[] value=new TankFactory().inputs().encode(TankCommand.IDLE);int accepted=0;
        for(int i=1;i<=1000;i++){if(r.input(a,input(r,i,1,value))){accepted++;}}assertEquals(32,accepted);assertEquals(32,a.pending.get());step(r);assertEquals(0,a.pending.get());assertEquals(32,r.latest().getAcks(0).getExecuted());}}
    @Test void expiredLeaseActuallyReleasesPersistentTankCommand(){try(var r=room()){var a=new Peer();join(r,a,new Peer());var f=new TankFactory();r.input(a,input(r,1,1,f.inputs().encode(new TankCommand(1,0,false))));for(int i=0;i<17;i++){step(r);}var stopped=f.snapshots().decode(r.latest().getState().toByteArray()).tanks().getFirst().position();for(int i=0;i<5;i++){step(r);}assertEquals(stopped,f.snapshots().decode(r.latest().getState().toByteArray()).tanks().getFirst().position());}}
    @Test void disconnectAbortsAndReleasesPending(){try(var r=room()){var a=new Peer();var b=new Peer();join(r,a,b);r.input(a,input(r,1,10,new TankFactory().inputs().encode(TankCommand.IDLE)));r.runDue(System.nanoTime());a.active=false;r.runDue(System.nanoTime());
        assertEquals("ABORTED",r.metrics().status());assertEquals(0,a.pending.get());assertEquals(1,r.metrics().players());b.active=false;r.runDue(System.nanoTime());assertEquals("WAITING",r.metrics().status());assertNull(r.latest());}}
    @Test void destructionRacingProducersCannotRefillClosedMailbox() throws Exception{var r=room();var a=new Peer();join(r,a,new Peer());var value=input(r,1,10,new TankFactory().inputs().encode(TankCommand.IDLE));var start=new java.util.concurrent.CountDownLatch(1);var t=new Thread(()->{try{start.await();for(int i=0;i<10000;i++){r.input(a,value);}}catch(InterruptedException e){Thread.currentThread().interrupt();}});t.start();start.countDown();r.close();t.join();assertFalse(r.input(a,value));assertFalse(r.join(new Peer(),1));assertTrue(r.debug(true,true).isCompletedExceptionally());assertEquals(0,a.pending.get());assertEquals("CLOSED",r.metrics().status());}
    @Test void localDebugIsExplicitAndSingleWriter() throws Exception{var f=new SnakeFactory();try(var r=new RoomActor("snake-1",f,f.defaults(),42,false,new RoomActor.Observer(){})){assertTrue(r.debug(true,true).isCompletedExceptionally());r.runDue(System.nanoTime());var error=new java.util.concurrent.atomic.AtomicReference<Throwable>();var t=new Thread(()->{try{r.runDue(System.nanoTime());}catch(Throwable e){error.set(e);}});t.start();t.join();assertInstanceOf(IllegalStateException.class,error.get());}}
}
