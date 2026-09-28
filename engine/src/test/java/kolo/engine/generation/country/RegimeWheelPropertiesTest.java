package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestRegime.PACK;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.TreeSet;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.Wheel;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/** Властивості колеса ладу на довільних seed. */
class RegimeWheelPropertiesTest {

    @Property
    void sameSeedGivesSameRegime(@ForAll long seed) {
        assertThat(RegimeWheel.generate(Rng.of(seed), PACK)).isEqualTo(RegimeWheel.generate(Rng.of(seed), PACK));
    }

    @Property
    void regimeIsConsistentWithItsRolls(@ForAll long seed) {
        Regime regime = RegimeWheel.generate(Rng.of(seed), PACK);

        assertThat(regime.ideology().subIdeologies()).contains(regime.subIdeology());
        assertThat(regime.rolls()).hasSize(2);
        assertThat(regime.rolls().get(0).resultSectorId())
                .isEqualTo(regime.ideology().id().value());
        assertThat(regime.rolls().get(1).resultSectorId())
                .isEqualTo(regime.subIdeology().id().value());
        assertThat(regime.rolls()).allSatisfy(roll -> assertThat(roll.roll()).isBetween(0, Wheel.TOTAL_BP - 1));

        TreeSet<String> expected = new TreeSet<>(regime.ideology().tags());
        expected.addAll(regime.subIdeology().tags());
        assertThat(regime.tags()).containsExactlyElementsOf(expected);
    }
}
