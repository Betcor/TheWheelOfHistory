package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.ContentPack;
import kolo.engine.content.TestMaps;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import org.junit.jupiter.api.Test;

/**
 * Розподіл коліс розміру світу відповідає вагам. Seed-и фіксовані, тож тест детермінований; пороги — χ² для
 * p = 0,001.
 */
class WorldSizeWheelDistributionTest {

    private static final ContentPack PACK = TestNames.PACK;
    private static final int RUNS = 60_000;

    @Test
    void templatesAndTheirContinentsFollowWeights() {
        // Шаблони 100 : 100; у Пангеї 1 материк, в Архіпелагу — рівно 3, 4 або 5.
        long[] counts = new long[4];
        for (long seed = 0; seed < RUNS; seed++) {
            WorldSize size = WorldSizeWheel.generate(Rng.of(seed), PACK, WorldSizeInput.of(2, NpcShare.NORMAL));
            if (size.template().equals(TestMaps.PANGAEA.id())) {
                counts[0]++;
            } else {
                counts[size.continents() - 2]++;
            }
        }

        // 3 ступені свободи.
        assertThat(chiSquared(counts, new double[] {5000, 5000 / 3.0, 5000 / 3.0, 5000 / 3.0}))
                .isLessThan(16.27);
    }

    @Test
    void npcProvincesAndUnclaimedLandAreUniform() {
        long[] npc = new long[3];
        long[] provinces = new long[3];
        long[] unclaimed = new long[3];
        for (long seed = 0; seed < RUNS; seed++) {
            WorldSize size = WorldSizeWheel.generate(Rng.of(seed), PACK, WorldSizeInput.of(2, NpcShare.NORMAL));
            npc[size.npc() - 2 - 2]++;
            provinces[(size.provincesPerCountry() - 60) / 20]++;
            unclaimed[(size.unclaimedBp() - 500) / 500]++;
        }

        double[] uniform = {10_000 / 3.0, 10_000 / 3.0, 10_000 / 3.0};
        // 2 ступені свободи.
        assertThat(chiSquared(npc, uniform)).isLessThan(13.82);
        assertThat(chiSquared(provinces, uniform)).isLessThan(13.82);
        assertThat(chiSquared(unclaimed, uniform)).isLessThan(13.82);
    }

    private static double chiSquared(long[] counts, double[] weightsBp) {
        double chiSquared = 0;
        for (int i = 0; i < counts.length; i++) {
            double mean = RUNS * weightsBp[i] / 10_000;
            chiSquared += (counts[i] - mean) * (counts[i] - mean) / mean;
        }
        return chiSquared;
    }
}
