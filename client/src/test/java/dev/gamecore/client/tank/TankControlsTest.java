package dev.gamecore.client.tank;

import com.badlogic.gdx.Input;
import dev.gamecore.client.settings.KeyBindings;
import dev.gamecore.tank.TankCommand;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TankControlsTest {
    @Test void controlsHoldReleaseAndCancelOppositeKeys() {
        var controls = new TankControls(KeyBindings.defaults());
        controls.down(Input.Keys.W); controls.down(Input.Keys.A); controls.down(Input.Keys.SPACE);
        assertEquals(new TankCommand(1, 1, true), controls.command(0)); controls.down(Input.Keys.S);
        assertEquals(0, controls.command(0).throttle()); controls.up(Input.Keys.W);
        assertEquals(-1, controls.command(0).throttle()); controls.clear();
        assertEquals(TankCommand.IDLE, controls.command(0));
    }
    @Test void reboundPlayerOneAndSecondPlayerNeverShareControlsOrFire() {
        var keys = new KeyBindings(Input.Keys.UP, Input.Keys.DOWN, Input.Keys.LEFT, Input.Keys.RIGHT);
        var controls = new TankControls(keys); Set<Integer> first = Set.of(keys.up(), keys.down(), keys.left(), keys.right(), controls.fireKey(0));
        for (var action : KeyBindings.Action.values()) { assertFalse(first.contains(controls.keys(1).key(action))); }
        assertFalse(first.contains(controls.fireKey(1))); controls.down(Input.Keys.ENTER);
        assertTrue(controls.command(1).fire()); assertFalse(controls.command(0).fire());
        assertEquals(keys, controls.keys(0)); assertTrue(controls.label(1).contains("开火"));
    }
}
