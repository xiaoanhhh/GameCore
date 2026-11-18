package dev.gamecore.client.snake;

import dev.gamecore.snake.SnakeMode;
import dev.gamecore.snake.SnakeGame;
import dev.gamecore.snake.SnakeSnapshot;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/**
 * 每种模式保存最高个人分及已结束对局的个人前五名；分数相同保留较早录入者在前。
 * 保存失败保留本次内存成绩并明确提示；下次 flush 可重试，不向游戏规则传播 IO 异常。
 */
public final class SnakeRecords {
    public static final int LEADERBOARD_SIZE = 5;
    public record Entry(int player, int score) {
        public Entry {
            if (player < 1 || player > 2 || score < 0 || score % 10 != 0) { throw new IllegalArgumentException("Invalid entry"); }
        }
    }
    private final Path path;
    private final EnumMap<SnakeMode, Integer> best = new EnumMap<>(SnakeMode.class);
    private final EnumMap<SnakeMode, List<Entry>> rankings = new EnumMap<>(SnakeMode.class);
    private String notice = "";
    private boolean dirty;

    public SnakeRecords(Path path) {
        this.path = path.toAbsolutePath();
        for (SnakeMode mode : SnakeMode.values()) { best.put(mode, 0); rankings.put(mode, List.of()); }
        if (!Files.exists(this.path)) { return; }
        try (Reader reader = Files.newBufferedReader(this.path, StandardCharsets.UTF_8)) {
            Properties values = new Properties();
            values.load(reader);
            String version = values.getProperty("version");
            if (!"1".equals(version) && !"2".equals(version)) { throw new IllegalArgumentException("Unknown version"); }
            EnumMap<SnakeMode, Integer> loaded = new EnumMap<>(SnakeMode.class);
            EnumMap<SnakeMode, List<Entry>> loadedRankings = new EnumMap<>(SnakeMode.class);
            for (SnakeMode mode : SnakeMode.values()) {
                int score = Integer.parseInt(values.getProperty(mode.id()));
                if (score < 0 || score % 10 != 0) { throw new IllegalArgumentException("Invalid score"); }
                loaded.put(mode, score);
                List<Entry> entries = new ArrayList<>();
                if ("2".equals(version)) {
                    int count = Integer.parseInt(values.getProperty(mode.id() + ".rank.count"));
                    if (count < 0 || count > LEADERBOARD_SIZE) { throw new IllegalArgumentException("Invalid count"); }
                    for (int i = 0; i < count; i++) {
                        String prefix = mode.id() + ".rank." + i;
                        Entry entry = new Entry(Integer.parseInt(values.getProperty(prefix + ".player")),
                                Integer.parseInt(values.getProperty(prefix + ".score")));
                        if (entry.score() > score || mode != SnakeMode.DUO && entry.player() != 1) { throw new IllegalArgumentException("Invalid ranking"); }
                        entries.add(entry);
                    }
                    entries.sort(Comparator.comparingInt(Entry::score).reversed());
                }
                loadedRankings.put(mode, List.copyOf(entries));
            }
            best.putAll(loaded);
            rankings.putAll(loadedRankings);
            if ("1".equals(version)) { notice = "已保留旧版最佳分；排行榜从新完成的对局开始记录。"; }
        } catch (IOException | IllegalArgumentException exception) {
            notice = "成绩文件无法读取，暂用零分；新纪录保存时会覆盖原文件。";
        }
    }

    public int best(SnakeMode mode) { return best.get(mode); }
    public String notice() { return notice; }
    public boolean pendingSave() { return dirty; }
    public List<Entry> leaderboard(SnakeMode mode) { return rankings.get(mode); }

    /** 调用方对一局仅提交一次；只有 FINISHED 对局入榜，中途离页或重开不计作完整成绩。 */
    public void recordFinishedRound(SnakeSnapshot round) {
        if (round.state() != SnakeGame.State.FINISHED || round.result() == SnakeGame.Result.NONE
                || round.players().size() != (round.mode() == SnakeMode.DUO ? 2 : 1)) {
            throw new IllegalArgumentException("Expected completed round");
        }
        List<Entry> additions = new ArrayList<>();
        for (int i = 0; i < round.players().size(); i++) { additions.add(new Entry(i + 1, round.players().get(i).score())); }
        List<Entry> entries = new ArrayList<>(leaderboard(round.mode()));
        entries.addAll(additions);
        entries.sort(Comparator.comparingInt(Entry::score).reversed());
        rankings.put(round.mode(), List.copyOf(entries.subList(0, Math.min(entries.size(), LEADERBOARD_SIZE))));
        additions.forEach(entry -> best.merge(round.mode(), entry.score(), Math::max));
        dirty = true;
        flush();
    }

    public void record(SnakeMode mode, int score) {
        if (score < 0 || score % 10 != 0) { throw new IllegalArgumentException("Invalid score"); }
        if (score > best(mode)) { best.put(mode, score); dirty = true; flush(); }
    }

    public void flush() {
        if (!dirty) { return; }
        Path temporary = null;
        try {
            Files.createDirectories(path.getParent());
            temporary = Files.createTempFile(path.getParent(), "snake-records-", ".tmp");
            Properties values = new Properties();
            values.setProperty("version", "2");
            best.forEach((mode, score) -> values.setProperty(mode.id(), score.toString()));
            rankings.forEach((mode, entries) -> {
                values.setProperty(mode.id() + ".rank.count", Integer.toString(entries.size()));
                for (int i = 0; i < entries.size(); i++) {
                    String prefix = mode.id() + ".rank." + i;
                    values.setProperty(prefix + ".player", Integer.toString(entries.get(i).player()));
                    values.setProperty(prefix + ".score", Integer.toString(entries.get(i).score()));
                }
            });
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                values.store(writer, "Snake Arena personal best per mode");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            notice = "";
        } catch (IOException | SecurityException exception) {
            notice = "成绩保存失败：当前纪录仅保留在内存，请检查目录写入权限。";
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException | SecurityException ignored) { /* 下次写入仍使用新临时文件。 */ }
            }
        }
    }
}
