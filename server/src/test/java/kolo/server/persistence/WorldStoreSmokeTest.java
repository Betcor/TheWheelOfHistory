package kolo.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Основний сценарій файлу світу: новий світ → файл → рік → закрити → відкрити → продовжити з того самого стану. */
class WorldStoreSmokeTest {

    @Test
    void newWorldSurvivesFileAndYear(@TempDir Path dir) {
        WorldState state = TestWorlds.state(42, 2, NpcShare.NORMAL);
        MapSnapshot map = MapSnapshot.of(state.map());
        Path file = dir.resolve("Новий світ" + WorldStore.EXTENSION);
        WorldState year = state.deepCopy();
        year.setTurn(1);
        StateSnapshot saved = StateSnapshot.of(year, map);

        try (WorldStore store = WorldStore.create(file, "Новий світ", map, StateSnapshot.of(state, map))) {
            store.saveTurn(saved);
        }
        try (WorldStore store = WorldStore.open(file)) {
            StateSnapshot loaded = store.loadLatest();

            assertThat(store.meta().name()).isEqualTo("Новий світ");
            assertThat(loaded.state()).isEqualTo(year);
            assertThat(loaded.hash()).isEqualTo(saved.hash());
        }
    }
}
