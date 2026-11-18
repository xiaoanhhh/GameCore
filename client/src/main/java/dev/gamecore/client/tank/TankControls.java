package dev.gamecore.client.tank;

import com.badlogic.gdx.Input;
import dev.gamecore.client.settings.KeyBindings;
import dev.gamecore.tank.TankCommand;
import java.util.HashSet;
import java.util.Set;

/** 按下/释放形成持续命令；P1 继承设置，P2 选择不相交键组；暂停/失焦必须 clear，防止粘键。 */
public final class TankControls {
    private final KeyBindings first;
    private final KeyBindings second;
    private final Set<Integer> held = new HashSet<>();
    public TankControls(KeyBindings first) {
        this.first = first;
        Set<Integer> occupied = Set.of(first.up(), first.down(), first.left(), first.right());
        KeyBindings[] candidates = {
            new KeyBindings(Input.Keys.UP, Input.Keys.DOWN, Input.Keys.LEFT, Input.Keys.RIGHT),
            new KeyBindings(Input.Keys.I, Input.Keys.K, Input.Keys.J, Input.Keys.L),
            new KeyBindings(Input.Keys.NUM_8, Input.Keys.NUM_5, Input.Keys.NUM_4, Input.Keys.NUM_6),
            new KeyBindings(Input.Keys.T, Input.Keys.G, Input.Keys.F, Input.Keys.H),
            new KeyBindings(Input.Keys.U, Input.Keys.N, Input.Keys.B, Input.Keys.M)
        };
        KeyBindings found = null;
        for (KeyBindings candidate : candidates) {
            if (java.util.Arrays.stream(KeyBindings.Action.values()).noneMatch(a -> occupied.contains(candidate.key(a)))) { found = candidate; break; }
        }
        if (found == null) { throw new IllegalStateException("No player two controls"); }
        second = found;
    }
    public KeyBindings keys(int player) {
        if (player < 0 || player > 1) { throw new IllegalArgumentException("Unknown player"); }
        return player == 0 ? first : second;
    }
    public int fireKey(int player) { keys(player); return player == 0 ? Input.Keys.SPACE : Input.Keys.ENTER; }
    public boolean accepts(int key) {
        if (key == Input.Keys.SPACE || key == Input.Keys.ENTER) { return true; }
        for (int player = 0; player < 2; player++) {
            for (KeyBindings.Action action : KeyBindings.Action.values()) { if (keys(player).key(action) == key) { return true; } }
        }
        return false;
    }
    public void down(int key) { if (accepts(key)) { held.add(key); } }
    public void up(int key) { held.remove(key); }
    public void clear() { held.clear(); }
    public TankCommand command(int player) {
        var k = keys(player);
        return new TankCommand(axis(k.up(), k.down()), axis(k.left(), k.right()), held.contains(fireKey(player)));
    }
    private int axis(int positive, int negative) { return (held.contains(positive) ? 1 : 0) - (held.contains(negative) ? 1 : 0); }
    public String label(int player) {
        var k = keys(player);
        return "前进 " + Input.Keys.toString(k.up()) + " / 后退 " + Input.Keys.toString(k.down())
                + "\n左转 " + Input.Keys.toString(k.left()) + " / 右转 " + Input.Keys.toString(k.right())
                + "\n开火 " + Input.Keys.toString(fireKey(player));
    }
}
