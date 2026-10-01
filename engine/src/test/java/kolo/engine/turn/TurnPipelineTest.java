package kolo.engine.turn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kolo.engine.content.ContentPack;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.generation.name.TestNames;
import kolo.engine.state.CountryId;
import kolo.engine.state.TestWorldStates;
import kolo.engine.state.WorldInvariants;
import kolo.engine.state.WorldState;
import org.junit.jupiter.api.Test;

/** Порожній рік: наступний номер року, решта стану без змін, вхідний стан не чіпається. */
class TurnPipelineTest {

    private static final ContentPack CONTENT = TestNames.pack(5);

    @Test
    void emptyYearAdvancesTheTurnOnly() {
        WorldState state = TestWorldStates.state();

        WorldState next = TurnPipeline.resolve(state, CONTENT).newState();

        assertThat(next.turn()).isEqualTo(state.turn() + 1);
        WorldState expected = TestWorldStates.state();
        expected.setTurn(state.turn() + 1);
        assertThat(next).isEqualTo(expected);
    }

    @Test
    void inputStateIsNotChanged() {
        WorldState state = TestWorldStates.state();

        WorldState next = TurnPipeline.resolve(state, CONTENT).newState();

        assertThat(state).isEqualTo(TestWorldStates.state());
        assertThat(next).isNotSameAs(state);
        assertThat(next.countries()).isNotSameAs(state.countries());
    }

    @Test
    void sameInputSameOutput() {
        WorldState first =
                TurnPipeline.resolve(TestWorldStates.state(), CONTENT).newState();
        WorldState second =
                TurnPipeline.resolve(TestWorldStates.state(), CONTENT).newState();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void consecutiveYearsKeepInvariants() {
        WorldState state = TestWorldStates.state();
        for (int year = 0; year < 50; year++) {
            state = TurnPipeline.resolve(state, CONTENT).newState();
            WorldInvariants.check(state);
        }
        assertThat(state.turn()).isEqualTo(50);
    }

    @Test
    void brokenStateIsAnInvariantViolation() {
        WorldState state = TestWorldStates.state();
        state.provinces().get(TestWorldStates.UNCLAIMED).setOwner(CountryId.of(9));

        assertThatThrownBy(() -> TurnPipeline.resolve(state, CONTENT)).isInstanceOf(InvariantViolationException.class);
    }
}
