package dev.gamecore.tank;

import dev.gamecore.api.event.GameEvent;
import dev.gamecore.api.session.GameSession;
import dev.gamecore.api.session.PlayerInput;
import dev.gamecore.api.session.SessionInputs;
import dev.gamecore.api.session.SessionState;
import java.util.List;
import java.util.Objects;

/**
 * 60Hz 会话：同玩家同 Tick 按序执行，最后一条完整命令生效并持续到新命令/暂停。
 * Bot 只在 RUNNING 步开始生成输入；人机 P2 拒绝人类覆盖。物理与地图仍由 TankGame 管理。
 */
public final class TankSession implements GameSession<TankCommand, TankSnapshot> {
    private final TankGame game;
    private final TankMode mode;
    private final TankBot bot;
    private final SessionInputs<TankCommand> inputs = new SessionInputs<>();
    private List<GameEvent> events = List.of();
    public TankSession(TankMap map, TankMode mode, TankBot.Level level, long seed) {
        game = new TankGame(map); this.mode = Objects.requireNonNull(mode); Objects.requireNonNull(level);
        bot = mode == TankMode.BOT ? new ArenaBot(level, seed) : null;
    }
    @Override public TankSnapshot snapshot() { return game.snapshot(); }
    public TankBot.State botState() { return bot == null ? TankBot.State.PATROL : bot.state(); }
    @Override public SessionState state() { return switch (game.state()) {
        case READY -> SessionState.READY; case RUNNING -> SessionState.RUNNING;
        case PAUSED -> SessionState.PAUSED; case FINISHED -> SessionState.FINISHED;
    }; }
    @Override public long tickNumber() { return game.tickNumber(); }
    @Override public int ticksPerSecond() { return TankGame.TICKS_PER_SECOND; }
    @Override public List<GameEvent> events() { return events; }
    @Override public void start() { if (state() == SessionState.READY) { game.start(); } }
    @Override public void resume() { if (state() == SessionState.PAUSED) { game.start(); } }
    @Override public void pause() { inputs.clear(); game.pause(); }
    @Override public boolean submitInput(PlayerInput<TankCommand> input) {
        Objects.requireNonNull(input);
        if (input.player() > 1) { throw new IllegalArgumentException("Unknown player"); }
        return state() == SessionState.RUNNING && !(mode == TankMode.BOT && input.player() == 1)
                && inputs.offer(input, tickNumber() + 1);
    }
    /** 本地便利入口沿用持续命令语义；公共入口显式记录下一 Tick 和顺序。 */
    public void command(int player, TankCommand command) {
        submitInput(new PlayerInput<>(tickNumber() + 1, player, inputs.nextSequence(), command));
    }
    @Override public void tick() {
        if (state() != SessionState.RUNNING) { return; }
        var before = snapshot();
        for (var input : inputs.drain()) { game.command(input.player(), input.value()); }
        if (bot != null) { game.command(1, bot.command(before, 1)); }
        game.tick();
        var after = snapshot();
        if (isFinished()) { inputs.clear(); }
        events = TankEvents.between(before, after);
    }
}
