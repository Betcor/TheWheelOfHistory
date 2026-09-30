package kolo.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import kolo.engine.state.Country;
import kolo.engine.state.WorldState;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/** Властивості файлу світу: будь-яка послідовність років після перевідкриття читається з тими самими хешами. */
class WorldStorePropertiesTest {

    private static final WorldState STATE = TestWorlds.small();
    private static final MapSnapshot MAP = MapSnapshot.of(STATE.map());

    @Property(tries = 20)
    void everySavedTurnLoadsWithItsHash(
            @ForAll @Size(min = 1, max = 6) List<@IntRange(min = 1, max = 30) Integer> steps,
            @ForAll @IntRange(min = 0, max = 3) int tokens) {
        Path dir = temporaryDirectory();
        try {
            Path file = dir.resolve("w.koloworld");
            List<StateSnapshot> saved = new ArrayList<>();
            saved.add(StateSnapshot.of(STATE, MAP));
            try (WorldStore store = TestStores.create(file, saved.getFirst(), MAP)) {
                WorldState state = STATE.deepCopy();
                for (int step : steps) {
                    state = state.deepCopy();
                    state.setTurn(state.turn() + step);
                    Country country = state.countries().firstEntry().getValue();
                    country.setFateTokens((country.fateTokens() + tokens) % 4);
                    country.tags().add("year_" + state.turn());
                    StateSnapshot snapshot = StateSnapshot.of(state, MAP);
                    store.saveTurn(snapshot);
                    saved.add(snapshot);
                }
            }
            try (WorldStore store = TestStores.open(file)) {
                assertThat(store.lastTurn()).isEqualTo(saved.getLast().state().turn());
                assertThat(store.turns())
                        .extracting(SavedTurn::stateHash)
                        .containsExactlyElementsOf(
                                saved.stream().map(StateSnapshot::hash).toList());
                for (StateSnapshot snapshot : saved) {
                    StateSnapshot loaded =
                            store.loadState(snapshot.state().turn()).orElseThrow();
                    assertThat(loaded.hash()).isEqualTo(snapshot.hash());
                    assertThat(loaded.state()).isEqualTo(snapshot.state());
                }
            }
        } finally {
            deleteRecursively(dir);
        }
    }

    // jqwik не підтримує @TempDir у властивостях.
    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("kolo-world-store");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
