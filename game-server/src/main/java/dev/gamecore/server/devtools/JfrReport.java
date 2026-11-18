package dev.gamecore.server.devtools;
import jdk.jfr.consumer.RecordingFile;
import java.nio.file.*;
import java.util.*;
/** 离线JFR分析，GCPhasePause与采样分配明确区分，不把采样估计包装成精确分配值。 */
public final class JfrReport {
    private JfrReport() { }
    public static void main(String[] args) throws Exception{
        var o=DevToolsMain.options(args,Set.of("file","output"));var pauses=new ArrayList<Long>();long samples=0,weight=0,events=0;
        try(var recording=new RecordingFile(Path.of(Objects.requireNonNull(o.get("file"))))){while(recording.hasMoreEvents()){
            var e=recording.readEvent();events++;switch(e.getEventType().getName()){
                case "jdk.GCPhasePause"->pauses.add(e.getDuration().toNanos());
                case "jdk.ObjectAllocationSample"->{samples++;weight+=e.getLong("weight");}
                default->{ }
            }
        }}Collections.sort(pauses);var result=new LinkedHashMap<String,Object>();result.put("events",events);result.put("gcPauseEvents",pauses.size());result.put("gcPauseTotalMillis",pauses.stream().mapToLong(Long::longValue).sum()/1e6);result.put("gcPauseP50Millis",ServerTelemetry.percentile(pauses,.5)/1e6);result.put("gcPauseP95Millis",ServerTelemetry.percentile(pauses,.95)/1e6);result.put("gcPauseP99Millis",ServerTelemetry.percentile(pauses,.99)/1e6);result.put("allocationSamples",samples);result.put("estimatedSampledAllocationBytes",weight);
        result.put("scope","GCPhasePause only; ObjectAllocationSample weighted estimate; profile may affect results");String json=Json.encode(result);if(o.containsKey("output")){Files.writeString(Path.of(o.get("output")),json);}System.out.println(json);
    }
}
