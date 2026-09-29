package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.generation.name.TestNames;
import kolo.engine.generation.religion.StartReligions;
import kolo.engine.generation.religion.WorldReligionsWheel;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/** Основний сценарій: після ладу держава отримує релігію світу або стає світською. */
class StateReligionWheelSmokeTest {

    @Test
    void stateGetsReligionAfterRegime() {
        Rng rng = Rng.of(1970);
        StartReligions world = WorldReligionsWheel.generate(rng.fork("religions"), TestNames.PACK, 12);
        Regime regime = RegimeWheel.generate(rng.fork("regime"), TestNames.PACK);

        StartStateReligion religion = StateReligionWheel.generate(
                rng.fork("state_religion"), TestNames.PACK, regime.tags(), world.religions());

        assertThat(religion.tags()).isNotEmpty();
        assertThat(religion.rolls()).hasSize(1);
        religion.religion()
                .ifPresent(
                        index -> assertThat(index).isLessThan(world.religions().size()));
    }
}
