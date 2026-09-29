package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestGdp.PACK;
import static kolo.engine.generation.country.TestGdp.development;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kolo.engine.content.GdpLevelDef;
import kolo.engine.modifier.Modifier;
import kolo.engine.rng.Rng;
import kolo.engine.state.Development;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.Wheel;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/** Властивості колеса ВВП на довільних seed, перевагах і розвиненості. */
class GdpWheelPropertiesTest {

    @Property
    void sameSeedGivesSameGdp(
            @ForAll long seed,
            @ForAll @IntRange(min = Development.MIN, max = Development.MAX) int economy,
            @ForAll @IntRange(min = Development.MIN, max = Development.MAX) int society) {
        List<Modifier> modifiers = TestGdp.REGIME.modifiers();

        assertThat(GdpWheel.generate(Rng.of(seed), PACK, modifiers, development(economy, society)))
                .isEqualTo(GdpWheel.generate(Rng.of(seed), PACK, modifiers, development(economy, society)));
    }

    @Property
    void resultIsConsistentWithRollAndAdvantage(
            @ForAll long seed,
            @ForAll @IntRange(min = -150, max = 150) int regime,
            @ForAll @IntRange(min = Development.MIN, max = Development.MAX) int economy,
            @ForAll @IntRange(min = Development.MIN, max = Development.MAX) int society) {
        // Два внески: сума може виходити за межі переваги, колесо обрізає її.
        List<Modifier> modifiers =
                List.of(TestGdp.modifier("a", regime / 2), TestGdp.modifier("b", regime - regime / 2));

        StartGdp gdp = GdpWheel.generate(Rng.of(seed), PACK, modifiers, development(economy, society));

        RollRecord roll = gdp.rolls().getFirst();
        int expected = regime + (economy + society) * TestGdp.DEVELOPMENT_ADVANTAGE;
        assertThat(roll.advantage()).isEqualTo(Math.clamp(expected, -100, 100));
        assertThat(roll.sectors().stream().mapToInt(RolledSector::weightBp).sum())
                .isEqualTo(Wheel.TOTAL_BP);
        assertThat(roll.sectors().getFirst().weightBp()).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        assertThat(roll.sectors().getLast().weightBp()).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        assertThat(roll.resultSectorId()).isEqualTo(gdp.level().value());
        GdpLevelDef level = PACK.gdpLevel(gdp.level()).orElseThrow();
        assertThat(gdp.perCapita()).isEqualTo(level.perCapita());
        assertThat(gdp.quality()).isEqualTo(level.quality());
    }
}
