package dev.gamecore.client.network;
import com.badlogic.gdx.scenes.scene2d.Actor;
/** 仅渲染线程调用的游戏专属展示/操作端口；规则适配器与Scene2D不互相依赖。 */
public interface OnlineGameView extends AutoCloseable {
    /** 页面注入输入/选择意图，展示适配器不直接访问连接或世界。 */
    interface Host {
        void choose(java.util.Map<String,String> choices,boolean ready);
        void register(Actor actor);
        void ranking();
    }
    Actor actor(); boolean key(int key,boolean down); byte[] command(); void snapshot(byte[] state); void clear();
    default void snapshot(byte[] state,long arrival){snapshot(state);}
    default void advance(float delta,long now){advance(delta);}
    default void advance(float delta) { } String hint();
    /** slot 是服务端确认的身份，-1 表示尚未分配。展示层不改变比赛状态。 */
    default void connection(String notice,int slot) { }
    /** alert 表示连接/控制错误等必须原样展示的提示，游戏专属状态文案不得覆盖。 */
    default void connection(String notice,int slot,boolean alert) { connection(notice,slot); }
    default void frozen(boolean value) { }
    default Actor selectionActor(){return null;}
    default boolean selecting(){return false;}
    default void openSelection(){ }
    default void cancelReconfigure(){ }
    default boolean selectionKey(int key,boolean down){return false;}
    default void room(dev.gamecore.protocol.GameProtocol.RoomState state,int slot){ }
    default void events(java.util.List<dev.gamecore.api.event.GameEvent> events){ }
    default void newMatch() { clear(); }
    /** 同房双方同意重开后的新 Match；默认与首次进入相同，游戏可保留已确认的选择。 */
    default void rematch() { newMatch(); }
    default String summary() { return ""; }
    @Override default void close() { }
}
