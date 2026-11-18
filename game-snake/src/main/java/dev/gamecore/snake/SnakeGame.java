package dev.gamecore.snake;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 网格规则与终局编排；一次 tick 表示全员同时移动一格，不读取系统时间。
 * 实际可玩会话由 SnakeGameSession 固定模拟节拍驱动；空间、玩家、碰撞和计分各自封装。
 */
public final class SnakeGame {
    public static final int SIZE = GridMap.SIZE;
    public static final int FOOD_POINTS = ScoreSystem.FOOD_POINTS;
    public enum State { READY, RUNNING, PAUSED, FINISHED }
    public enum Result { NONE, SOLO_LOSS, SOLO_WIN, PLAYER_ONE, PLAYER_TWO, DRAW }
    public enum Collision { NONE, WALL, OBSTACLE, SELF, OTHER, HEAD_ON, HEAD_SWAP }
    private final SnakeMode mode;
    private final SnakeWorld world;
    private State state = State.READY;
    private Result result = Result.NONE;
    private long tick;

    public SnakeGame(SnakeMode mode, long seed) {
        this(mode, seed, startingPlayers(mode), GridMap.forMode(mode).obstacles(), null);
    }
    /** 开发/在线受信配置复用现有障碍布局与双人出生点，不改变原本地模式。 */
    public static SnakeGame duelWithObstacles(long seed) {
        return new SnakeGame(SnakeMode.DUO, seed, startingPlayers(SnakeMode.DUO), GridMap.forMode(SnakeMode.OBSTACLES).obstacles(), null);
    }
    /** 包内场景入口只供精确规则测试，生产构造固定地图与合法初始玩家。 */
    SnakeGame(SnakeMode mode, long seed, List<SnakeSnapshot.Player> players, Set<Cell> walls, Cell food) {
        this.mode = Objects.requireNonNull(mode, "mode");
        world = new SnakeWorld(seed, players, new GridMap(walls), food);
    }
    private static List<SnakeSnapshot.Player> startingPlayers(SnakeMode mode) {
        var first = new SnakeSnapshot.Player(List.of(new Cell(5, 10), new Cell(4, 10), new Cell(3, 10)), Direction.RIGHT, 0, Collision.NONE);
        return mode != SnakeMode.DUO ? List.of(first) : List.of(first,
                new SnakeSnapshot.Player(List.of(new Cell(14, 10), new Cell(15, 10), new Cell(16, 10)), Direction.LEFT, 0, Collision.NONE));
    }
    /** 只读标量供会话调度使用，避免为了判断生命周期反复复制整个世界。 */
    public State state() { return state; }
    public void start() { if (state == State.READY) { state = State.RUNNING; } }
    public void pause() {
        if (state == State.RUNNING) { state = State.PAUSED; world.players.forEach(SnakePlayer::clearTurns); }
    }
    public void resume() { if (state == State.PAUSED) { state = State.RUNNING; } }
    public boolean turn(int player, Direction direction) {
        Objects.requireNonNull(direction, "direction");
        if (player < 0 || player >= world.players.size()) { throw new IllegalArgumentException("Unknown player"); }
        return state == State.RUNNING && world.players.get(player).turn(direction);
    }
    public void tick() {
        if (state != State.RUNNING) { return; }
        tick++;
        boolean eaten = world.move();
        long alive = world.players.stream().filter(player -> player.collision == Collision.NONE).count();
        if (alive < world.players.size()) {
            state = State.FINISHED;
            result = mode != SnakeMode.DUO ? Result.SOLO_LOSS : alive == 0 ? Result.DRAW
                    : world.players.getFirst().collision == Collision.NONE ? Result.PLAYER_ONE : Result.PLAYER_TWO;
        } else if (eaten) {
            world.food = world.spawnFood();
            if (world.food == null) {
                state = State.FINISHED;
                result = mode != SnakeMode.DUO ? Result.SOLO_WIN
                        : world.players.get(0).score == world.players.get(1).score ? Result.DRAW
                        : world.players.get(0).score > world.players.get(1).score ? Result.PLAYER_ONE : Result.PLAYER_TWO;
            }
        }
    }
    public SnakeSnapshot snapshot() { return new SnakeSnapshot(mode, state, result, tick, world.snapshots(), world.map.obstacles(), world.food); }
}
