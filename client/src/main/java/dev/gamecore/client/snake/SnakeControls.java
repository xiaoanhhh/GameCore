package dev.gamecore.client.snake;

import com.badlogic.gdx.Input;
import dev.gamecore.client.settings.KeyBindings;
import dev.gamecore.snake.Direction;
import java.util.Set;

/** 双人键盘分配：P1 沿用大厅设置；P2 优先方向键，冲突时选择完整的不相交键组。 */
public final class SnakeControls {
    private final KeyBindings first;
    private final KeyBindings second;
    public SnakeControls(KeyBindings first) {
        this.first = first;
        Set<Integer> occupied = Set.of(first.up(), first.down(), first.left(), first.right());
        KeyBindings[] choices = {
            new KeyBindings(Input.Keys.UP, Input.Keys.DOWN, Input.Keys.LEFT, Input.Keys.RIGHT),
            new KeyBindings(Input.Keys.I, Input.Keys.K, Input.Keys.J, Input.Keys.L),
            new KeyBindings(Input.Keys.NUM_8, Input.Keys.NUM_5, Input.Keys.NUM_4, Input.Keys.NUM_6),
            new KeyBindings(Input.Keys.T, Input.Keys.G, Input.Keys.F, Input.Keys.H),
            new KeyBindings(Input.Keys.U, Input.Keys.N, Input.Keys.B, Input.Keys.M)
        };
        KeyBindings candidate = null;
        for (KeyBindings choice : choices) {
            if (java.util.Arrays.stream(KeyBindings.Action.values()).noneMatch(action -> occupied.contains(choice.key(action)))) {
                candidate = choice; break;
            }
        }
        // 五组互斥候选、P1 只有四个键，因此总能找到一组。
        if (candidate == null) { throw new IllegalStateException("No disjoint key group"); }
        second = candidate;
    }
    public KeyBindings keys(int player) {
        if (player < 0 || player > 1) { throw new IllegalArgumentException("Unknown player"); }
        return player == 0 ? first : second;
    }
    public Direction direction(int player, int key) {
        KeyBindings keys = keys(player);
        if (key == keys.up()) { return Direction.UP; }
        if (key == keys.down()) { return Direction.DOWN; }
        if (key == keys.left()) { return Direction.LEFT; }
        if (key == keys.right()) { return Direction.RIGHT; }
        return null;
    }
    public String label(int player) {
        KeyBindings keys = keys(player);
        return "↑ " + Input.Keys.toString(keys.up()) + "   ↓ " + Input.Keys.toString(keys.down())
                + "\n← " + Input.Keys.toString(keys.left()) + "   → " + Input.Keys.toString(keys.right());
    }
}
