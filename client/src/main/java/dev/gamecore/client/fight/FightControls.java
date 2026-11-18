package dev.gamecore.client.fight;

import com.badlogic.gdx.Input;
import dev.gamecore.fight.FightCommand;
import java.util.HashSet;
import java.util.Set;

/** 格斗专用键位；一次按下锁存到下一 Tick，避免短按丢失或系统按键重复触发技能。 */
public final class FightControls {
    private final Set<Integer> held = new HashSet<>();
    private final Set<Integer> pressed = new HashSet<>();
    private final boolean[] dropPressed = new boolean[2];
    private static final int[][] KEYS = {
        {Input.Keys.A, Input.Keys.D, Input.Keys.K, Input.Keys.J, Input.Keys.U, Input.Keys.I, Input.Keys.S, Input.Keys.L, Input.Keys.W},
        {Input.Keys.LEFT, Input.Keys.RIGHT, Input.Keys.NUMPAD_2, Input.Keys.NUMPAD_1, Input.Keys.NUMPAD_4,
            Input.Keys.NUMPAD_5, Input.Keys.DOWN, Input.Keys.NUMPAD_3, Input.Keys.UP}
    };
    public boolean accepts(int key) {
        if(key==Input.Keys.SHIFT_LEFT||key==Input.Keys.SHIFT_RIGHT){return true;}
        for (int[] keys : KEYS) { for (int candidate : keys) { if (key == candidate) { return true; } } }
        return false;
    }
    public void down(int key) {
        if (accepts(key) && held.add(key)) {
            pressed.add(key);
            for (int p = 0; p < 2; p++) { if ((key == KEYS[p][2] || key == KEYS[p][8]) && held.contains(KEYS[p][6])) { dropPressed[p] = true; } }
        }
    }
    public void up(int key) { held.remove(key); }
    public void clear() { held.clear(); pressed.clear(); java.util.Arrays.fill(dropPressed, false); }
    public FightCommand command(int player) {
        if (player < 0 || player > 1) { throw new IllegalArgumentException("Unknown player"); }
        int[] k = KEYS[player];
        // Shift 明确表达穿台意图，记录为 down+jump 但不格挡；避免依靠两键恰好落在同一规则步来区分闪袭。
        boolean forceDrop=(held.contains(Input.Keys.SHIFT_LEFT)||held.contains(Input.Keys.SHIFT_RIGHT))
                &&(held.contains(k[6])||dropPressed[player])&&(pressed.contains(k[2])||pressed.contains(k[8]));
        FightCommand result = new FightCommand((held.contains(k[1]) ? 1 : 0) - (held.contains(k[0]) ? 1 : 0),
                pressed.contains(k[2]) || pressed.contains(k[8]), pressed.contains(k[3]), pressed.contains(k[4]),
                pressed.contains(k[5]), held.contains(k[6])&&!forceDrop, pressed.contains(k[7]), held.contains(k[6]) || dropPressed[player]);
        dropPressed[player] = false;
        for (int key : k) { pressed.remove(key); }
        return result;
    }
}
