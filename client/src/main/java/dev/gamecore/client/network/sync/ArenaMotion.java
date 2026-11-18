package dev.gamecore.client.network.sync;

import dev.gamecore.fight.FightSnapshot;
import dev.gamecore.tank.TankSnapshot;
import dev.gamecore.tank.Vec2;
import java.util.ArrayList;

/** 只替换客户端连续坐标；生命、技能、命中ID、碰撞地图和结果始终取最新权威快照。
 * 格斗瞬移/束缚/硬直/顿帧直接显示权威值，避免把真实闪袭画成横穿场景的滑行。 */
public final class ArenaMotion {
    private ArenaMotion(){ }
    private static double lerp(double a,double b,double t){return a+(b-a)*Math.max(0,Math.min(1,t));}
    private static Vec2 lerp(Vec2 a,Vec2 b,double t){return new Vec2(lerp(a.x(),b.x(),t),lerp(a.y(),b.y(),t));}
    public static TankSnapshot tank(TankSnapshot authority,ArenaSnapshotTimeline.Frame<TankSnapshot> frame){
        if(frame==null){return authority;}var a=frame.from();var b=frame.to();double t=frame.alpha();
        if(!a.map().id().equals(authority.map().id())||!b.map().id().equals(authority.map().id())){return authority;}
        var tanks=new ArrayList<TankSnapshot.Tank>();
        for(var meta:authority.tanks()){
            var one=a.tanks().stream().filter(v->v.id()==meta.id()).findFirst().orElse(meta);
            var two=b.tanks().stream().filter(v->v.id()==meta.id()).findFirst().orElse(meta);
            tanks.add(new TankSnapshot.Tank(meta.id(),lerp(one.position(),two.position(),t),
                    dev.gamecore.tank.TankGame.normalize(one.heading()+ClientPrediction.shortAngle(two.heading()-one.heading())*t),meta.velocity(),meta.health(),meta.cooldown()));
        }
        var bullets=new ArrayList<TankSnapshot.Bullet>();
        // 以最新存活ID为集合：不能复活已撞墙/命中的子弹，新增子弹直接显示真实出生位置。
        for(var meta:authority.bullets()){
            var one=a.bullets().stream().filter(v->v.id()==meta.id()).findFirst().orElse(null);
            var two=b.bullets().stream().filter(v->v.id()==meta.id()).findFirst().orElse(null);
            bullets.add(one==null||two==null?meta:new TankSnapshot.Bullet(meta.id(),meta.owner(),lerp(one.position(),two.position(),t),meta.velocity(),meta.remainingTicks()));
        }
        return new TankSnapshot(authority.map(),authority.tick(),authority.state(),authority.result(),tanks,bullets,authority.events());
    }
    public static FightSnapshot fight(FightSnapshot authority,ArenaSnapshotTimeline.Frame<FightSnapshot> frame){
        if(frame==null||authority.hitstopTicks()>0||!authority.teleports().isEmpty()){return authority;}
        var a=frame.from();var b=frame.to();double t=frame.alpha();
        if(a.map()!=authority.map()||b.map()!=authority.map()||!a.teleports().isEmpty()||!b.teleports().isEmpty()){return authority;}
        var fighters=new ArrayList<FightSnapshot.Fighter>();
        for(var meta:authority.fighters()){
            var one=a.fighters().get(meta.player());var two=b.fighters().get(meta.player());
            boolean snap=one.kind()!=meta.kind()||two.kind()!=meta.kind()||meta.stunTicks()>0||meta.boundBy()!=0
                    ||meta.action()==FightSnapshot.Action.TELEPORT||Math.abs(two.x()-one.x())>160||Math.abs(two.y()-one.y())>160;
            fighters.add(snap?meta:new FightSnapshot.Fighter(meta.player(),meta.kind(),lerp(one.x(),two.x(),t),lerp(one.y(),two.y(),t),
                    meta.facing(),meta.health(),meta.energy(),meta.action(),meta.actionTicks(),meta.stunTicks(),meta.slowTicks(),meta.vx(),meta.vy(),
                    meta.grounded(),meta.jumpCount(),meta.platform(),meta.travel(),meta.poseTick(),meta.comboCount(),meta.maxCombo(),meta.totalDamage(),meta.attacks(),meta.hits(),meta.teleportCooldown(),meta.boundBy()));
        }
        // 格斗弹道没有稳定ID，保持权威值；不能按列表位置把两发不同技能连起来。
        return new FightSnapshot(authority.tick(),authority.state(),authority.result(),fighters,authority.projectiles(),authority.impacts(),authority.hitstopTicks(),authority.map(),authority.ultimates(),authority.teleports());
    }
}
