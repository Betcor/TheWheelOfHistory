package kolo.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ключ світу й переписування гравців у файлі світу: новий файл, міграція файлу без ключа, роздача місць. */
class WorldStoreKeyTest {

    private static final WorldState STATE = TestWorlds.state(3, 2, NpcShare.FEW);
    private static final MapSnapshot MAP = MapSnapshot.of(STATE.map());
    private static final StateSnapshot INITIAL = StateSnapshot.of(STATE, MAP);
    private static final List<PlayerRecord> PLAYERS = List.of(
            new PlayerRecord(1, "Оля", "a".repeat(64), 0, true), new PlayerRecord(4, "Ігор", "b".repeat(64), 1, false));

    @TempDir
    Path dir;

    @Test
    void newKeysAreRandomHex() {
        String first = WorldStore.newKey();

        assertThat(first).matches("[0-9a-f]{32}");
        assertThat(WorldStore.newKey()).isNotEqualTo(first);
    }

    @Test
    void givenKeyIsKeptInTheFile() {
        Path file = dir.resolve("w" + WorldStore.EXTENSION);
        WorldStore.create(file, "Світ", "k".repeat(32), MAP, INITIAL, PLAYERS, TestStores.CLOCK, Migrator.bundled())
                .close();

        try (WorldStore store = TestStores.open(file)) {
            assertThat(store.meta().key()).isEqualTo("k".repeat(32));
        }
    }

    @Test
    void fileWithoutAKeyGetsOneOnceAndKeepsItsPlayers() throws Exception {
        Path file = dir.resolve("old" + WorldStore.EXTENSION);
        WorldStore.create(file, "Старий", MAP, INITIAL, PLAYERS, TestStores.CLOCK, Migrator.bundled())
                .close();
        TestStores.downgrade(file, 2);

        String key;
        try (WorldStore store = TestStores.open(file)) {
            key = store.meta().key();
            assertThat(key).matches("[0-9a-f]{32}");
            assertThat(store.players()).extracting(SavedPlayer::player).isEqualTo(PLAYERS);
        }
        try (WorldStore again = TestStores.open(file)) {
            assertThat(again.meta().key()).isEqualTo(key);
        }
        try (Stream<Path> files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString())).contains("old.koloworld.v2.bak");
        }
    }

    @Test
    void updatedPlayersReplaceNicknamesTokensAndHost() {
        Path file = dir.resolve("w" + WorldStore.EXTENSION);
        try (WorldStore store =
                WorldStore.create(file, "Світ", MAP, INITIAL, PLAYERS, TestStores.CLOCK, Migrator.bundled())) {
            store.updatePlayers(List.of(new PlayerRecord(4, "Марко", "c".repeat(64), 1, true)));
        }

        try (WorldStore store = TestStores.open(file)) {
            assertThat(store.players())
                    .extracting(SavedPlayer::player)
                    .containsExactly(
                            new PlayerRecord(1, "Оля", "a".repeat(64), 0, false),
                            new PlayerRecord(4, "Марко", "c".repeat(64), 1, true));
        }
    }

    @Test
    void updateOfAnUnknownPlayerChangesNothing() {
        Path file = dir.resolve("w" + WorldStore.EXTENSION);
        try (WorldStore store =
                WorldStore.create(file, "Світ", MAP, INITIAL, PLAYERS, TestStores.CLOCK, Migrator.bundled())) {
            List<PlayerRecord> wrongCountry = List.of(
                    new PlayerRecord(1, "Нова", "d".repeat(64), 0, true),
                    new PlayerRecord(4, "Марко", "c".repeat(64), 0, false));

            assertThatThrownBy(() -> store.updatePlayers(wrongCountry)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> store.updatePlayers(List.of(PLAYERS.get(0), PLAYERS.get(0))))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> store.updatePlayers(
                            List.of(PLAYERS.get(0), new PlayerRecord(4, "Ігор", "b".repeat(64), 1, true))))
                    .isInstanceOf(IllegalArgumentException.class);
            // Транзакцію відкочено: перший гравець лишився, яким був.
            assertThat(store.players()).extracting(SavedPlayer::player).isEqualTo(PLAYERS);
        }
    }
}
