package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestArmy.IDS;
import static kolo.engine.generation.country.TestArmy.PACK;
import static kolo.engine.generation.country.TestArmy.REGIME;
import static kolo.engine.generation.country.TestArmy.WEIGHTS;
import static kolo.engine.generation.country.TestHdi.gdp;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import kolo.engine.modifier.Modifier;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;

/**
 * Розподіл розмірів армії відповідає вагам контенту, зсунутим перевагою ладу й ВВП. Seed-и фіксовані, тож тест
 * детермінований; поріг — χ² для p = 0,001 і 4 ступенів свободи.
 */
class ArmySizeWheelDistributionTest {

    private static final int RUNS = 60_000;
    private static final double CHI_SQUARED_4_DOF = 18.47;

    @Test
    void withoutAdvantageLevelsFollowContentWeights() {
        long[] counts = levels(List.of(), gdp(OutcomeTier.PARTIAL));

        assertThat(chiSquared(counts, WEIGHTS)).isLessThan(CHI_SQUARED_4_DOF);
    }

    @Test
    void regimeAndRichGdpShiftSizesToAdjustedWeights() {
        // Лад +25, ВВП на рівні успіху +10: разом +35.
        long[] counts = levels(REGIME.modifiers(), gdp(OutcomeTier.SUCCESS));

        assertThat(chiSquared(counts, weights(35))).isLessThan(CHI_SQUARED_4_DOF);
        assertThat(counts[4]).isGreaterThan((long) RUNS * WEIGHTS[4] / Wheel.TOTAL_BP);
    }

    @Test
    void destituteGdpShiftsSizesDown() {
        long[] counts = levels(List.of(), gdp(OutcomeTier.CRIT_FAIL));

        assertThat(chiSquared(counts, weights(-20))).isLessThan(CHI_SQUARED_4_DOF);
        assertThat(counts[0]).isGreaterThan((long) RUNS * WEIGHTS[0] / Wheel.TOTAL_BP);
    }

    private static int[] weights(int advantage) {
        return Wheel.applyAdvantage(
                        ArmySizeWheel.sectors(PACK),
                        advantage,
                        PACK.balance().wheel().strength(ArmySizeWheel.KIND))
                .stream()
                .mapToInt(Sector::weightBp)
                .toArray();
    }

    private static long[] levels(List<Modifier> modifiers, StartGdp gdp) {
        long[] counts = new long[IDS.length];
        List<String> ids = Arrays.asList(IDS);
        for (long seed = 0; seed < RUNS; seed++) {
            counts[
                    ids.indexOf(ArmySizeWheel.generate(Rng.of(seed), PACK, modifiers, gdp)
                            .size()
                            .value())]++;
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
