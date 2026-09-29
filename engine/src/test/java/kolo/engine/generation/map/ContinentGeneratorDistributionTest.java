package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.ContentPack;
import kolo.engine.content.TestMaps;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/** Колесо ваги материка рівномірне. Seed-и фіксовані, тож тест детермінований; поріг — χ² для p = 0,001. */
class ContinentGeneratorDistributionTest {

    private static final ContentPack PACK = TestNames.PACK;
    private static final int RUNS = 3_000;

    @Test
    void sizeWeightsAreUniform() {
        // Вага 1–3; сітка одна на всі прогони — колесо ваги від неї не залежить.
        WorldSize size = ContinentGeneratorTest.size(TestMaps.ARCHIPELAGO, 5, 250);
        MapGrid grid = ContinentGeneratorTest.grid(0, TestMaps.ARCHIPELAGO, size);
        long[] counts = new long[3];
        for (long seed = 0; seed < RUNS; seed++) {
            for (Continent continent :
                    ContinentGenerator.generate(Rng.of(seed), PACK, size, grid).continents()) {
                counts[continent.sizeWeight() - 1]++;
            }
        }

        double mean = RUNS * 5 / 3.0;
        double chiSquared = 0;
        for (long count : counts) {
            chiSquared += (count - mean) * (count - mean) / mean;
        }
        // 2 ступені свободи.
        assertThat(chiSquared).isLessThan(13.82);
    }
}
