package dev.gamecore.client.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 覆盖重复键、保留键与不可变重绑，避免用户保存后无法操作。 */
class KeyBindingsTest {
    @Test void rebindCreatesNewSnapshot() {
        KeyBindings old = KeyBindings.defaults();
        KeyBindings next = old.with(KeyBindings.Action.UP, Input.Keys.I);
        assertEquals(Input.Keys.W, old.up());
        assertEquals(Input.Keys.I, next.up());
        assertEquals(old.down(), next.down());
    }

    @Test void rejectsConflictingDirections() {
        assertThrows(IllegalArgumentException.class, () -> KeyBindings.defaults().with(KeyBindings.Action.UP, Input.Keys.S));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, Input.Keys.ESCAPE, Input.Keys.TAB, Input.Keys.ENTER, Input.Keys.P})
    void rejectsReservedOrUnsupportedKeys(int key) {
        assertThrows(IllegalArgumentException.class, () -> KeyBindings.defaults().with(KeyBindings.Action.UP, key));
    }

    @Test void supportsArrowKeyPreset() {
        assertEquals(Input.Keys.RIGHT, new KeyBindings(Input.Keys.UP, Input.Keys.DOWN, Input.Keys.LEFT, Input.Keys.RIGHT).right());
    }
}
