package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.ContentPack;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/** Колесо кількості хребтів рівномірне. Seed-и фіксовані, тож тест детермінований; поріг — χ² для p = 0,001. */
class ReliefGeneratorDistributionTest {

    private static final ContentPack PACK = TestNames.PACK;
    private static final int RUNS = 3_000;

    @Test
    void ridgeCountsAreUniform() {
        // Один материк на 200 провінцій: контент дає 1–2 хребти без обрізання; карта одна на всі прогони.
        ReliefGeneratorTest.World world = ReliefGeneratorTest.world(0, 1, 200);
        long[] counts = new long[2];
        for (long seed = 0; seed < RUNS; seed++) {
            counts[
                    ReliefGenerator.generate(Rng.of(seed), PACK, world.grid(), world.continents())
                                    .ridges()
                                    .size()
                            - 1]++;
        }

        double mean = RUNS / 2.0;
        double chiSquared = 0;
        for (long count : counts) {
            chiSquared += (count - mean) * (count - mean) / mean;
        }
        // 1 ступінь свободи.
        assertThat(chiSquared).isLessThan(10.83);
    }
}
