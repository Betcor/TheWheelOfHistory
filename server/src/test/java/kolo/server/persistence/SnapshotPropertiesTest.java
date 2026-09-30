package kolo.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldState;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/** Властивості снапшотів на світах з довільним seed: запис → читання дає той самий стан і ті самі байти. */
class SnapshotPropertiesTest {

    @Property(tries = 15)
    void roundTripKeepsStateBytesAndHash(@ForAll long seed, @ForAll @IntRange(min = 1, max = 3) int players) {
        WorldState state = TestWorlds.state(seed, players, NpcShare.FEW);
        MapSnapshot map = MapSnapshot.of(state.map());
        StateSnapshot snapshot = StateSnapshot.of(state, map);

        MapSnapshot readMap = MapSnapshot.read(map.json());
        StateSnapshot read = StateSnapshot.read(snapshot.json(), readMap);

        assertThat(readMap.hash()).isEqualTo(map.hash());
        assertThat(read.state()).isEqualTo(state);
        assertThat(read.json()).isEqualTo(snapshot.json());
        assertThat(read.hash()).isEqualTo(snapshot.hash());
    }

    @Property(tries = 50)
    void turnIsPartOfHash(@ForAll @IntRange(min = 1, max = 10_000) int turn) {
        WorldState state = TestWorlds.small();
        MapSnapshot map = MapSnapshot.of(state.map());
        String before = StateSnapshot.of(state, map).hash();

        state.setTurn(turn);
        StateSnapshot after = StateSnapshot.of(state, map);

        assertThat(after.hash()).isNotEqualTo(before);
        assertThat(StateSnapshot.read(after.json(), map).state().turn()).isEqualTo(turn);
    }
}
