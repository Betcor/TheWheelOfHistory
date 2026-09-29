package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakRewardDef;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/**
 * Розподіл нагород стріків відповідає вагам контенту. Seed-и фіксовані, тож тест детермінований; пороги — χ² для
 * p = 0,001.
 */
class StreakWheelDistributionTest {

    private static final int RUNS = 60_000;

    @Test
    void goldenAgeRewardsFollowContentWeights() {
        // 100 : 300 : 600; 2 ступені свободи.
        assertThat(chiSquared(StreakKind.GOLDEN_AGE, new int[] {1000, 3000, 6000}))
                .isLessThan(13.82);
    }

    @Test
    void underdogRewardsFollowContentWeights() {
        // 100 : 100; 1 ступінь свободи.
        assertThat(chiSquared(StreakKind.UNDERDOG, new int[] {5000, 5000})).isLessThan(10.83);
    }

    private static double chiSquared(StreakKind kind, int[] weightsBp) {
        List<StreakRewardDef> rewards = TestRegime.PACK.streaks().wheel(kind).rewards();
        long[] counts = new long[rewards.size()];
        for (long seed = 0; seed < RUNS; seed++) {
            counts[
                    rewards.indexOf(StreakWheel.generate(Rng.of(seed), TestRegime.PACK, kind)
                            .reward())]++;
        }
        double chiSquared = 0;
        for (int i = 0; i < counts.length; i++) {
            double mean = (double) RUNS * weightsBp[i] / 10_000;
            chiSquared += (counts[i] - mean) * (counts[i] - mean) / mean;
        }
        return chiSquared;
    }
}
