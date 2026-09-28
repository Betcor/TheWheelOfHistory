package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestRegime.CONSTITUTIONAL;
import static kolo.engine.generation.country.TestRegime.DEMOCRACY;
import static kolo.engine.generation.country.TestRegime.PACK;
import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/**
 * Розподіл ладу відповідає вагам контенту. Seed-и фіксовані, тож тест детермінований; пороги — χ² для p = 0,001.
 */
class RegimeWheelDistributionTest {

    private static final int RUNS = 60_000;

    @Test
    void ideologyAndSubIdeologyFollowContentWeights() {
        // Ідеології 100 : 300; у демократії підкласифікації 100 : 300, у монархії одна.
        long[] counts = new long[3];
        for (long seed = 0; seed < RUNS; seed++) {
            Regime regime = RegimeWheel.generate(Rng.of(seed), PACK);
            if (!regime.ideology().equals(DEMOCRACY)) {
                counts[2]++;
            } else if (regime.subIdeology().equals(CONSTITUTIONAL)) {
                counts[1]++;
            } else {
                counts[0]++;
            }
        }

        // 2500 bp × (2500 : 7500) = 625 : 1875, монархія — 7500 bp; 2 ступені свободи.
        assertThat(chiSquared(counts, new int[] {625, 1875, 7500})).isLessThan(13.82);
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
