package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestHdi.PACK;
import static kolo.engine.generation.country.TestHdi.gdp;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kolo.engine.content.HdiLevelDef;
import kolo.engine.modifier.Modifier;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.Wheel;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/** Властивості колеса ІЛР на довільних seed, перевагах і ВВП. */
class HdiWheelPropertiesTest {

    @Property
    void sameSeedGivesSameHdi(@ForAll long seed, @ForAll OutcomeTier gdpTier) {
        List<Modifier> modifiers = TestHdi.REGIME.modifiers();

        assertThat(HdiWheel.generate(Rng.of(seed), PACK, modifiers, gdp(gdpTier)))
                .isEqualTo(HdiWheel.generate(Rng.of(seed), PACK, modifiers, gdp(gdpTier)));
    }

    @Property
    void resultIsConsistentWithRollAndAdvantage(
            @ForAll long seed, @ForAll @IntRange(min = -150, max = 150) int regime, @ForAll OutcomeTier gdpTier) {
        // Два внески: сума може виходити за межі переваги, колесо обрізає її.
        List<Modifier> modifiers =
                List.of(TestHdi.modifier("a", regime / 2), TestHdi.modifier("b", regime - regime / 2));

        StartHdi hdi = HdiWheel.generate(Rng.of(seed), PACK, modifiers, gdp(gdpTier));

        RollRecord roll = hdi.rolls().getFirst();
        int expected = regime + gdpTier.step() * TestHdi.GDP_ADVANTAGE;
        assertThat(roll.advantage()).isEqualTo(Math.clamp(expected, -100, 100));
        assertThat(roll.sectors().stream().mapToInt(RolledSector::weightBp).sum())
                .isEqualTo(Wheel.TOTAL_BP);
        assertThat(roll.sectors().getFirst().weightBp()).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        assertThat(roll.sectors().getLast().weightBp()).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        assertThat(roll.resultSectorId()).isEqualTo(hdi.level().value());
        HdiLevelDef level = PACK.hdiLevel(hdi.level()).orElseThrow();
        assertThat(hdi.hdi()).isEqualTo(level.hdi());
        assertThat(hdi.quality()).isEqualTo(level.quality());
    }
}
