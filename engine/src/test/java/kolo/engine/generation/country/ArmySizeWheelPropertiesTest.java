package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestArmy.PACK;
import static kolo.engine.generation.country.TestHdi.gdp;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kolo.engine.content.ArmySizeDef;
import kolo.engine.modifier.Modifier;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.Wheel;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/** Властивості колеса розміру армії на довільних seed, перевагах і ВВП. */
class ArmySizeWheelPropertiesTest {

    @Property
    void sameSeedGivesSameArmySize(@ForAll long seed, @ForAll OutcomeTier gdpTier) {
        List<Modifier> modifiers = TestArmy.REGIME.modifiers();

        assertThat(ArmySizeWheel.generate(Rng.of(seed), PACK, modifiers, gdp(gdpTier)))
                .isEqualTo(ArmySizeWheel.generate(Rng.of(seed), PACK, modifiers, gdp(gdpTier)));
    }

    @Property
    void resultIsConsistentWithRollAndAdvantage(
            @ForAll long seed, @ForAll @IntRange(min = -150, max = 150) int regime, @ForAll OutcomeTier gdpTier) {
        // Два внески: сума може виходити за межі переваги, колесо обрізає її.
        List<Modifier> modifiers =
                List.of(TestArmy.modifier("a", regime / 2), TestArmy.modifier("b", regime - regime / 2));

        StartArmySize army = ArmySizeWheel.generate(Rng.of(seed), PACK, modifiers, gdp(gdpTier));

        RollRecord roll = army.rolls().getFirst();
        int expected = regime + gdpTier.step() * TestArmy.GDP_ADVANTAGE;
        assertThat(roll.advantage()).isEqualTo(Math.clamp(expected, -100, 100));
        assertThat(roll.sectors().stream().mapToInt(RolledSector::weightBp).sum())
                .isEqualTo(Wheel.TOTAL_BP);
        assertThat(roll.sectors().getFirst().weightBp()).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        assertThat(roll.sectors().getLast().weightBp()).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        assertThat(roll.resultSectorId()).isEqualTo(army.size().value());
        ArmySizeDef size = PACK.armySize(army.size()).orElseThrow();
        assertThat(army.shareBp()).isEqualTo(size.shareBp());
        assertThat(army.quality()).isEqualTo(size.quality());
    }
}
