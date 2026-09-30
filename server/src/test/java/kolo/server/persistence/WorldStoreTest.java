package kolo.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.SaveFileException;
import kolo.engine.error.SaveVersionException;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Country;
import kolo.engine.state.GameMap;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldStoreTest {

    private static final WorldState STATE = TestWorlds.small();
    private static final MapSnapshot MAP = MapSnapshot.of(STATE.map());
    private static final StateSnapshot INITIAL = StateSnapshot.of(STATE, MAP);

    @TempDir
    Path dir;

    @Test
    void createdFileKeepsMetaMapAndInitialState() {
        Path file = dir.resolve("світ" + WorldStore.EXTENSION);
        try (WorldStore store = TestStores.create(file, INITIAL, MAP)) {
            assertThat(store.file()).isEqualTo(file);
            assertThat(store.meta())
                    .isEqualTo(new WorldMeta(
                            "Тестовий світ", STATE.seed(), STATE.contentHash(), MAP.hash(), TestStores.NOW));
            assertThat(store.map().hash()).isEqualTo(MAP.hash());
            assertThat(store.lastTurn()).isEqualTo(STATE.turn());
            assertThat(store.turns())
                    .containsExactly(new SavedTurn(STATE.turn(), INITIAL.hash(), TestStores.NOW, true));
            assertThat(store.loadLatest().state()).isEqualTo(STATE);
        }
        try (WorldStore reopened = TestStores.open(file)) {
            assertThat(reopened.meta().mapHash()).isEqualTo(MAP.hash());
            assertThat(reopened.loadLatest().hash()).isEqualTo(INITIAL.hash());
        }
    }

    @Test
    void closedFileIsASingleFile() throws IOException {
        Path file = dir.resolve("w.koloworld");
        TestStores.create(file, INITIAL, MAP).close();

        assertThat(files()).containsExactly(file);
    }

    @Test
    void savedTurnsLoadBack() {
        Path file = dir.resolve("w.koloworld");
        StateSnapshot second = next(INITIAL, 2, state -> first(state).setFateTokens(2));
        StateSnapshot third = next(second, 3, state -> first(state).tags().add("ruins"));
        try (WorldStore store = TestStores.create(file, INITIAL, MAP)) {
            store.saveTurn(second);
            store.saveTurn(third);

            assertThat(store.lastTurn()).isEqualTo(3);
        }
        try (WorldStore store = TestStores.open(file)) {
            assertThat(store.lastTurn()).isEqualTo(3);
            assertThat(store.turns()).extracting(SavedTurn::turn).containsExactly(0, 2, 3);
            assertThat(store.turns())
                    .extracting(SavedTurn::stateHash)
                    .containsExactly(INITIAL.hash(), second.hash(), third.hash());
            assertThat(store.loadState(2).orElseThrow().state()).isEqualTo(second.state());
            assertThat(store.loadState(1)).isEmpty();
            assertThat(store.loadLatest().state()).isEqualTo(third.state());
        }
    }

    @Test
    void turnMustAdvance() {
        try (WorldStore store = TestStores.create(dir.resolve("w.koloworld"), INITIAL, MAP)) {
            store.saveTurn(next(INITIAL, 1, state -> {}));

            assertThatThrownBy(() -> store.saveTurn(next(INITIAL, 1, state -> {})))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> store.saveTurn(INITIAL)).isInstanceOf(IllegalArgumentException.class);
            assertThat(store.lastTurn()).isEqualTo(1);
            assertThat(store.turns()).hasSize(2);
        }
    }

    @Test
    void stateOfAnotherWorldIsRejected() {
        WorldState other = TestWorlds.state(2, 1, NpcShare.FEW);
        other.setTurn(5);
        GameMap map = STATE.map();
        MapSnapshot otherMap = MapSnapshot.of(new GameMap(map.width() + 1, map.height(), map.tiles(), map.seaZones()));
        try (WorldStore store = TestStores.create(dir.resolve("w.koloworld"), INITIAL, MAP)) {
            assertThatThrownBy(() -> store.saveTurn(StateSnapshot.of(other, MapSnapshot.of(other.map()))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> TestStores.create(dir.resolve("x.koloworld"), INITIAL, otherMap))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(dir.resolve("x.koloworld")).doesNotExist();
    }

    @Test
    void blankNameIsRejected() {
        Path file = dir.resolve("w.koloworld");

        assertThatThrownBy(() -> WorldStore.create(file, " ", MAP, INITIAL)).isInstanceOf(ValidationException.class);
        assertThat(file).doesNotExist();
    }

    @Test
    void existingFileIsNotOverwritten() throws IOException {
        Path file = dir.resolve("w.koloworld");
        Files.writeString(file, "чужі дані");

        assertFileError(() -> TestStores.create(file, INITIAL, MAP), "create", "file_exists");
        assertThat(Files.readString(file)).isEqualTo("чужі дані");
        assertThat(files()).containsExactly(file);
    }

    @Test
    void leftoverTemporaryFileIsReplaced() throws IOException {
        Path file = dir.resolve("w.koloworld");
        Files.writeString(dir.resolve(".w.koloworld.creating"), "залишок невдалої спроби");

        TestStores.create(file, INITIAL, MAP).close();

        assertThat(files()).containsExactly(file);
    }

    @Test
    void missingFileIsFileError() {
        assertFileError(() -> TestStores.open(dir.resolve("нема.koloworld")), "open", "file_missing");
        assertThat(files()).isEmpty();
    }

    @Test
    void foreignFilesAreNotWorlds() throws IOException {
        Path text = dir.resolve("text.koloworld");
        Files.writeString(text, "це не база даних ".repeat(20));
        Path empty = dir.resolve("empty.koloworld");
        Files.createFile(empty);
        Path foreign = dir.resolve("foreign.koloworld");
        TestStores.sql(foreign, "CREATE TABLE notes (text TEXT)");

        for (Path file : List.of(text, empty, foreign)) {
            assertMalformed(
                    () -> TestStores.open(file), "file", file.getFileName().toString(), "not_a_world_file");
        }
    }

    @Test
    void newerFileIsRejected() {
        Path file = created();
        TestStores.sql(file, "INSERT INTO schema_migrations (version, name, applied_at) VALUES (99, 'future', 'x')");

        assertThatThrownBy(() -> TestStores.open(file))
                .isInstanceOfSatisfying(
                        SaveVersionException.class,
                        e -> assertThat(e.details())
                                .containsEntry("part", "file")
                                .containsEntry("version", 99)
                                .containsEntry("supported", Migrator.bundled().latest()));
    }

    @Test
    void tamperedStateHashIsMalformed() {
        Path file = created();
        TestStores.sql(file, "UPDATE turns SET state_hash = 'ab'");

        try (WorldStore store = TestStores.open(file)) {
            assertMalformed(store::loadLatest, "state", "snapshots[0]", "state_hash_mismatch");
        }
    }

    @Test
    void damagedSnapshotIsMalformed() {
        Path file = created();
        TestStores.sql(file, "UPDATE snapshots SET state_gz = ?", (Object) new byte[] {1, 2, 3});

        try (WorldStore store = TestStores.open(file)) {
            assertMalformed(() -> store.loadState(0), "state", "snapshots[0]", "bad_gzip");
        }
    }

    @Test
    void snapshotOfAnotherTurnIsMalformed() {
        Path file = created();
        StateSnapshot later = next(INITIAL, 4, state -> {});
        TestStores.sql(file, "UPDATE turns SET state_hash = ?", later.hash());
        TestStores.sql(file, "UPDATE snapshots SET state_gz = ?", (Object) Gzip.compress(later.json()));

        try (WorldStore store = TestStores.open(file)) {
            assertMalformed(store::loadLatest, "state", "snapshots[0]", "turn_mismatch");
        }
    }

    @Test
    void mapOfAnotherWorldIsMalformed() {
        Path file = created();
        TestStores.sql(file, "UPDATE world_meta SET map_hash = 'ab'");

        assertMalformed(() -> TestStores.open(file), "map", "world_map", "map_hash_mismatch");
    }

    @Test
    void missingRowsAreMalformed() {
        Path noSnapshots = created();
        TestStores.sql(noSnapshots, "DELETE FROM snapshots");
        Path noMeta = created("m.koloworld");
        TestStores.sql(noMeta, "DELETE FROM world_meta");
        Path noTurns = created("t.koloworld");
        TestStores.sql(noTurns, "DELETE FROM snapshots");
        TestStores.sql(noTurns, "DELETE FROM turns");

        try (WorldStore store = TestStores.open(noSnapshots)) {
            assertThat(store.turns()).containsExactly(new SavedTurn(0, INITIAL.hash(), TestStores.NOW, false));
            assertMalformed(store::loadLatest, "file", "snapshots", "no_snapshots");
        }
        assertMalformed(() -> TestStores.open(noMeta), "file", "world_meta", "missing_row");
        assertMalformed(() -> TestStores.open(noTurns), "file", "turns", "no_turns");
    }

    @Test
    void closedStoreCannotBeUsed() {
        WorldStore store = TestStores.create(dir.resolve("w.koloworld"), INITIAL, MAP);
        store.close();
        store.close();

        assertThatThrownBy(store::loadLatest).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.saveTurn(next(INITIAL, 1, state -> {})))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- Допоміжне ----

    private Path created() {
        return created("w.koloworld");
    }

    private Path created(String name) {
        Path file = dir.resolve(name);
        TestStores.create(file, INITIAL, MAP).close();
        return file;
    }

    private static StateSnapshot next(StateSnapshot previous, int turn, Consumer<WorldState> change) {
        WorldState state = previous.state().deepCopy();
        state.setTurn(turn);
        change.accept(state);
        return StateSnapshot.of(state, MAP);
    }

    private static Country first(WorldState state) {
        return state.countries().firstEntry().getValue();
    }

    private List<Path> files() {
        try (Stream<Path> files = Files.list(dir)) {
            return files.sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void assertFileError(Runnable action, String operation, String problem) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(SaveFileException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.SAVE_FILE_ERROR);
            assertThat(e.details()).containsEntry("operation", operation).containsEntry("problem", problem);
        });
    }

    private static void assertMalformed(Runnable action, String part, String location, String problem) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(SaveFileException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.SAVE_MALFORMED);
            assertThat(e.details())
                    .containsEntry("part", part)
                    .containsEntry("location", location)
                    .containsEntry("problem", problem);
        });
    }
}
