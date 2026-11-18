package dev.gamecore.api.online;
import java.util.Optional;
/** 有界显式编码，输入只含意图；held决定缺新输入时是否持续，按下沿不能每Tick重发。 */
public interface GameInputCodec<I> {
    /** 持续命令租约到期必须显式释放；按次转向的游戏可保持空。 */
    default java.util.Optional<I> neutral() { return java.util.Optional.empty(); }
    I decode(byte[] bytes);
    byte[] encode(I input);
    Optional<I> held(I input);
}
