package kolo.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.SaveFileException;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Гравці у файлі світу: запис разом зі світом, читання, час останнього візиту й міграція старого файлу. */
class WorldStorePlayersTest {

    private static final WorldState STATE = TestWorlds.state(3, 2, NpcShare.FEW);
    private static final MapSnapshot MAP = MapSnapshot.of(STATE.map());
    private static final StateSnapshot INITIAL = StateSnapshot.of(STATE, MAP);
    private static final List<PlayerRecord> PLAYERS = List.of(
            new PlayerRecord(1, "Оля", "a".repeat(64), 0, true), new PlayerRecord(4, "Ігор", "b".repeat(64), 1, false));

    @TempDir
    Path dir;

    @Test
    void playersAreSavedWithTheWorld() {
        Path file = dir.resolve("w" + WorldStore.EXTENSION);
        WorldStore.create(file, "Світ", MAP, INITIAL, PLAYERS, TestStores.CLOCK, Migrator.bundled())
                .close();

        try (WorldStore store = TestStores.open(file)) {
            assertThat(store.players())
                    .containsExactly(
                            new SavedPlayer(PLAYERS.get(0), TestStores.NOW),
                            new SavedPlayer(PLAYERS.get(1), TestStores.NOW));
        }
    }

    @Test
    void worldWithoutPlayersHasAnEmptyList() {
        try (WorldStore store = TestStores.create(dir.resolve("w" + WorldStore.EXTENSION), INITIAL, MAP)) {
            assertThat(store.players()).isEmpty();
        }
    }

    @Test
    void lastSeenIsUpdated() {
        Path file = dir.resolve("w" + WorldStore.EXTENSION);
        Instant later = TestStores.NOW.plusSeconds(3600);
        WorldStore.create(file, "Світ", MAP, INITIAL, PLAYERS, TestStores.CLOCK, Migrator.bundled())
                .close();

        try (WorldStore store = WorldStore.open(file, Clock.fixed(later, ZoneOffset.UTC), Migrator.bundled())) {
            store.markSeen(4);

            assertThat(store.players()).extracting(SavedPlayer::lastSeenAt).containsExactly(TestStores.NOW, later);
            assertThatThrownBy(() -> store.markSeen(2)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void inconsistentPlayersAreRejected() {
        Path file = dir.resolve("w" + WorldStore.EXTENSION);
        PlayerRecord first = PLAYERS.getFirst();

        assertThatThrownBy(() -> create(file, List.of(first, new PlayerRecord(1, "Б", "c", 1, false))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(file, List.of(first, new PlayerRecord(2, "Б", "c", 0, false))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(file, List.of(first, new PlayerRecord(2, "Б", "c", 1, true))))
                .isInstanceOf(IllegalArgumentException.class);
        // Держава 2 — NPC: гравця в неї немає.
        assertThatThrownBy(() -> create(file, List.of(new PlayerRecord(1, "Оля", "c", 2, true))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(file).doesNotExist();
    }

    @Test
    void damagedPlayerRowIsMalformed() {
        Path file = dir.resolve("w" + WorldStore.EXTENSION);
        create(file, PLAYERS).close();
        TestStores.sql(file, "UPDATE players SET nickname = ' ' WHERE id = 4");

        try (WorldStore store = TestStores.open(file)) {
            assertThatThrownBy(store::players).isInstanceOfSatisfying(SaveFileException.class, e -> {
                assertThat(e.code()).isEqualTo(ErrorCode.SAVE_MALFORMED);
                assertThat(e.details()).containsEntry("location", "players[4]");
            });
        }
    }

    @Test
    void fileOfTheFirstSchemaGetsThePlayersTable() throws Exception {
        Path file = dir.resolve("old" + WorldStore.EXTENSION);
        WorldStore.create(file, "Старий", MAP, INITIAL, TestStores.CLOCK, Migrator.bundled(1))
                .close();

        try (WorldStore store = TestStores.open(file)) {
            assertThat(store.players()).isEmpty();
            assertThat(store.lastTurn()).isZero();
        }
        try (Stream<Path> files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString())).contains("old.koloworld.v1.bak");
        }
    }

    private static WorldStore create(Path file, List<PlayerRecord> players) {
        return WorldStore.create(file, "Світ", MAP, INITIAL, players, TestStores.CLOCK, Migrator.bundled());
    }
}
