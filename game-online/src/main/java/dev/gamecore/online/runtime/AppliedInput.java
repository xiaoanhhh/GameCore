package dev.gamecore.online.runtime;
/** 真正交给核心Tick的规范输入；来源序号用于ack，canonicalSequence只在会话内排序。 */
public record AppliedInput(int player, long sourceSequence, long canonicalSequence, byte[] command) {
    public AppliedInput { command=command.clone(); }
    @Override public byte[] command(){return command.clone();}
}
