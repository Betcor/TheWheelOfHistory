package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestNuclear.IRON;
import static kolo.engine.generation.country.TestNuclear.PACK;
import static kolo.engine.generation.country.TestNuclear.REGIME;
import static kolo.engine.generation.country.TestNuclear.URANIUM;
import static kolo.engine.generation.country.TestNuclear.WARHEADS;
import static kolo.engine.generation.country.TestNuclear.WEIGHTS;
import static kolo.engine.generation.country.TestNuclear.development;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import kolo.engine.content.ResourceId;
import kolo.engine.modifier.Modifier;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;

/**
 * Розподіл статусів відповідає вагам контенту, зсунутим перевагою ладу й енергетики, а кількість боєголовок —
 * рівномірна. Seed-и фіксовані, тож тест детермінований; пороги — χ² для p = 0,001.
 */
class NuclearWheelDistributionTest {

    private static final int RUNS = 60_000;
    private static final double CHI_SQUARED_1_DOF = 10.83;
    private static final double CHI_SQUARED_2_DOF = 13.82;
    private static final double CHI_SQUARED_3_DOF = 16.27;
    private static final List<ResourceId> WITH_URANIUM = List.of(IRON, URANIUM);

    @Test
    void withoutAdvantageStatusesFollowContentWeights() {
        long[] counts = statuses(List.of(), development(0), WITH_URANIUM);

        assertThat(chiSquared(counts, WEIGHTS)).isLessThan(CHI_SQUARED_2_DOF);
    }

    @Test
    void regimeAndEnergyShiftStatusesToAdjustedWeights() {
        // Лад +30, енергетика +2 → +20: разом +50.
        long[] counts = statuses(REGIME.modifiers(), development(2), WITH_URANIUM);
        int[] adjusted = Wheel.applyAdvantage(
                        NuclearWheel.sectors(PACK, true),
                        50,
                        PACK.balance().wheel().strength(NuclearWheel.KIND))
                .stream()
                .mapToInt(Sector::weightBp)
                .toArray();

        assertThat(chiSquared(counts, adjusted)).isLessThan(CHI_SQUARED_2_DOF);
        assertThat(counts[2]).isGreaterThan((long) RUNS * WEIGHTS[2] / Wheel.TOTAL_BP);
    }

    @Test
    void withoutUraniumProgramKeepsItsShareOfTheRest() {
        long[] counts = statuses(List.of(), development(0), List.of(IRON));

        assertThat(counts[2]).isZero();
        // Без арсеналу ваги «немає» і «програма» нормалізуються між собою.
        int[] rest = {WEIGHTS[0], WEIGHTS[1]};
        assertThat(chiSquared(new long[] {counts[0], counts[1]}, normalized(rest)))
                .isLessThan(CHI_SQUARED_1_DOF);
    }

    @Test
    void warheadsAreUniformWithinBalance() {
        int size = WARHEADS.max() - WARHEADS.min() + 1;
        long[] counts = new long[size];
        long arsenals = 0;
        for (long seed = 0; seed < RUNS; seed++) {
            // Найбільша перевага: так арсеналів досить для перевірки.
            StartNuclear nuclear = NuclearWheel.generate(
                    Rng.of(seed), PACK, List.of(TestNuclear.modifier("max", 100)), development(0), WITH_URANIUM);
            if (nuclear.warheads() > 0) {
                counts[nuclear.warheads() - WARHEADS.min()]++;
                arsenals++;
            }
        }
        int[] equal = new int[size];
        Arrays.fill(equal, Wheel.TOTAL_BP / size);

        assertThat(chiSquared(counts, equal, arsenals)).isLessThan(CHI_SQUARED_3_DOF);
    }

    private static long[] statuses(List<Modifier> modifiers, StartDevelopment development, List<ResourceId> resources) {
        long[] counts = new long[3];
        for (long seed = 0; seed < RUNS; seed++) {
            counts[
                    NuclearWheel.generate(Rng.of(seed), PACK, modifiers, development, resources)
                            .status()
                            .ordinal()]++;
        }
        return counts;
    }

    private static int[] normalized(int[] weights) {
        int total = Arrays.stream(weights).sum();
        int[] result = new int[weights.length];
        for (int i = 0; i < weights.length; i++) {
            result[i] = weights[i] * Wheel.TOTAL_BP / total;
        }
        return result;
    }

    private static double chiSquared(long[] counts, int[] weightsBp) {
        return chiSquared(counts, weightsBp, RUNS);
    }

    private static double chiSquared(long[] counts, int[] weightsBp, long runs) {
        double chiSquared = 0;
        for (int i = 0; i < counts.length; i++) {
            double mean = (double) runs * weightsBp[i] / Wheel.TOTAL_BP;
            chiSquared += (counts[i] - mean) * (counts[i] - mean) / mean;
        }
        return chiSquared;
    }
}
