package dev.gamecore.client.snake;

import static org.junit.jupiter.api.Assertions.*;
import com.badlogic.gdx.Input;
import dev.gamecore.client.settings.KeyBindings;
import dev.gamecore.snake.Direction;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 自定义 P1 按键不能让 P2 同时收到同一个方向键。 */
class SnakeControlsTest {
    @Test void defaultsUseWasdAndArrows() {
        var controls = new SnakeControls(KeyBindings.defaults());
        assertEquals(Direction.UP, controls.direction(0, Input.Keys.W));
        assertEquals(Direction.LEFT, controls.direction(1, Input.Keys.LEFT));
        assertNull(controls.direction(0, Input.Keys.P));
    }
    @Test void arrowRebindingChoosesIjklForSecondPlayer() {
        var keys = new KeyBindings(Input.Keys.UP, Input.Keys.DOWN, Input.Keys.LEFT, Input.Keys.RIGHT);
        var controls = new SnakeControls(keys);
        assertEquals(Input.Keys.I, controls.keys(1).up());
        assertEquals(Direction.DOWN, controls.direction(1, Input.Keys.K));
    }
    @Test void evenFourSeparateGroupConflictsLeaveADisjointGroup() {
        var keys = new KeyBindings(Input.Keys.UP, Input.Keys.I, Input.Keys.NUM_8, Input.Keys.T);
        var controls = new SnakeControls(keys);
        Set<Integer> occupied = Set.of(keys.up(), keys.down(), keys.left(), keys.right());
        for (var action : KeyBindings.Action.values()) { assertFalse(occupied.contains(controls.keys(1).key(action))); }
        assertEquals(Input.Keys.U, controls.keys(1).up());
        assertThrows(IllegalArgumentException.class, () -> controls.keys(2));
    }
}
