package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/** Основний сценарій: лад обирається, і його мітки ведуть передісторію. */
class RegimeWheelSmokeTest {

    @Test
    void regimeFeedsBackstory() {
        Rng rng = Rng.of(1970);
        Regime regime = RegimeWheel.generate(rng.fork("regime"), TestRegime.PACK);
        Backstory backstory = BackstoryWheel.generate(rng.fork("backstory"), TestRegime.PACK, regime.tags(), List.of());

        assertThat(regime.rolls()).hasSize(2);
        assertThat(backstory.entries()).isNotEmpty();
        assertThat(backstory.tags()).containsAll(regime.tags());
    }
}
