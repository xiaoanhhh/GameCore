package dev.gamecore.client.traffic;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * 有界交互记录。EventLoop、HTTP 工作线程写入，渲染线程读取；同步块内只有有界内存操作。
 * 满额丢弃最旧一行。快照和输入这类高频消息进入合并窗口，每秒写成一行，内存不随消息数量增长。
 * 关闭状态下写入直接返回，关闭时清空全部内容。
 */
public final class TrafficLog {
    public static final int CAPACITY = 512;
    public static final int TEXT_LIMIT = 96;
    public static final long WINDOW_MILLIS = 1000;
    private static final class Burst {
        final long opened; final TrafficDirection direction; final String unit;
        final long first; long last, count; int bytes;
        Burst(long opened, TrafficDirection direction, String unit, long value, int bytes) {
            this.opened = opened; this.direction = direction; this.unit = unit;
            first = last = value; count = 1; this.bytes = bytes;
        }
    }
    private final ArrayDeque<TrafficEntry> entries = new ArrayDeque<>(CAPACITY);
    private final Map<TrafficKind, Burst> bursts = new EnumMap<>(TrafficKind.class);
    private final LongSupplier clock;
    private final ZoneId zone;
    private volatile boolean enabled;
    private long version;

    public TrafficLog() { this(System::currentTimeMillis, ZoneId.systemDefault()); }
    TrafficLog(LongSupplier clock, ZoneId zone) { this.clock = clock; this.zone = zone; }

    public synchronized void enable() { enabled = true; }
    public synchronized void disable() { enabled = false; reset(); }
    public boolean enabled() { return enabled; }
    public synchronized void clear() { reset(); }
    private void reset() { entries.clear(); bursts.clear(); version++; }

    /** 逐条记录。 */
    public void add(TrafficKind kind, TrafficDirection direction, String text) {
        if (!enabled) { return; }
        synchronized (this) {
            if (!enabled) { return; }
            long now = clock.getAsLong();
            expire(now);
            append(now, kind, direction, text);
        }
    }

    /** 进入 kind 的合并窗口；value 是序号或 Tick，用于显示窗口内的首末值。 */
    public void burst(TrafficKind kind, TrafficDirection direction, String unit, long value, int bytes) {
        if (!enabled) { return; }
        synchronized (this) {
            if (!enabled) { return; }
            long now = clock.getAsLong();
            expire(now);
            Burst open = bursts.get(kind);
            if (open == null) { bursts.put(kind, new Burst(now, direction, unit, value, bytes)); return; }
            open.count++; open.last = value; open.bytes = bytes;
        }
    }

    /** 渲染线程每帧调用：消息停止后，未满的窗口也要按时落成一行。 */
    public void flush() {
        if (!enabled) { return; }
        synchronized (this) { if (enabled) { expire(clock.getAsLong()); } }
    }

    private void expire(long now) {
        for (var iterator = bursts.entrySet().iterator(); iterator.hasNext(); ) {
            var item = iterator.next();
            Burst burst = item.getValue();
            if (now - burst.opened < WINDOW_MILLIS) { continue; }
            // EnumMap 的条目在移除后失效，必须先取出 key。
            TrafficKind kind = item.getKey();
            iterator.remove();
            append(now, kind, burst.direction, "×" + burst.count + " · " + burst.unit + " " + burst.first + "→" + burst.last + " · " + burst.bytes + " B");
        }
    }

    private void append(long now, TrafficKind kind, TrafficDirection direction, String text) {
        if (entries.size() == CAPACITY) { entries.removeFirst(); }
        entries.addLast(new TrafficEntry(clock(now), kind, direction, clean(text)));
        version++;
    }

    /** 服务器字符串不可信：去掉控制字符并限制长度，避免破坏行布局。 */
    static String clean(String text) {
        StringBuilder result = new StringBuilder(Math.min(text.length(), TEXT_LIMIT));
        text.codePoints().limit(TEXT_LIMIT).forEach(c -> result.appendCodePoint(Character.isISOControl(c) ? ' ' : c));
        return result.toString();
    }

    private String clock(long millis) {
        LocalTime time = LocalTime.ofInstant(Instant.ofEpochMilli(millis), zone);
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d.%03d", time.getHour(), time.getMinute(), time.getSecond(), time.getNano() / 1_000_000);
    }

    /** 内容变化时递增，渲染线程据此避免每帧复制。 */
    public synchronized long version() { return version; }
    public synchronized List<TrafficEntry> entries() { return List.copyOf(entries); }
}
