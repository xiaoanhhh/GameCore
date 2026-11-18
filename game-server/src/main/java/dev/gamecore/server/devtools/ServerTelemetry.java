package dev.gamecore.server.devtools;
import dev.gamecore.server.multigame.*;
import java.lang.management.*;
import java.util.*;
/** 控制线程采样；Tick只累计标量/有界耗时窗口。不把GC collection时间冒称精确STW停顿。 */
public final class ServerTelemetry {
    private final NettyMultiGameServer server;private final long started=System.nanoTime();private long lastNanos=started,lastCpu,lastAllocated;
    private final com.sun.management.OperatingSystemMXBean os=(com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
    private final com.sun.management.ThreadMXBean threads=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private final List<Map<String,Object>> samples=new ArrayList<>();private final Map<Long,Long> allocations=new HashMap<>();
    public ServerTelemetry(NettyMultiGameServer server){this.server=server;lastCpu=os.getProcessCpuTime();if(threads.isThreadAllocatedMemorySupported()&&!threads.isThreadAllocatedMemoryEnabled()){threads.setThreadAllocatedMemoryEnabled(true);}allocation();}
    private long allocation(){long delta=0;if(!threads.isThreadAllocatedMemorySupported()){return -1;}var ids=threads.getAllThreadIds();var values=threads.getThreadAllocatedBytes(ids);var live=new HashSet<Long>();for(int i=0;i<ids.length;i++){if(values[i]<0){continue;}live.add(ids[i]);Long old=allocations.put(ids[i],values[i]);if(old!=null){delta+=Math.max(0,values[i]-old);}}allocations.keySet().retainAll(live);return delta;}
    public Map<String,Object> sample(){
        long now=System.nanoTime(),cpu=os.getProcessCpuTime(),delta=now-lastNanos;var result=new LinkedHashMap<String,Object>();
        result.put("elapsedSeconds",(now-started)/1e9);result.put("connections",server.connections());result.put("wireBytesOut",server.bytesOut());result.put("wireBytesIn",server.bytesIn());
        result.put("cpuCoresUsed",delta==0?0:(double)(cpu-lastCpu)/delta);result.put("heapUsedBytes",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());result.put("heapMaxBytes",Runtime.getRuntime().maxMemory());result.put("threads",threads.getThreadCount());
        result.put("nonHeapUsedBytes",ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed());
        result.put("metaspaceUsedBytes",ManagementFactory.getMemoryPoolMXBeans().stream().filter(p->p.getName().equals("Metaspace")).mapToLong(p->p.getUsage().getUsed()).sum());
        result.put("directBufferUsedBytes",ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class).stream().filter(p->p.getName().equals("direct")).mapToLong(BufferPoolMXBean::getMemoryUsed).sum());
        long allocated=allocation();result.put("sampledLiveThreadAllocationBytesPerSecond",allocated<0?null:allocated*1e9/Math.max(1,delta));
        var gc=ManagementFactory.getGarbageCollectorMXBeans();result.put("gcCount",gc.stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).filter(v->v>=0).sum());result.put("gcCollectionMillis",gc.stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).filter(v->v>=0).sum());
        var rooms=server.rooms().rooms().stream().map(RoomActor::metrics).toList();var durations=rooms.stream().flatMap(m->m.recentTickNanos().stream()).sorted().toList();
        result.put("tickSampleCount",durations.size());result.put("tickP50Micros",percentile(durations,.50)/1000.0);result.put("tickP95Micros",percentile(durations,.95)/1000.0);result.put("tickP99Micros",percentile(durations,.99)/1000.0);
        long ticks=rooms.stream().mapToLong(RoomActor.Metrics::ticks).sum(),overruns=rooms.stream().mapToLong(RoomActor.Metrics::overruns).sum();result.put("ticks",ticks);result.put("overrunTicks",overruns);result.put("overrunRatio",ticks==0?0:(double)overruns/ticks);
        result.put("skippedScheduleDeadlines",rooms.stream().mapToLong(RoomActor.Metrics::skippedDeadlines).sum());result.put("inputQueueDepth",rooms.stream().mapToInt(RoomActor.Metrics::queued).sum());result.put("entities",rooms.stream().mapToInt(RoomActor.Metrics::entities).sum());
        result.put("snapshotCount",rooms.stream().mapToLong(RoomActor.Metrics::snapshotCount).sum());result.put("generatedSnapshotBytes",rooms.stream().mapToLong(RoomActor.Metrics::snapshotBytes).sum());result.put("largestCurrentSnapshotBytes",rooms.stream().mapToInt(RoomActor.Metrics::snapshotSize).max().orElse(0));
        var roomValues=new ArrayList<Map<String,Object>>();for(var m:rooms){var r=new LinkedHashMap<String,Object>();r.put("id",m.room());r.put("game",m.game());r.put("status",m.status());r.put("tick",m.tick());r.put("coreTick",m.coreTick());r.put("players",m.players());r.put("entities",m.entities());r.put("queue",m.queued());r.put("hash",m.hash());r.put("simulationNanos",m.simulationNanos());r.put("snapshotProjectionNanos",m.snapshotNanos());roomValues.add(r);}result.put("rooms",roomValues);
        lastCpu=cpu;lastNanos=now;if(samples.size()<3601){samples.add(result);}return result;
    }
    public Map<String,Object> report(){var r=new LinkedHashMap<String,Object>();r.put("java",System.getProperty("java.runtime.version"));r.put("jvm",System.getProperty("java.vm.name"));r.put("os",System.getProperty("os.name")+" "+System.getProperty("os.arch"));r.put("availableProcessors",Runtime.getRuntime().availableProcessors());r.put("jvmArguments",ManagementFactory.getRuntimeMXBean().getInputArguments().stream().filter(v->v.startsWith("-Xmx")||v.startsWith("-Xms")||v.startsWith("-XX:+Use")||v.startsWith("-XX:ActiveProcessorCount")||v.startsWith("-XX:MaxDirectMemorySize")||v.startsWith("-XX:MaxMetaspaceSize")).toList());r.put("samples",List.copyOf(samples));r.put("allocationScope","sampled live-thread delta; exited-thread allocations may be missed");r.put("tickWindow","last <=2048 ticks per room, refreshed each second; not scheduling latency");return r;}
    public static long percentile(List<Long> sorted,double percentile){if(sorted.isEmpty()){return 0;}return sorted.get(Math.min(sorted.size()-1,Math.max(0,(int)Math.ceil(sorted.size()*percentile)-1)));}
}
