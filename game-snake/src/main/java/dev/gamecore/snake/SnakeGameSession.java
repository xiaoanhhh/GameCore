package dev.gamecore.snake;

import dev.gamecore.api.event.GameEvent;
import dev.gamecore.api.session.GameSession;
import dev.gamecore.api.session.PlayerInput;
import dev.gamecore.api.session.SessionInputs;
import dev.gamecore.api.session.SessionState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 固定 20Hz 会话；整数移动相位保留在 Snake，不强迫 Tank 采用网格节拍。
 * 输入在目标 Tick 开始时按序号交给合法转弯队列（最多两次）；暂停清空两个队列及相位。
 * 公共事件仅为不可变输出，模拟不调用任何客户端监听器。
 */
public final class SnakeGameSession implements GameSession<Direction, SnakeSnapshot> {
    public static final int TICKS_PER_SECOND = 20;
    private final SnakeGame game;
    private final SessionInputs<Direction> inputs = new SessionInputs<>();
    private List<GameEvent> events = List.of();
    private long simulationTick;
    private int movementCredit;
    public SnakeGameSession(SnakeMode mode, long seed) { this(new SnakeGame(mode, seed)); }
    /** 显式配置入口；仍使用同一个网格、移动相位和同时碰撞实现。 */
    public static SnakeGameSession duelWithObstacles(long seed) { return new SnakeGameSession(SnakeGame.duelWithObstacles(seed)); }
    SnakeGameSession(SnakeGame game) { this.game = Objects.requireNonNull(game); }
    @Override public void start() { game.start(); }
    @Override public void pause() {
        if (state() == SessionState.RUNNING) { movementCredit = 0; inputs.clear(); }
        game.pause();
    }
    @Override public void resume() { game.resume(); }
    @Override public SessionState state() { return switch (game.state()) {
        case READY -> SessionState.READY; case RUNNING -> SessionState.RUNNING;
        case PAUSED -> SessionState.PAUSED; case FINISHED -> SessionState.FINISHED;
    }; }
    @Override public long tickNumber() { return simulationTick; }
    @Override public int ticksPerSecond() { return TICKS_PER_SECOND; }
    @Override public List<GameEvent> events() { return events; }
    @Override public boolean submitInput(PlayerInput<Direction> input) {
        Objects.requireNonNull(input);
        if (input.player() >= game.snapshot().players().size()) { throw new IllegalArgumentException("Unknown player"); }
        return state() == SessionState.RUNNING && inputs.offer(input, simulationTick + 1);
    }
    /** 本地兼容入口；返回值表示接收，规则合法性在目标 Tick 判断。新调用者应显式传 Tick/序号。 */
    public boolean turn(int player, Direction direction) {
        return submitInput(new PlayerInput<>(simulationTick + 1, player, inputs.nextSequence(), direction));
    }
    @Override public void tick() {
        if (state() != SessionState.RUNNING) { return; }
        SnakeSnapshot before = game.snapshot();
        for (var input : inputs.drain()) { game.turn(input.player(), input.value()); }
        simulationTick++;
        movementCredit += movementRate();
        if (movementCredit >= TICKS_PER_SECOND) { movementCredit -= TICKS_PER_SECOND; game.tick(); }
        SnakeSnapshot after = game.snapshot();
        List<GameEvent> batch = new ArrayList<>();
        for (int player = 0; player < after.players().size(); player++) {
            var old = before.players().get(player); var next = after.players().get(player);
            if (old.score() != next.score()) { batch.add(new GameEvent.ScoreChanged(simulationTick, player, old.score(), next.score())); }
            if (old.alive() && !next.alive()) { batch.add(new GameEvent.PlayerDied(simulationTick, player)); }
        }
        if (isFinished()) { inputs.clear(); batch.add(new GameEvent.MatchEnded(simulationTick, after.result().name())); }
        events = List.copyOf(batch);
    }
    public int movementRate() { return ScoreSystem.movementRate(game.snapshot().players()); }
    @Override public SnakeSnapshot snapshot() {
        var world = game.snapshot();
        return new SnakeSnapshot(world.mode(), world.state(), world.result(), world.tick(), world.players(),
                world.obstacles(), world.food(), simulationTick, movementCredit);
    }
    /** 兼容既有计时验收；公共快照现在也包含相位，回放最终比较不会漏掉移动时机。 */
    public TimingSnapshot timingSnapshot() { return new TimingSnapshot(simulationTick, movementCredit, movementRate(), snapshot()); }
    public record TimingSnapshot(long simulationTick, int movementCredit, int movementRate, SnakeSnapshot world) { }
}
