package dev.gamecore.api.online;
/** 每款游戏显式注册一个适配器；共享Room/Netty代码只依赖这些契约。 */
public interface GameFactory<I, S> {
    GameType type();
    GameRuleConfig defaults();
    void validate(GameRuleConfig config);
    GameSessionFactory<I, S> sessions();
    GameInputCodec<I> inputs();
    GameSnapshotCodec<S> snapshots();
    /** 只合并连接所属槽位允许的选择；具体白名单由游戏适配器验证，不接受客户端完整配置。 */
    default GameRuleConfig select(GameRuleConfig current,int slot,java.util.Map<String,String> choices){throw new IllegalArgumentException("Selection unsupported");}
    /** 演出属于房间准备阶段，耗时不计入规则 Tick。 */
    default int openingMillis(){return 0;}
    /** 只用于无图形测试客户端，必须产生符合规则的意图。 */
    I bot(S snapshot, int slot, long step, boolean active);
}
