package dev.gamecore.client.fight;

import dev.gamecore.fight.FightMap;
import dev.gamecore.fight.FightSessionFactory;
import dev.gamecore.fight.FighterKind;
import java.util.Objects;

/** 出战选择的草稿事务：浏览不改对局；确认才提交，取消恢复已提交配置。无图形或设备依赖。 */
public final class FightSelection {
    public enum Page { FIGHTERS, MAPS, CLOSED }
    private FightSessionFactory.Config committed;
    private FighterKind one, two;
    private FightMap map;
    private Page page;
    public FightSelection(FightSessionFactory.Mode mode) {
        committed=new FightSessionFactory.Config(FighterKind.NAIWA,FighterKind.FEIDUDU,Objects.requireNonNull(mode),FightMap.NEON);
        open();
    }
    public void open(){one=committed.one();two=committed.two();map=committed.map();page=Page.FIGHTERS;}
    public boolean active(){return page!=Page.CLOSED;}
    public Page page(){return page;}
    public FightSessionFactory.Config committed(){return committed;}
    public FighterKind fighter(int player){checkPlayer(player);return player==0?one:two;}
    public FightMap map(){return map;}
    public void choose(int player,FighterKind kind){requireOpen();checkPlayer(player);Objects.requireNonNull(kind);if(player==0){one=kind;}else{two=kind;}}
    public void cycleFighter(int player,int delta){choose(player,FighterKind.values()[Math.floorMod(fighter(player).ordinal()+delta,FighterKind.values().length)]);}
    public void choose(FightMap value){requireOpen();map=Objects.requireNonNull(value);}
    public void cycleMap(int delta){choose(FightMap.values()[Math.floorMod(map.ordinal()+delta,FightMap.values().length)]);}
    public void fighters(){requireOpen();page=Page.FIGHTERS;}
    public void maps(){requireOpen();page=Page.MAPS;}
    /** 第一页只前进；地图页才提交，避免 Enter 连按跳过角色/地图两个明确阶段。 */
    public boolean next(){requireOpen();if(page==Page.FIGHTERS){page=Page.MAPS;return false;}
        committed=new FightSessionFactory.Config(one,two,committed.mode(),map);page=Page.CLOSED;return true;}
    public void cancel(){one=committed.one();two=committed.two();map=committed.map();page=Page.CLOSED;}
    /** 在线准备仅同步已确认配置；浏览保留自己的草稿，对手角色和非房主地图跟随服务器。 */
    public void observe(FightSessionFactory.Config config,int localPlayer){
        committed=Objects.requireNonNull(config);
        if(!active()){one=config.one();two=config.two();map=config.map();}
        else{if(localPlayer!=0){one=config.one();map=config.map();}if(localPlayer!=1){two=config.two();}}
    }
    private void requireOpen(){if(!active()){throw new IllegalStateException("Selection is closed");}}
    private static void checkPlayer(int player){if(player<0||player>1){throw new IllegalArgumentException("Unknown player");}}
}
