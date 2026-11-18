package dev.gamecore.server.multigame;

import dev.gamecore.online.fight.FightFactory;
import dev.gamecore.online.snake.SnakeFactory;
import dev.gamecore.online.runtime.GameStep;
import dev.gamecore.protocol.GameProtocol;
import dev.gamecore.fight.FightCommand;
import dev.gamecore.fight.FightMap;
import dev.gamecore.fight.FighterKind;
import dev.gamecore.api.session.SessionState;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 不使用图形或客户端伪造 World；验证 Room 准备、控制世代与两人同步的真实规则状态。 */
class InteractiveRoomTest {
    static final class Peer implements RoomPeer {
        final String id=UUID.randomUUID().toString();final AtomicInteger pending=new AtomicInteger();
        final List<GameProtocol.Envelope> messages=new ArrayList<>();boolean active=true;long sequence;
        @Override public String id(){return id;}@Override public boolean active(){return active;}
        @Override public AtomicInteger pending(){return pending;}@Override public boolean interactive(){return true;}
        @Override public void reliable(GameProtocol.Envelope e){messages.add(e);}@Override public void snapshot(GameProtocol.Envelope e){ }
        String error(){return messages.stream().filter(GameProtocol.Envelope::hasError).map(e->e.getError().getCode()).reduce((a,b)->b).orElse("");}
    }
    static void pump(RoomActor room){room.runDue(System.nanoTime());}
    static void command(RoomActor room,Peer p,GameProtocol.RoomAction action,Map<String,String> choices){
        var b=GameProtocol.RoomControl.newBuilder().setRoomId(room.id()).setMatchId(room.latest().getMatchId()).setSequence(++p.sequence).setRevision(room.roomState().getRevision()).setAction(action);
        choices.forEach((k,v)->b.addChoices(GameProtocol.Option.newBuilder().setKey(k).setValue(v)));assertTrue(room.control(p,b.build()));pump(room);
    }
    static void action(RoomActor room,Peer p,GameProtocol.RoomAction action){command(room,p,action,Map.of());}
    static void join(RoomActor room,Peer a,Peer b){assertTrue(room.join(a,1));assertTrue(room.join(b,2));pump(room);assertEquals("SETUP",room.metrics().status());}
    static void start(RoomActor room,Peer a,Peer b){action(room,a,GameProtocol.RoomAction.READY);action(room,b,GameProtocol.RoomAction.READY);}
    @Test void ownFighterAndHostMapCommitBeforeBothReadyAndOpeningDoesNotRunRules(){
        var f=new FightFactory();for(var map:FightMap.values()){try(var room=new RoomActor("fight-1",f,f.defaults(),42,false,new RoomActor.Observer(){})){
            var a=new Peer();var b=new Peer();join(room,a,b);
            command(room,a,GameProtocol.RoomAction.SELECT,Map.of("one","FEIDUDU","map",map.name()));
            var snapshot=f.snapshots().decode(room.latest().getState().toByteArray());assertEquals(map,snapshot.map());assertEquals(map.platforms(),snapshot.map().platforms());
            action(room,a,GameProtocol.RoomAction.READY);assertEquals("SETUP",room.metrics().status());action(room,b,GameProtocol.RoomAction.READY);
            assertEquals("OPENING",room.metrics().status());assertEquals(0,room.metrics().coreTick());assertEquals(SessionState.READY,f.snapshots().decode(room.latest().getState().toByteArray()).state());
        }}
    }
    @Test void selectionOwnershipReadinessPauseEpochAndSynchronizedRestart(){
        var f=new FightFactory();var ended=new ArrayList<String>();
        try(var room=new RoomActor("fight-1",f,f.defaults(),42,false,new RoomActor.Observer(){@Override public void ended(String reason,GameStep last){ended.add(reason);}})){
            var a=new Peer();var b=new Peer();join(room,a,b);String first=room.latest().getMatchId();
            var map=FightMap.values()[FightMap.values().length-1];
            command(room,a,GameProtocol.RoomAction.SELECT,Map.of("one","FEIDUDU","map",map.name()));
            command(room,b,GameProtocol.RoomAction.SELECT,Map.of("two","NAIWA"));
            command(room,b,GameProtocol.RoomAction.SELECT,Map.of("one","NAIWA"));assertEquals("CONTROL_REJECTED",b.error());
            command(room,b,GameProtocol.RoomAction.SELECT,Map.of("map","NEON"));assertEquals(map.name(),room.config().options().get("map"));
            var view=f.snapshots().decode(room.latest().getState().toByteArray());assertEquals(FighterKind.FEIDUDU,view.fighters().get(0).kind());assertEquals(FighterKind.NAIWA,view.fighters().get(1).kind());assertEquals(map,view.map());assertEquals(SessionState.READY,view.state());
            start(room,a,b);assertEquals("OPENING",room.metrics().status());assertEquals(0,room.metrics().coreTick());
            action(room,a,GameProtocol.RoomAction.PAUSE);room.runDue(System.nanoTime()+20_000_000_000L);assertEquals(0,room.metrics().coreTick());
            action(room,a,GameProtocol.RoomAction.RESUME);assertTrue(room.roomState().getPaused());action(room,b,GameProtocol.RoomAction.RESUME);
            action(room,a,GameProtocol.RoomAction.SKIP_OPENING);assertEquals("OPENING",room.metrics().status());action(room,b,GameProtocol.RoomAction.SKIP_OPENING);assertEquals("RUNNING",room.metrics().status());
            long epoch=room.roomState().getInputEpoch();action(room,a,GameProtocol.RoomAction.PAUSE);long tick=room.metrics().tick();room.runDue(System.nanoTime()+1_000_000_000L);assertEquals(tick,room.metrics().tick());assertEquals(SessionState.PAUSED,f.snapshots().decode(room.latest().getState().toByteArray()).state());
            action(room,a,GameProtocol.RoomAction.RESUME);action(room,b,GameProtocol.RoomAction.RESUME);
            var stale=GameProtocol.Input.newBuilder().setRoomId(room.id()).setMatchId(first).setGameId("fight").setSequence(1).setTargetTick(tick+2).setInputEpoch(epoch).setCommand(com.google.protobuf.ByteString.copyFrom(f.inputs().encode(FightCommand.NONE))).build();
            assertTrue(room.input(a,stale));pump(room);assertEquals("STALE_INPUT_EPOCH",a.error());assertEquals(0,a.pending.get());
            action(room,a,GameProtocol.RoomAction.RESTART);assertEquals(first,room.latest().getMatchId());assertEquals("PAUSED",room.metrics().status());
            action(room,a,GameProtocol.RoomAction.CANCEL_RESTART);assertFalse(room.roomState().getSeatsList().stream().anyMatch(GameProtocol.RoomSeat::getRestart));
            action(room,a,GameProtocol.RoomAction.RESTART);action(room,b,GameProtocol.RoomAction.RESTART);
            assertNotEquals(first,room.latest().getMatchId());assertEquals("SETUP",room.metrics().status());assertEquals(0,room.metrics().tick());assertEquals(map.name(),room.config().options().get("map"));assertEquals(List.of("RESTARTED"),ended);
            assertEquals(2,room.metrics().players());assertTrue(room.roomState().getSeatsList().stream().noneMatch(GameProtocol.RoomSeat::getReady));
        }
    }
    @Test void mapChangeInvalidatesBothReadyAndOldRevisionCannotStart(){
        var f=new SnakeFactory();try(var room=new RoomActor("snake-1",f,f.defaults(),42,false,new RoomActor.Observer(){})){
            var a=new Peer();var b=new Peer();join(room,a,b);action(room,a,GameProtocol.RoomAction.READY);long revision=room.roomState().getRevision();
            command(room,a,GameProtocol.RoomAction.SELECT,Map.of("map","obstacles"));assertTrue(room.roomState().getSeatsList().stream().noneMatch(GameProtocol.RoomSeat::getReady));
            var stale=GameProtocol.RoomControl.newBuilder().setRoomId(room.id()).setMatchId(room.latest().getMatchId()).setSequence(++b.sequence).setRevision(revision).setAction(GameProtocol.RoomAction.READY).build();
            assertTrue(room.control(b,stale));pump(room);assertEquals("CONTROL_REJECTED",b.error());assertEquals("SETUP",room.metrics().status());
            assertFalse(f.snapshots().decode(room.latest().getState().toByteArray()).obstacles().isEmpty());start(room,a,b);assertEquals("RUNNING",room.metrics().status());
        }
    }
    @Test void pendingAllocationDoesNotBlockRoomAndCloseCancelsIt(){
        var future=new CompletableFuture<String>();var f=new FightFactory();
        var room=new RoomActor("fight-1",f,f.defaults(),42,false,new RoomActor.Observer(){@Override public CompletableFuture<String> nextMatch(String previous){return future;}});
        var a=new Peer();var b=new Peer();join(room,a,b);start(room,a,b);action(room,a,GameProtocol.RoomAction.RESTART);action(room,b,GameProtocol.RoomAction.RESTART);
        assertEquals("RESETTING",room.metrics().status());pump(room);room.close();assertTrue(future.isCancelled());assertEquals(0,room.metrics().queued());
    }
}
