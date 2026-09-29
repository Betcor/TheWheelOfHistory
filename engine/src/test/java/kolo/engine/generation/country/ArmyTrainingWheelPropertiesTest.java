package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestHdi.gdp;
import static kolo.engine.generation.country.TestTraining.PACK;
import static kolo.engine.generation.country.TestTraining.development;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kolo.engine.content.TrainingLevelDef;
import kolo.engine.modifier.Modifier;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.Wheel;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/** Властивості колеса вишколу армії на довільних seed, перевагах, ВВП і військовій розвиненості. */
class ArmyTrainingWheelPropertiesTest {

    @Property
    void sameSeedGivesSameTraining(
            @ForAll long seed, @ForAll OutcomeTier gdpTier, @ForAll @IntRange(min = -3, max = 2) int military) {
        List<Modifier> modifiers = TestTraining.REGIME.modifiers();

        assertThat(ArmyTrainingWheel.generate(Rng.of(seed), PACK, modifiers, gdp(gdpTier), development(military)))
                .isEqualTo(
                        ArmyTrainingWheel.generate(Rng.of(seed), PACK, modifiers, gdp(gdpTier), development(military)));
    }

    @Property
    void resultIsConsistentWithRollAndAdvantage(
            @ForAll long seed,
            @ForAll @IntRange(min = -150, max = 150) int regime,
            @ForAll OutcomeTier gdpTier,
            @ForAll @IntRange(min = -3, max = 2) int military) {
        // Два внески: сума може виходити за межі переваги, колесо обрізає її.
        List<Modifier> modifiers =
                List.of(TestTraining.modifier("a", regime / 2), TestTraining.modifier("b", regime - regime / 2));

        StartArmyTraining training =
                ArmyTrainingWheel.generate(Rng.of(seed), PACK, modifiers, gdp(gdpTier), development(military));

        RollRecord roll = training.rolls().getFirst();
        int expected =
                regime + gdpTier.step() * TestTraining.GDP_ADVANTAGE + military * TestTraining.DEVELOPMENT_ADVANTAGE;
        assertThat(roll.advantage()).isEqualTo(Math.clamp(expected, -100, 100));
        assertThat(roll.sectors().stream().mapToInt(RolledSector::weightBp).sum())
                .isEqualTo(Wheel.TOTAL_BP);
        assertThat(roll.sectors().getFirst().weightBp()).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        assertThat(roll.sectors().getLast().weightBp()).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        assertThat(roll.resultSectorId()).isEqualTo(ArmyTrainingWheel.sectorId(training.level()));
        TrainingLevelDef level = PACK.trainingLevel(training.level());
        assertThat(training.combatModifier()).isEqualTo(level.combatModifier());
        assertThat(training.quality()).isEqualTo(level.quality());
    }
}
