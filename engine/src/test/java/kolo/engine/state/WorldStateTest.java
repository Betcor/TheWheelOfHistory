package kolo.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.TreeMap;
import kolo.engine.error.ValidationException;
import org.junit.jupiter.api.Test;

class WorldStateTest {

    @Test
    void turnZeroIsYear1970() {
        assertThat(WorldState.year(0)).isEqualTo(1970);
        assertThat(WorldState.year(31)).isEqualTo(2001);
    }

    @Test
    void deepCopyIsEqualButIndependent() {
        WorldState original = TestWorldStates.state();
        WorldState copy = original.deepCopy();

        assertThat(copy).isEqualTo(original).isNotSameAs(original);
        assertThat(copy.map()).isSameAs(original.map());
        copy.setTurn(1);
        copy.takeId();
        copy.provinces().get(TestWorldStates.UNCLAIMED).setOwner(TestWorldStates.SECOND);
        copy.countries().get(TestWorldStates.FIRST).tags().add("golden_age");
        copy.people().get(TestWorldStates.LEADER).setAlive(false);
        copy.religions().remove(TestWorldStates.FAITH);

        assertThat(original).isEqualTo(TestWorldStates.state());
        assertThat(copy).isNotEqualTo(original);
    }

    @Test
    void takeIdAdvancesTheSharedCounter() {
        WorldState state = TestWorldStates.state();

        assertThat(state.takeId()).isEqualTo(4);
        assertThat(state.takeId()).isEqualTo(5);
        assertThat(state.nextIdSeq()).isEqualTo(6);
    }

    @Test
    void rejectsNegativeTurnAndBlankContentHash() {
        WorldState state = TestWorldStates.state();

        assertThatThrownBy(() -> state.setTurn(-1)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new WorldState(
                        WorldState.SCHEMA_VERSION,
                        " ",
                        1,
                        0,
                        0,
                        state.map(),
                        new TreeMap<>(),
                        new TreeMap<>(),
                        new TreeMap<>(),
                        new TreeMap<>(),
                        List.of()))
                .isInstanceOf(ValidationException.class);
    }
}
