package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import kolo.engine.generation.name.TestNames;
import org.junit.jupiter.api.Test;

/**
 * Розподіл колеса назви: кожен кандидат випадає рівноймовірно, стиль і форма першого кандидата — рівноймовірні.
 * Seed-и фіксовані, тож тест детермінований; пороги — χ² для p = 0,001.
 */
class NameWheelDistributionTest {

    private static final int RUNS = 20_000;
    private static final double CHI_SQUARED_1_DOF = 10.83;
    private static final double CHI_SQUARED_4_DOF = 18.47;

    @Test
    void everyCandidateIsEquallyLikely() {
        long[] counts = new long[5];
        for (long seed = 0; seed < RUNS; seed++) {
            counts[NameWheelTest.generate(seed, NameWheelTest.DIRECT, Set.of()).chosen()]++;
        }
        assertThat(chiSquaredUniform(counts)).isLessThan(CHI_SQUARED_4_DOF);
    }

    @Test
    void stylesAndFormsAreEquallyLikely() {
        // Перший кандидат не перегенеровується: без зайнятих назв конфлікту ще немає.
        long[] styles = new long[2];
        long[] forms = new long[2];
        for (long seed = 0; seed < RUNS; seed++) {
            NameCandidate first = NameWheelTest.generate(seed, NameWheelTest.DIRECT, Set.of())
                    .candidates()
                    .getFirst();
            styles[first.style().equals(TestNames.NORTHERN.id()) ? 0 : 1]++;
            forms[first.name().fullName().nominative().startsWith("Республіка ") ? 0 : 1]++;
        }
        assertThat(chiSquaredUniform(styles)).isLessThan(CHI_SQUARED_1_DOF);
        assertThat(chiSquaredUniform(forms)).isLessThan(CHI_SQUARED_1_DOF);
    }

    /** χ² спостережених частот проти рівних. */
    private static double chiSquaredUniform(long[] counts) {
        long total = 0;
        for (long count : counts) {
            total += count;
        }
        double mean = (double) total / counts.length;
        double chiSquared = 0;
        for (long count : counts) {
            chiSquared += (count - mean) * (count - mean) / mean;
        }
        return chiSquared;
    }
}
