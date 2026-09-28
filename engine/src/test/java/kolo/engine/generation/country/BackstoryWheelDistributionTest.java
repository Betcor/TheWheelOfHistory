package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import kolo.engine.content.BackstoryFragmentDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/**
 * Розподіл результатів колеса передісторії відповідає вагам. Seed-и фіксовані, тож тест детермінований; пороги —
 * χ² для p = 0,001.
 */
class BackstoryWheelDistributionTest {

    private static final int RUNS = 60_000;

    @Test
    void countsAreEquallyLikely() {
        long[] counts = new long[3];
        for (long seed = 0; seed < RUNS; seed++) {
            int size = BackstoryWheel.generate(Rng.of(seed), TestBackstory.PACK, Set.of(), List.of())
                    .entries()
                    .size();
            counts[size - 2]++;
        }

        // Ваги 3334/3333/3333 bp; 2 ступені свободи.
        assertThat(chiSquared(counts, new int[] {3334, 3333, 3333})).isLessThan(13.82);
    }

    @Test
    void firstFragmentFollowsContentWeights() {
        BackstoryFragmentDef rare = TestBackstory.fragment("rare", 100, 50, 1900, 1969, List.of());
        BackstoryFragmentDef common = TestBackstory.fragment("common", 300, 50, 1900, 1969, List.of());
        ContentPack pack = TestBackstory.pack(List.of(rare, common), new CountRange(1, 1));

        long[] counts = new long[2];
        for (long seed = 0; seed < RUNS; seed++) {
            BackstoryFragmentDef first = BackstoryWheel.generate(Rng.of(seed), pack, Set.of(), List.of())
                    .entries()
                    .getFirst()
                    .fragment();
            counts[first.equals(rare) ? 0 : 1]++;
        }

        // Ваги 100 : 300 → 2500 : 7500 bp; 1 ступінь свободи.
        assertThat(chiSquared(counts, new int[] {2500, 7500})).isLessThan(10.83);
    }

    private static double chiSquared(long[] counts, int[] weightsBp) {
        double chiSquared = 0;
        for (int i = 0; i < counts.length; i++) {
            double mean = (double) RUNS * weightsBp[i] / 10_000;
            chiSquared += (counts[i] - mean) * (counts[i] - mean) / mean;
        }
        return chiSquared;
    }
}
