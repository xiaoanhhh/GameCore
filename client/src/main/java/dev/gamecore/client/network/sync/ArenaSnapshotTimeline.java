package dev.gamecore.client.network.sync;

import java.util.TreeMap;

/** v2 连续坐标展示时间轴，最多64份只读样本。仅插值、不外推；离线/暂停/换局由页面清空。
 * 使用规则Tick及真实接收时刻，缓冲一个快照间隔（至多100ms），不把渲染帧当模拟步。 */
public final class ArenaSnapshotTimeline<S> {
    public static final int CAPACITY=64;
    public record Frame<S>(S from,S to,double alpha) { }
    private record Sample<S>(S value,long arrival) { }
    private final TreeMap<Long,Sample<S>> samples=new TreeMap<>();
    private double cursor=Double.NaN, interval=2;
    public void offer(long tick,S value,long arrival){
        if(!samples.isEmpty()){
            if(tick<=samples.lastKey()){return;}
            interval=Math.max(1,Math.min(6,tick-samples.lastKey()));
        }
        samples.put(tick,new Sample<>(value,arrival));
        while(samples.size()>CAPACITY){samples.pollFirstEntry();}
    }
    public Frame<S> frame(long now){
        if(samples.isEmpty()){return null;}
        var latest=samples.lastEntry();
        double target=latest.getKey()-interval+Math.max(0,now-latest.getValue().arrival())*60d/1_000_000_000;
        target=Math.max(samples.firstKey(),Math.min(latest.getKey(),target));
        cursor=Double.isNaN(cursor)?target:Math.max(cursor,target);
        var a=samples.floorEntry((long)Math.floor(cursor));var b=samples.ceilingEntry((long)Math.ceil(cursor));
        if(a==null){a=samples.firstEntry();}if(b==null){b=latest;}
        return new Frame<>(a.getValue().value(),b.getValue().value(),a.getKey().equals(b.getKey())?1:(cursor-a.getKey())/(b.getKey()-a.getKey()));
    }
    public int size(){return samples.size();}
    public void clear(){samples.clear();cursor=Double.NaN;interval=2;}
}
