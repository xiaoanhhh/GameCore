package dev.gamecore.client.snake;

import static org.junit.jupiter.api.Assertions.*;
import dev.gamecore.snake.SnakeMode;
import dev.gamecore.snake.SnakeGame;
import dev.gamecore.snake.SnakeSnapshot;
import dev.gamecore.snake.Cell;
import dev.gamecore.snake.Direction;
import java.util.List;
import java.util.Set;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 成绩测试全部在临时目录执行，包含真实 IO 故障及恢复，不读取用户存档。 */
class SnakeRecordsTest {
    @TempDir Path directory;
    @Test void missingFileDefaultsWithoutCreatingSave() {
        Path file = directory.resolve("scores.properties"); var records = new SnakeRecords(file);
        assertEquals(0, records.best(SnakeMode.CLASSIC)); assertEquals("", records.notice()); assertFalse(Files.exists(file));
    }
    @Test void bestOnlyIncreasesAndModesRoundTripIndependently() {
        Path file = directory.resolve("nested/scores.properties"); var records = new SnakeRecords(file);
        records.record(SnakeMode.CLASSIC, 40); records.record(SnakeMode.CLASSIC, 20);
        records.record(SnakeMode.OBSTACLES, 10); records.record(SnakeMode.DUO, 70);
        var loaded = new SnakeRecords(file);
        assertEquals(40, loaded.best(SnakeMode.CLASSIC)); assertEquals(10, loaded.best(SnakeMode.OBSTACLES));
        assertEquals(70, loaded.best(SnakeMode.DUO)); assertFalse(records.pendingSave());
    }
    @Test void damagedAndUnsupportedFilesFallBackWithNotice() throws IOException {
        Path file = directory.resolve("scores.properties");
        for (String content : new String[]{"broken", "version=9", "version=1\nclassic=20\nobstacles=-10\nduo=0", "version=1\nclassic=20\nobstacles=0\nduo=abc"}) {
            Files.writeString(file, content); var records = new SnakeRecords(file);
            assertEquals(0, records.best(SnakeMode.CLASSIC)); assertFalse(records.notice().isBlank());
            assertEquals(content, Files.readString(file));
        }
    }
    @Test void failedWriteKeepsMemoryScoreAndCanRetryWhenDirectoryRecovers() throws IOException {
        Path parent = directory.resolve("blocked"); Files.writeString(parent, "block");
        var records = new SnakeRecords(parent.resolve("scores.properties")); records.record(SnakeMode.DUO, 50);
        assertEquals(50, records.best(SnakeMode.DUO)); assertTrue(records.pendingSave());
        assertTrue(records.notice().contains("保存失败"));
        Files.delete(parent); Files.createDirectory(parent); records.flush();
        assertFalse(records.pendingSave()); assertEquals("", records.notice());
        assertEquals(50, new SnakeRecords(parent.resolve("scores.properties")).best(SnakeMode.DUO));
    }
    @Test void failedReplacementPreservesExistingTargetAndCleansTemporaryFiles() throws IOException {
        Path target = directory.resolve("scores.properties"); Files.createDirectory(target);
        Files.writeString(target.resolve("keep.txt"), "keep");
        var records = new SnakeRecords(target); records.record(SnakeMode.CLASSIC, 10);
        assertTrue(records.pendingSave()); assertEquals("keep", Files.readString(target.resolve("keep.txt")));
        try (var entries = Files.list(directory)) { assertEquals(1, entries.count()); }
    }
    @Test void rejectsNegativeAndNonFoodScores() {
        var records = new SnakeRecords(directory.resolve("scores.properties"));
        assertThrows(IllegalArgumentException.class, () -> records.record(SnakeMode.CLASSIC, -10));
        assertThrows(IllegalArgumentException.class, () -> records.record(SnakeMode.CLASSIC, 3));
    }
    private static SnakeSnapshot finished(SnakeMode mode, int... scores) {
        List<SnakeSnapshot.Player> players = java.util.Arrays.stream(scores).mapToObj(score ->
                new SnakeSnapshot.Player(List.of(new Cell(0, 0)), Direction.LEFT, score, SnakeGame.Collision.WALL)).toList();
        return new SnakeSnapshot(mode, SnakeGame.State.FINISHED, mode == SnakeMode.DUO ? SnakeGame.Result.DRAW : SnakeGame.Result.SOLO_LOSS,
                10, players, Set.of(), new Cell(10, 10));
    }
    @Test void completedRoundsProduceFiveSortedResultsAndTiesKeepEarlierPlayer() {
        var records = new SnakeRecords(directory.resolve("scores.properties"));
        for (int score : new int[]{10, 50, 20, 40, 30, 0, 60}) { records.recordFinishedRound(finished(SnakeMode.CLASSIC, score)); }
        assertEquals(List.of(60, 50, 40, 30, 20), records.leaderboard(SnakeMode.CLASSIC).stream().map(SnakeRecords.Entry::score).toList());
        records.recordFinishedRound(finished(SnakeMode.DUO, 70, 70));
        assertEquals(List.of(new SnakeRecords.Entry(1, 70), new SnakeRecords.Entry(2, 70)), records.leaderboard(SnakeMode.DUO));
        assertTrue(records.leaderboard(SnakeMode.OBSTACLES).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> records.leaderboard(SnakeMode.DUO).clear());
    }
    @Test void rankingAndBestRoundTripIncludingAZeroPointCompletedRound() {
        Path file = directory.resolve("scores.properties"); var records = new SnakeRecords(file);
        records.recordFinishedRound(finished(SnakeMode.CLASSIC, 0));
        records.recordFinishedRound(finished(SnakeMode.OBSTACLES, 30));
        records.recordFinishedRound(finished(SnakeMode.DUO, 10, 40));
        var loaded = new SnakeRecords(file);
        for (SnakeMode mode : SnakeMode.values()) {
            assertEquals(records.leaderboard(mode), loaded.leaderboard(mode)); assertEquals(records.best(mode), loaded.best(mode));
        }
    }
    @Test void legacyBestMigratesWithoutInventingHistoricalRounds() throws IOException {
        Path file = directory.resolve("scores.properties");
        Files.writeString(file, "version=1\nclassic=100\nobstacles=20\nduo=30\n");
        var records = new SnakeRecords(file); assertEquals(100, records.best(SnakeMode.CLASSIC));
        assertTrue(records.leaderboard(SnakeMode.CLASSIC).isEmpty()); assertTrue(records.notice().contains("旧版"));
        assertTrue(Files.readString(file).contains("version=1"));
        records.recordFinishedRound(finished(SnakeMode.CLASSIC, 10));
        var loaded = new SnakeRecords(file); assertEquals(100, loaded.best(SnakeMode.CLASSIC));
        assertEquals(List.of(new SnakeRecords.Entry(1, 10)), loaded.leaderboard(SnakeMode.CLASSIC));
        assertTrue(Files.readString(file).contains("version=2"));
    }
    @Test void runningOrMalformedRoundsCannotChangeLeaderboard() {
        var records = new SnakeRecords(directory.resolve("scores.properties"));
        assertThrows(IllegalArgumentException.class, () -> records.recordFinishedRound(new dev.gamecore.snake.SnakeGame(SnakeMode.CLASSIC, 1).snapshot()));
        assertThrows(IllegalArgumentException.class, () -> records.recordFinishedRound(finished(SnakeMode.CLASSIC, 10, 20)));
        assertThrows(IllegalArgumentException.class, () -> records.recordFinishedRound(finished(SnakeMode.CLASSIC, -10)));
        assertTrue(records.leaderboard(SnakeMode.CLASSIC).isEmpty()); assertEquals(0, records.best(SnakeMode.CLASSIC));
    }
    @Test void damagedVersionTwoRankingFallsBackAtomicallyAndPreservesFile() throws IOException {
        Path file = directory.resolve("scores.properties");
        String valid = "version=2\nclassic=50\nclassic.rank.count=1\nclassic.rank.0.player=1\nclassic.rank.0.score=50\n"
                + "obstacles=0\nobstacles.rank.count=0\nduo=0\nduo.rank.count=0\n";
        for (String invalid : new String[]{valid.replace("rank.count=1", "rank.count=6"),
                valid.replace("player=1", "player=2"), valid.replace("score=50", "score=60"), valid.replace("rank.count=0", "rank.count=oops")}) {
            Files.writeString(file, invalid); var records = new SnakeRecords(file);
            assertEquals(0, records.best(SnakeMode.CLASSIC)); assertTrue(records.leaderboard(SnakeMode.CLASSIC).isEmpty());
            assertFalse(records.notice().isBlank()); assertEquals(invalid, Files.readString(file));
        }
    }
    @Test void leaderboardWriteFailureRetainsRoundAndRetryDoesNotDuplicateIt() throws IOException {
        Path blocked = directory.resolve("blocked"); Files.writeString(blocked, "block");
        var records = new SnakeRecords(blocked.resolve("scores.properties"));
        records.recordFinishedRound(finished(SnakeMode.DUO, 20, 40));
        assertTrue(records.pendingSave()); assertEquals(2, records.leaderboard(SnakeMode.DUO).size());
        Files.delete(blocked); records.flush(); records.flush();
        assertEquals(2, new SnakeRecords(blocked.resolve("scores.properties")).leaderboard(SnakeMode.DUO).size());
    }
}
