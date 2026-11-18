package dev.gamecore.server.network;

import dev.gamecore.protocol.TankProtocol;
import dev.gamecore.server.core.RoomFrame;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 房间→出站IO：最新快照1份、可靠事件/控制128项、最多一批9条在写；仅一个合并唤醒任务。
 * 网络变慢时合并显示帧，事件满则断开，绝不阻塞Tick或无限堆积EventLoop Runnable。
 */
public final class OutboundMailbox {
    private record EventItem(String matchId, RoomFrame.Event event) { }
    private record ResultItem(RoomFrame frame) { }
    public static final int CAPACITY = 128;
    private final Channel channel;
    private final Runnable fatal;
    private final ArrayBlockingQueue<Object> reliable = new ArrayBlockingQueue<>(CAPACITY);
    private final AtomicReference<RoomFrame> latest = new AtomicReference<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private volatile boolean closeAfter;
    private boolean busy;
    public OutboundMailbox(Channel channel, Runnable fatal) { this.channel = channel; this.fatal = fatal; }
    private boolean offer(Object message) {
        if (!reliable.offer(message)) { fatal.run(); return false; } return true;
    }
    public void control(TankProtocol.Envelope message) { if (offer(message)) { signal(); } }
    public void frame(RoomFrame frame) {
        for (var event : frame.events()) { if (!offer(new EventItem(frame.matchId(), event))) { return; } }
        latest.set(frame); signal();
    }
    public void result(RoomFrame frame) { if (offer(new ResultItem(frame))) { signal(); } }
    public void closeAfterFlush() {
        closeAfter = true; signal();
        try { channel.eventLoop().schedule(() -> { channel.close(); }, 1, TimeUnit.SECONDS); }
        catch (RuntimeException ignored) { channel.close(); }
    }
    public void signal() {
        if (!scheduled.compareAndSet(false, true)) { return; }
        try { channel.eventLoop().execute(() -> { scheduled.set(false); drain(); }); }
        catch (RuntimeException stopped) { scheduled.set(false); fatal.run(); }
    }
    private void drain() {
        if (!channel.isActive()) { clear(); return; }
        if (busy || !channel.isWritable()) { return; }
        List<TankProtocol.Envelope> batch = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            Object item = reliable.poll(); if (item == null) { break; }
            if (item instanceof TankProtocol.Envelope envelope) { batch.add(envelope); }
            else if (item instanceof EventItem event) { batch.add(Wire.event(event.matchId(), event.event())); }
            else if (item instanceof ResultItem result) { batch.add(Wire.result(result.frame())); }
        }
        var frame = latest.getAndSet(null);
        if (frame != null) { batch.add(Wire.envelope(0, builder -> builder.setWorldSnapshot(Wire.snapshot(frame)))); }
        if (batch.isEmpty()) { if (closeAfter) { channel.close(); } return; }
        busy = true; ChannelFuture last = null;
        for (var message : batch) {
            last = channel.write(message);
            // 任意一条可靠消息编码/写入失败都关闭连接，不能只检查最后一条的Future。
            last.addListener(future -> { if (!future.isSuccess()) { fatal.run(); } });
        }
        channel.flush();
        java.util.Objects.requireNonNull(last).addListener(future -> {
            busy = false;
            if (!future.isSuccess()) { fatal.run(); }
            else if (!reliable.isEmpty() || latest.get() != null || closeAfter) { signal(); }
        });
    }
    public int queuedReliable() { return reliable.size(); }
    public boolean hasLatest() { return latest.get() != null; }
    public void clear() { reliable.clear(); latest.set(null); }
}
