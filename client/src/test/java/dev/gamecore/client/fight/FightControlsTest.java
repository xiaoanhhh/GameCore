package dev.gamecore.client.fight;

import com.badlogic.gdx.Input;
import dev.gamecore.fight.FightCommand;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 不创建窗口，验证双人键位、短按锁存和清理，不依赖操作系统自动重复。 */
class FightControlsTest {
    @Test void shortDropChordRemembersModifierUntilTick() {
        var c = new FightControls(); c.down(Input.Keys.S); c.down(Input.Keys.K); c.up(Input.Keys.S); c.up(Input.Keys.K);
        var value = c.command(0); assertTrue(value.jump()); assertTrue(value.down()); assertFalse(c.command(0).down());
    }
    @Test void shortTapSurvivesUntilNextTickOnce() {
        var c = new FightControls(); c.down(Input.Keys.J); c.up(Input.Keys.J); assertTrue(c.command(0).punch()); assertFalse(c.command(0).punch());
    }
    @Test void heldMoveAndGuardRepeatButAttackDoesNot() {
        var c = new FightControls(); c.down(Input.Keys.A); c.down(Input.Keys.S); c.down(Input.Keys.U);
        assertEquals(-1, c.command(0).move()); c.down(Input.Keys.U); var v = c.command(0); assertTrue(v.guard()); assertFalse(v.ranged());
        c.up(Input.Keys.A); assertEquals(0, c.command(0).move());
    }
    @Test void twoPlayersAreIndependent() {
        var c = new FightControls(); c.down(Input.Keys.I); c.down(Input.Keys.NUMPAD_5); c.down(Input.Keys.RIGHT);
        assertTrue(c.command(0).ultimate()); var two = c.command(1); assertTrue(two.ultimate()); assertEquals(1, two.move());
    }
    @Test void jumpAliasesAndOpposingDirectionsWork() {
        var c = new FightControls(); c.down(Input.Keys.W); c.down(Input.Keys.A); c.down(Input.Keys.D);
        var v = c.command(0); assertTrue(v.jump()); assertEquals(0, v.move());
        c.down(Input.Keys.UP); assertTrue(c.command(1).jump());
    }
    @Test void clearingRemovesHeldAndPendingActions() {
        var c = new FightControls(); c.down(Input.Keys.L); c.down(Input.Keys.DOWN); c.clear(); assertEquals(FightCommand.NONE, c.command(0)); assertEquals(FightCommand.NONE, c.command(1));
        assertFalse(c.accepts(Input.Keys.P)); assertThrows(IllegalArgumentException.class, () -> c.command(2));
    }
}
