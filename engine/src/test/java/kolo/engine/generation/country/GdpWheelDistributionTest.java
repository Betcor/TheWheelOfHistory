package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestGdp.IDS;
import static kolo.engine.generation.country.TestGdp.PACK;
import static kolo.engine.generation.country.TestGdp.REGIME;
import static kolo.engine.generation.country.TestGdp.WEIGHTS;
import static kolo.engine.generation.country.TestGdp.development;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import kolo.engine.modifier.Modifier;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;

/**
 * Розподіл рівнів ВВП відповідає вагам контенту, зсунутим перевагою ладу й розвиненості. Seed-и фіксовані, тож тест
 * детермінований; поріг — χ² для p = 0,001 і 4 ступенів свободи.
 */
class GdpWheelDistributionTest {

    private static final int RUNS = 60_000;
    private static final double CHI_SQUARED_4_DOF = 18.47;

    @Test
    void withoutAdvantageLevelsFollowContentWeights() {
        long[] counts = levels(List.of(), development(0, 0));

        assertThat(chiSquared(counts, WEIGHTS)).isLessThan(CHI_SQUARED_4_DOF);
    }

    @Test
    void regimeAndDevelopmentShiftLevelsToAdjustedWeights() {
        // Лад +30, економіка +1 → +10, суспільство −2 → −20: разом +20.
        long[] counts = levels(REGIME.modifiers(), development(1, -2));
        int[] adjusted = weights(20);

        assertThat(chiSquared(counts, adjusted)).isLessThan(CHI_SQUARED_4_DOF);
        assertThat(counts[4]).isGreaterThan((long) RUNS * WEIGHTS[4] / Wheel.TOTAL_BP);
    }

    @Test
    void backwardDevelopmentShiftsLevelsTowardsPoverty() {
        long[] counts = levels(List.of(), development(-3, -3));

        assertThat(chiSquared(counts, weights(-60))).isLessThan(CHI_SQUARED_4_DOF);
        assertThat(counts[0]).isGreaterThan((long) RUNS * WEIGHTS[0] / Wheel.TOTAL_BP);
    }

    private static int[] weights(int advantage) {
        return Wheel.applyAdvantage(
                        GdpWheel.sectors(PACK),
                        advantage,
                        PACK.balance().wheel().strength(GdpWheel.KIND))
                .stream()
                .mapToInt(Sector::weightBp)
                .toArray();
    }

    private static long[] levels(List<Modifier> modifiers, StartDevelopment development) {
        long[] counts = new long[IDS.length];
        List<String> ids = Arrays.asList(IDS);
        for (long seed = 0; seed < RUNS; seed++) {
            counts[
                    ids.indexOf(GdpWheel.generate(Rng.of(seed), PACK, modifiers, development)
                            .level()
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
