package dev.gamecore.client.snake;
import dev.gamecore.client.timing.FixedStepClock;

import static org.junit.jupiter.api.Assertions.*;
import dev.gamecore.snake.Direction;
import dev.gamecore.snake.SnakeGame;
import dev.gamecore.snake.SnakeGameSession;
import dev.gamecore.snake.SnakeMode;
import org.junit.jupiter.api.Test;

/** 不只核对计时数：将同一模拟 Tick 输入送入真实规则，比较不同渲染帧率下的完整会话。 */
class FrameRateIndependenceTest {
    @Test void thirtySixtyAndOneFortyFourFpsProduceIdenticalSessionIncludingPhase() {
        SnakeGameSession.TimingSnapshot expected = null;
        for (int fps : new int[]{30, 60, 144}) {
            var game = new SnakeGameSession(SnakeMode.CLASSIC, 42); var clock = new FixedStepClock(20); game.start();
            for (int frame = 0; frame < fps * 4; frame++) {
                int updates = clock.advance(1.0 / fps);
                for (int update = 0; update < updates; update++) {
                    long tick = game.timingSnapshot().simulationTick();
                    Direction direction = switch ((int) (tick % 40)) {
                        case 0 -> Direction.UP; case 10 -> Direction.LEFT; case 20 -> Direction.DOWN; case 30 -> Direction.RIGHT;
                        default -> null;
                    };
                    if (direction != null) { game.turn(0, direction); }
                    game.tick();
                }
            }
            assertEquals(80, game.timingSnapshot().simulationTick());
            assertEquals(SnakeGame.State.RUNNING, game.snapshot().state());
            if (expected == null) { expected = game.timingSnapshot(); } else { assertEquals(expected, game.timingSnapshot()); }
        }
    }
}
