package dev.gamecore.client.network;

/** v2 输入时钟：截止时间沿固定节拍推进，低帧率只采样一次，绝不补发积压操作。
 * 目标覆盖快照下行、快照年龄、输入上行和一步余量；不同采样占不同 Tick，防技能脉冲被覆盖。 */
final class ArenaInputSchedule {
    private long nextDue, lastTarget;
    private int extraMargin;
    long target(long now, int tps, long tick, long arrival, long rtt, boolean force) {
        long period=1_000_000_000L/tps;
        if(!force&&now<nextDue){return 0;}
        long age=Math.min(1_000_000_000L,Math.max(0,now-arrival));
        long transit=rtt>0?Math.min(rtt,2_000_000_000L):100_000_000L;
        long window=Math.max(2,tps/2);
        long lead=Math.min(window,Math.max(1,(transit+age+marginTicks()*period+period-1)/period));
        long target=Math.max(lastTarget+1,tick+lead);
        // 窗口满时不消费键盘锁存，也不放宽服务端未来窗口。
        if(target>tick+window){return 0;}
        if(nextDue==0||force){nextDue=now+period;}
        else{nextDue+=((now-nextDue)/period+1)*period;}
        lastTarget=target;return target;
    }
    /** 真正迟到才增加排队余量，最多四步；不重发旧技能、不扩大服务端窗口。 */
    void lateInput(){extraMargin=Math.min(3,extraMargin+1);}
    int marginTicks(){return 1+extraMargin;}
    void reset(){nextDue=lastTarget=0;extraMargin=0;}
}
