package kolo.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldState;
import org.junit.jupiter.api.Test;

/** Основний сценарій збереження: новий світ → знімки карти й стану → читання → той самий світ. */
class SnapshotSmokeTest {

    @Test
    void newWorldSurvivesSaveAndLoad() {
        WorldState state = TestWorlds.state(42, 2, NpcShare.NORMAL);
        MapSnapshot map = MapSnapshot.of(state.map());
        StateSnapshot saved = StateSnapshot.of(state, map);

        StateSnapshot loaded = StateSnapshot.read(saved.json(), MapSnapshot.read(map.json()));

        assertThat(loaded.state()).isEqualTo(state);
        assertThat(loaded.hash()).isEqualTo(saved.hash());
    }
}
