package dev.gamecore.server.devtools;
import java.util.*;
/** 工具报告的小型JSON写出器；仅处理调用方明确列出的无凭证指标，不反射任意对象。 */
public final class Json {
    private Json() { }
    public static String encode(Object value){
        if(value==null){return "null";}if(value instanceof Boolean){return value.toString();}
        if(value instanceof Number n){return Double.isFinite(n.doubleValue())?n.toString():"null";}
        if(value instanceof Map<?,?> map){return "{"+String.join(",",map.entrySet().stream().map(e->encode(e.getKey().toString())+":"+encode(e.getValue())).toList())+"}";}
        if(value instanceof Iterable<?> list){var parts=new ArrayList<String>();list.forEach(v->parts.add(encode(v)));return "["+String.join(",",parts)+"]";}
        var s=value.toString();var b=new StringBuilder("\"");for(int i=0;i<s.length();i++){char c=s.charAt(i);switch(c){case '"'->b.append("\\\"");case '\\'->b.append("\\\\");case '\n'->b.append("\\n");case '\r'->b.append("\\r");case '\t'->b.append("\\t");default->{if(c<32){b.append(String.format("\\u%04x",(int)c));}else{b.append(c);}}}}return b.append('"').toString();
    }
}
