package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestHdi.gdp;
import static kolo.engine.generation.country.TestTraining.IDS;
import static kolo.engine.generation.country.TestTraining.PACK;
import static kolo.engine.generation.country.TestTraining.REGIME;
import static kolo.engine.generation.country.TestTraining.WEIGHTS;
import static kolo.engine.generation.country.TestTraining.development;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kolo.engine.modifier.Modifier;
import kolo.engine.rng.Rng;
import kolo.engine.state.Training;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;

/**
 * Розподіл рівнів вишколу відповідає вагам контенту, зсунутим перевагою ладу, ВВП і військової розвиненості. Seed-и
 * фіксовані, тож тест детермінований; поріг — χ² для p = 0,001 і 4 ступенів свободи.
 */
class ArmyTrainingWheelDistributionTest {

    private static final int RUNS = 60_000;
    private static final double CHI_SQUARED_4_DOF = 18.47;

    @Test
    void withoutAdvantageLevelsFollowContentWeights() {
        long[] counts = levels(List.of(), gdp(OutcomeTier.PARTIAL), development(0));

        assertThat(chiSquared(counts, WEIGHTS)).isLessThan(CHI_SQUARED_4_DOF);
    }

    @Test
    void regimeRichGdpAndAdvancedMilitaryShiftLevelsUp() {
        // Лад +15, ВВП на рівні успіху +10, військо +1 ще +10: разом +35.
        long[] counts = levels(REGIME.modifiers(), gdp(OutcomeTier.SUCCESS), development(1));

        assertThat(chiSquared(counts, weights(35))).isLessThan(CHI_SQUARED_4_DOF);
        assertThat(counts[4]).isGreaterThan((long) RUNS * WEIGHTS[4] / Wheel.TOTAL_BP);
    }

    @Test
    void destituteGdpAndBackwardMilitaryShiftLevelsDown() {
        // ВВП на рівні критичного провалу −20, військо −3 ще −30: разом −50.
        long[] counts = levels(List.of(), gdp(OutcomeTier.CRIT_FAIL), development(-3));

        assertThat(chiSquared(counts, weights(-50))).isLessThan(CHI_SQUARED_4_DOF);
        assertThat(counts[0]).isGreaterThan((long) RUNS * WEIGHTS[0] / Wheel.TOTAL_BP);
    }

    private static int[] weights(int advantage) {
        return Wheel.applyAdvantage(
                        ArmyTrainingWheel.sectors(PACK),
                        advantage,
                        PACK.balance().wheel().strength(ArmyTrainingWheel.KIND))
                .stream()
                .mapToInt(Sector::weightBp)
                .toArray();
    }

    private static long[] levels(List<Modifier> modifiers, StartGdp gdp, StartDevelopment development) {
        long[] counts = new long[IDS.length];
        for (long seed = 0; seed < RUNS; seed++) {
            counts[
                    ArmyTrainingWheel.generate(Rng.of(seed), PACK, modifiers, gdp, development)
                                    .level()
                            - Training.MIN]++;
        }
        return counts;
    }

    private static double chiSquared(long[] counts, int[] weightsBp) {
        double chiSquared = 0;
        for (int i = 0; i < counts.length; i++) {
            double mean = (double) RUNS * weightsBp[i] / Wheel.TOTAL_BP;
            chiSquared += (counts[i] - mean) * (counts[i] - mean) / mean;
        }
        return chiSquared;
    }
}
