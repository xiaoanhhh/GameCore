package dev.gamecore.snake;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** 单条蛇的可变状态与转弯队列，仅由规则线程使用；不暴露给 UI，边界通过快照复制。 */
final class SnakePlayer {
    final List<Cell> body;
    Direction direction;
    int score;
    SnakeGame.Collision collision = SnakeGame.Collision.NONE;
    private final ArrayDeque<Direction> turns = new ArrayDeque<>();
    SnakePlayer(SnakeSnapshot.Player initial) {
        body = new ArrayList<>(initial.body()); direction = initial.direction(); score = initial.score();
    }
    boolean turn(Direction next) {
        Direction previous = turns.isEmpty() ? direction : turns.getLast();
        if (turns.size() >= 2 || previous == next || previous.opposite(next)) { return false; }
        turns.addLast(next); return true;
    }
    void clearTurns() { turns.clear(); }
    boolean move(Cell food) {
        if (!turns.isEmpty()) { direction = turns.removeFirst(); }
        Cell next = body.getFirst().move(direction);
        boolean growing = next.equals(food);
        body.addFirst(next);
        if (!growing) { body.removeLast(); }
        return growing;
    }
    SnakeSnapshot.Player snapshot() { return new SnakeSnapshot.Player(body, direction, score, collision); }
}
