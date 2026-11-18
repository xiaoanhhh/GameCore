package dev.gamecore.online.tank;
import com.google.protobuf.ByteString;
import dev.gamecore.api.online.GameSnapshotCodec;
import dev.gamecore.protocol.GameProtocol;
import dev.gamecore.online.ProtocolValues;
import dev.gamecore.tank.*;
import java.util.*;
/** 完整不可变Tank快照；地图位图及列表顺序固定，槽位/World引用不跨网络。 */
public final class TankSnapshotCodec implements GameSnapshotCodec<TankSnapshot> {
    private static GameProtocol.Point point(Vec2 p) { return GameProtocol.Point.newBuilder().setX(p.x()).setY(p.y()).build(); }
    private static Vec2 point(GameProtocol.Point p) { ProtocolValues.known(p);if(Math.abs(p.getX())>1024||Math.abs(p.getY())>1024){throw new IllegalArgumentException("Coordinate range");}return new Vec2(p.getX(),p.getY()); }
    @Override public byte[] encode(TankSnapshot s) {
        var map = GameProtocol.TankMap.newBuilder().setId(s.map().id()).setWidth(s.map().width()).setHeight(s.map().height());
        byte[] walls = new byte[s.map().width()*s.map().height()];
        for (int y=0;y<s.map().height();y++) { for(int x=0;x<s.map().width();x++) { walls[y*s.map().width()+x]=(byte)(s.map().wall(x,y)?1:0); } }
        map.setWalls(ByteString.copyFrom(walls));
        for(var spawn:s.map().spawns()) { map.addSpawns(GameProtocol.TankSpawn.newBuilder().setPosition(point(spawn.position())).setHeading(spawn.heading())); }
        for(var z:s.map().repairZones()) { map.addRepairs(GameProtocol.RepairZone.newBuilder().setX(z.x()).setY(z.y()).setWidth(z.width()).setHeight(z.height())); }
        var v=GameProtocol.TankView.newBuilder().setMap(map).setTick(s.tick()).setState(s.state().name()).setResult(s.result().name());
        for(var t:s.tanks()) { v.addPlayers(GameProtocol.TankPlayer.newBuilder().setSlot(t.id()).setPosition(point(t.position())).setHeading(t.heading()).setVelocity(point(t.velocity())).setHealth(t.health()).setCooldown(t.cooldown())); }
        for(var t:s.bullets()) { v.addBullets(GameProtocol.TankBullet.newBuilder().setId(t.id()).setOwner(t.owner()).setPosition(point(t.position())).setVelocity(point(t.velocity())).setLife(t.remainingTicks())); }
        for(var t:s.events()) { v.addEvents(GameProtocol.TankEvent.newBuilder().setType(t.type().name()).setActor(t.actor()).setTarget(t.target()).setPosition(point(t.position()))); }
        return ProtocolValues.bytes(GameProtocol.GameSnapshot.newBuilder().setTank(v).build());
    }
    @Override public TankSnapshot decode(byte[] bytes) {
        try {
            var all=GameProtocol.GameSnapshot.parseFrom(bytes); ProtocolValues.known(all); if(!all.hasTank()) { throw new IllegalArgumentException("Wrong snapshot game"); }
            var v=all.getTank(); var m=v.getMap();ProtocolValues.known(v);ProtocolValues.known(m);int w=m.getWidth(), h=m.getHeight();
            if(w<4||h<4||w>128||h>128||m.getWalls().size()!=w*h||v.getPlayersCount()!=2||v.getBulletsCount()>128) { throw new IllegalArgumentException("Invalid Tank snapshot"); }
            boolean[] walls=new boolean[w*h]; for(int i=0;i<walls.length;i++){walls[i]=m.getWalls().byteAt(i)!=0;}
            TankMap map=new TankMap(m.getId(),w,h,walls,m.getSpawnsList().stream().map(t->new TankMap.Spawn(point(t.getPosition()),t.getHeading())).toList(),
                m.getRepairsList().stream().map(z->new TankMap.RepairZone(z.getX(),z.getY(),z.getWidth(),z.getHeight())).toList());
            if(v.getTick()<0||v.getPlayers(0).getSlot()!=0||v.getPlayers(1).getSlot()!=1||v.getEventsCount()>128){throw new IllegalArgumentException("Tank identity/order");}
            var tanks=v.getPlayersList().stream().map(t->{ProtocolValues.known(t);ProtocolValues.finite(t.getHeading()); if(t.getSlot()<0||t.getSlot()>1||t.getHealth()<0||t.getHealth()>TankGame.MAX_HEALTH||t.getCooldown()<0||t.getCooldown()>TankGame.FIRE_COOLDOWN){throw new IllegalArgumentException("Invalid tank");}
                return new TankSnapshot.Tank(t.getSlot(),point(t.getPosition()),t.getHeading(),point(t.getVelocity()),t.getHealth(),t.getCooldown()); }).toList();
            var ids=new HashSet<Long>();var bullets=v.getBulletsList().stream().map(t->{ProtocolValues.known(t);if(t.getId()<0||!ids.add(t.getId())||t.getOwner()<0||t.getOwner()>1||t.getLife()<0||t.getLife()>TankGame.BULLET_LIFETIME){throw new IllegalArgumentException("Bullet bounds");}return new TankSnapshot.Bullet(t.getId(),t.getOwner(),point(t.getPosition()),point(t.getVelocity()),t.getLife());}).toList();
            var events=v.getEventsList().stream().map(t->new TankSnapshot.Event(TankSnapshot.EventType.valueOf(t.getType()),t.getActor(),t.getTarget(),point(t.getPosition()))).toList();
            return new TankSnapshot(map,v.getTick(),TankGame.State.valueOf(v.getState()),TankGame.Result.valueOf(v.getResult()),tanks,bullets,events);
        }catch(com.google.protobuf.InvalidProtocolBufferException e){throw new IllegalArgumentException("Malformed snapshot",e);}
    }
    @Override public String outcome(TankSnapshot s){return s.result().name();}
    @Override public int entityCount(TankSnapshot s){return s.tanks().size()+s.bullets().size();}
}
