package dev.gamecore.client.settings;

import com.badlogic.gdx.Input;
import java.util.stream.IntStream;

/** 客户端方向键映射；保留导航和暂停键，避免设置产生无法操作的界面。 */
public record KeyBindings(int up, int down, int left, int right) {
    public enum Action {
        UP("向上"), DOWN("向下"), LEFT("向左"), RIGHT("向右");
        private final String label;
        Action(String label) { this.label = label; }
        public String label() { return label; }
    }

    public KeyBindings {
        int[] keys = {up, down, left, right};
        if (IntStream.of(keys).distinct().count() != 4 || IntStream.of(keys).anyMatch(key -> !assignable(key))) {
            throw new IllegalArgumentException("方向键不能重复；请选择字母、数字或方向键，P 用于暂停。");
        }
    }

    public static boolean assignable(int key) {
        return key != Input.Keys.P && ((key >= Input.Keys.A && key <= Input.Keys.Z)
                || (key >= Input.Keys.NUM_0 && key <= Input.Keys.NUM_9)
                || key == Input.Keys.UP || key == Input.Keys.DOWN || key == Input.Keys.LEFT || key == Input.Keys.RIGHT);
    }

    public static KeyBindings defaults() {
        return new KeyBindings(Input.Keys.W, Input.Keys.S, Input.Keys.A, Input.Keys.D);
    }

    public int key(Action action) {
        return switch (action) { case UP -> up; case DOWN -> down; case LEFT -> left; case RIGHT -> right; };
    }

    public KeyBindings with(Action action, int key) {
        return new KeyBindings(action == Action.UP ? key : up, action == Action.DOWN ? key : down,
                action == Action.LEFT ? key : left, action == Action.RIGHT ? key : right);
    }
}
