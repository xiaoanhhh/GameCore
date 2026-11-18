package dev.gamecore.client.fight;

import dev.gamecore.fight.FightArena;
import dev.gamecore.fight.FightMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 场景构件只装饰真实落脚面，顶沿始终取规则几何；立面不增加隐形墙或更宽的可站边缘。 */
public final class FightSceneryLayout {
    public enum Shape { ROOF, BRIDGE, COLUMN }
    public record Prop(FightArena.Plate support,Shape shape,double depth) {
        public double left(){return support.left();}public double right(){return support.right();}
        public double top(){return support.height();}public double bottom(){return top()-depth;}
    }
    private static final Map<FightMap,List<Prop>> PLANS=new EnumMap<>(FightMap.class);
    private static final Map<FightMap,String> LEVELS=new EnumMap<>(FightMap.class);
    static{
        for(var map:FightMap.values()){
            PLANS.put(map,map.platforms().stream().map(p->{
                Shape shape=p.id()<2?Shape.ROOF:p.id()==2?Shape.BRIDGE:p.id()<5?Shape.COLUMN:Shape.BRIDGE;
                double depth=shape==Shape.BRIDGE&&map!=FightMap.BAMBOO&&map!=FightMap.FROST?50:p.height();
                return new Prop(p,shape,depth);
            }).toList());
            LEVELS.put(map,map.platforms().stream().mapToInt(p->(int)p.height()).distinct().sorted().mapToObj(Integer::toString).collect(java.util.stream.Collectors.joining(" / ")));
        }
    }
    private FightSceneryLayout(){}
    public static List<Prop> props(FightMap map){return PLANS.get(map);}
    public static String levels(FightMap map){return LEVELS.get(map);}
}
