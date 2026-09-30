package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestPopulation.FERTILITY;
import static kolo.engine.generation.country.TestPopulation.GEOGRAPHY;
import static kolo.engine.generation.country.TestPopulation.IDS;
import static kolo.engine.generation.country.TestPopulation.LARGE;
import static kolo.engine.generation.country.TestPopulation.MEDIUM;
import static kolo.engine.generation.country.TestPopulation.PACK;
import static kolo.engine.generation.country.TestPopulation.SMALL;
import static kolo.engine.generation.country.TestPopulation.WEIGHTS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kolo.engine.content.AreaLevelId;
import kolo.engine.generation.map.FertilityMap;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;

/**
 * Розподіл рівнів населення відповідає вагам контенту, зсунутим площею й родючістю. Seed-и фіксовані, тож тест
 * детермінований; поріг — χ² для p = 0,001 і 4 ступенів свободи.
 */
class PopulationWheelDistributionTest {

    private static final int RUNS = 60_000;
    private static final double CHI_SQUARED_4_DOF = 18.47;

    /** Світ такої самої родючості, як держава: внеску родючості немає. */
    private static final FertilityMap EVEN = new FertilityMap(new TreeMap<>(Map.of(0, 42, 1, 42, 2, 42, 3, 42)));

    @Test
    void averageCountryFollowsContentWeights() {
        long[] counts = levels(MEDIUM, EVEN);

        assertThat(chiSquared(counts, WEIGHTS)).isLessThan(CHI_SQUARED_4_DOF);
    }

    @Test
    void largeFertileCountryShiftsToLargerPopulation() {
        // Площа +20, родючість 42 − 40 = +2.
        long[] counts = levels(LARGE, FERTILITY);

        assertThat(chiSquared(counts, weights(22))).isLessThan(CHI_SQUARED_4_DOF);
        assertThat(counts[4]).isGreaterThan((long) RUNS * WEIGHTS[4] / Wheel.TOTAL_BP);
    }

    @Test
    void smallCountryShiftsToSmallerPopulation() {
        long[] counts = levels(SMALL, EVEN);

        assertThat(chiSquared(counts, weights(-10))).isLessThan(CHI_SQUARED_4_DOF);
        assertThat(counts[0]).isGreaterThan((long) RUNS * WEIGHTS[0] / Wheel.TOTAL_BP);
    }

    private static int[] weights(int advantage) {
        return Wheel.applyAdvantage(
                        PopulationWheel.sectors(PACK),
                        advantage,
                        PACK.balance().wheel().strength(PopulationWheel.KIND))
                .stream()
                .mapToInt(Sector::weightBp)
                .toArray();
    }

    private static long[] levels(AreaLevelId area, FertilityMap fertility) {
        long[] counts = new long[IDS.length];
        List<String> ids = Arrays.asList(IDS);
        for (long seed = 0; seed < RUNS; seed++) {
            counts[
                    ids.indexOf(PopulationWheel.generate(Rng.of(seed), PACK, List.of(), area, GEOGRAPHY, fertility)
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
