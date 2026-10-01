package kolo.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.SaveFileException;
import kolo.engine.error.SaveVersionException;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Огляд файлу світу для списку збережень: без міграції, без карти, файл не змінюється. */
class WorldStoreSummaryTest {

    private static final WorldState STATE = TestWorlds.state(5, 2, NpcShare.FEW);
    private static final MapSnapshot MAP = MapSnapshot.of(STATE.map());
    private static final StateSnapshot INITIAL = StateSnapshot.of(STATE, MAP);
    private static final List<PlayerRecord> PLAYERS = List.of(
            new PlayerRecord(1, "Оля", "a".repeat(64), 0, true), new PlayerRecord(2, "Ігор", "b".repeat(64), 1, false));

    @TempDir
    Path dir;

    @Test
    void summaryDescribesTheLatestYear() {
        Path file = dir.resolve("світ-5" + WorldStore.EXTENSION);
        String key;
        try (WorldStore store =
                WorldStore.create(file, "Світ", MAP, INITIAL, PLAYERS, TestStores.CLOCK, Migrator.bundled())) {
            key = store.meta().key();
            store.saveTurn(StateSnapshot.of(next(STATE), MAP));
        }

        WorldSummary summary = WorldStore.summary(file);

        assertThat(summary)
                .isEqualTo(new WorldSummary(
                        file, Optional.of(key), 5, STATE.contentHash(), 1, TestStores.NOW, List.of("Оля", "Ігор")));
        assertThat(summary.name()).isEqualTo("світ-5");
    }

    @Test
    void summaryReadsAFileThatIsOpen() {
        Path file = dir.resolve("w" + WorldStore.EXTENSION);
        try (WorldStore store =
                WorldStore.create(file, "Світ", MAP, INITIAL, PLAYERS, TestStores.CLOCK, Migrator.bundled())) {
            store.saveTurn(StateSnapshot.of(next(STATE), MAP));

            assertThat(WorldStore.summary(file).lastTurn()).isEqualTo(1);
        }
    }

    @Test
    void olderFileIsDescribedWithoutMigration() throws Exception {
        Path file = dir.resolve("old" + WorldStore.EXTENSION);
        WorldStore.create(file, "Старий", MAP, INITIAL, PLAYERS, TestStores.CLOCK, Migrator.bundled())
                .close();
        TestStores.downgrade(file, 1);

        WorldSummary summary = WorldStore.summary(file);

        assertThat(summary.key()).isEmpty();
        assertThat(summary.players()).isEmpty();
        assertThat(summary.lastTurn()).isZero();
        try (Stream<Path> files = Files.list(dir)) {
            // Ні резервної копії, ні журналу: файл не мігрувався й не змінився.
            assertThat(files.map(p -> p.getFileName().toString())).containsExactly("old.koloworld");
        }
    }

    @Test
    void newerFileIsRejected() {
        Path file = dir.resolve("new" + WorldStore.EXTENSION);
        WorldStore.create(file, "Новий", MAP, INITIAL, PLAYERS, TestStores.CLOCK, Migrator.bundled())
                .close();

        assertThatThrownBy(() -> WorldStore.summary(file, Migrator.bundled(2)))
                .isInstanceOfSatisfying(
                        SaveVersionException.class,
                        e -> assertThat(e.details())
                                .containsEntry("version", Migrator.bundled().latest())
                                .containsEntry("supported", 2));
    }

    @Test
    void foreignOrMissingFilesAreSaveErrors() throws Exception {
        Path text = Files.writeString(dir.resolve("text" + WorldStore.EXTENSION), "не база");

        assertThatThrownBy(() -> WorldStore.summary(text))
                .isInstanceOfSatisfying(
                        SaveFileException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.SAVE_MALFORMED));
        assertThatThrownBy(() -> WorldStore.summary(dir.resolve("none" + WorldStore.EXTENSION)))
                .isInstanceOfSatisfying(
                        SaveFileException.class, e -> assertThat(e.details()).containsEntry("problem", "file_missing"));
    }

    private static WorldState next(WorldState state) {
        WorldState next = state.deepCopy();
        next.setTurn(state.turn() + 1);
        return next;
    }
}
