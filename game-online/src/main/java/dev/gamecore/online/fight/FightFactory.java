package dev.gamecore.online.fight;
import dev.gamecore.api.online.*;
import dev.gamecore.fight.*;
import dev.gamecore.online.ProtocolValues;
import dev.gamecore.protocol.GameProtocol;
import java.util.*;
/** 终极格斗只适配既有fight/7；技能脉冲仅执行一次，持续移动/格挡有租约。 */
public final class FightFactory implements GameFactory<FightCommand,FightSnapshot> {
    private final GameType type=new GameType("fight","1","fight/7",2,2,60,60);
    private final FightSnapshotCodec snapshots=new FightSnapshotCodec();private final FightBot bot=new FightBot(42);
    private final GameInputCodec<FightCommand> inputs=new GameInputCodec<>(){
        @Override public FightCommand decode(byte[] bytes){try{var all=GameProtocol.GameInput.parseFrom(bytes);ProtocolValues.known(all);if(!all.hasFight()){throw new IllegalArgumentException("Wrong game input");}var c=all.getFight();ProtocolValues.known(c);return new FightCommand(c.getMove(),c.getJump(),c.getPunch(),c.getRanged(),c.getUltimate(),c.getGuard(),c.getDash(),c.getDown(),c.getLaugh());}catch(com.google.protobuf.InvalidProtocolBufferException e){throw new IllegalArgumentException("Malformed Fight input",e);}}
        @Override public byte[] encode(FightCommand c){return GameProtocol.GameInput.newBuilder().setFight(GameProtocol.FightInput.newBuilder().setMove(c.move()).setJump(c.jump()).setPunch(c.punch()).setRanged(c.ranged()).setUltimate(c.ultimate()).setGuard(c.guard()).setDash(c.dash()).setDown(c.down()).setLaugh(c.laugh())).build().toByteArray();}
        @Override public Optional<FightCommand> held(FightCommand c){return Optional.of(new FightCommand(c.move(),false,false,false,false,c.guard(),false,c.down(),false));}
        @Override public Optional<FightCommand> neutral(){return Optional.of(FightCommand.NONE);}
    };
    @Override public GameType type(){return type;}@Override public GameRuleConfig defaults(){return new GameRuleConfig(2,60,Map.of("map","NEON","one","NAIWA","two","FEIDUDU"));}
    @Override public int openingMillis(){return 7650;}
    @Override public GameRuleConfig select(GameRuleConfig current,int slot,Map<String,String> choices){
        if(slot<0||slot>1||choices.isEmpty()||!Set.of(slot==0?"one":"two","map").containsAll(choices.keySet())||slot!=0&&choices.containsKey("map")){throw new IllegalArgumentException("Selection ownership");}
        var values=new TreeMap<>(current.options());values.putAll(choices);var next=new GameRuleConfig(current.players(),current.roomTps(),values);validate(next);return next;
    }
    @Override public void validate(GameRuleConfig c){c.require(type);if(!c.options().keySet().equals(Set.of("map","one","two"))){throw new IllegalArgumentException("Fight options");}FightMap.valueOf(c.options().get("map"));FighterKind.valueOf(c.options().get("one"));FighterKind.valueOf(c.options().get("two"));}
    @Override public GameSessionFactory<FightCommand,FightSnapshot> sessions(){return(c,seed)->{validate(c);return new FightSessionFactory().create(new FightSessionFactory.Config(FighterKind.valueOf(c.options().get("one")),FighterKind.valueOf(c.options().get("two")),FightSessionFactory.Mode.DUEL,FightMap.valueOf(c.options().get("map"))),seed);};}
    @Override public GameInputCodec<FightCommand> inputs(){return inputs;}@Override public GameSnapshotCodec<FightSnapshot> snapshots(){return snapshots;}
    @Override public FightCommand bot(FightSnapshot s,int slot,long step,boolean active){return active?bot.command(s,slot):FightCommand.NONE;}
}
