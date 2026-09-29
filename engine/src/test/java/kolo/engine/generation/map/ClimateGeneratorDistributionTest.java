package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.ContentPack;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/** Колесо клімату світу відповідає вагам контенту. Seed-и фіксовані, тож тест детермінований; поріг — χ² для p = 0,001. */
class ClimateGeneratorDistributionTest {

    private static final ContentPack PACK = TestNames.PACK;
    private static final int RUNS = 4_000;

    @Test
    void worldClimatesFollowWeights() {
        // Холодний, помірний і теплий світи з вагами 1 : 2 : 1; карта одна на всі прогони.
        ClimateGeneratorTest.World world = ClimateGeneratorTest.world(0, 1, 100);
        long[] counts = new long[3];
        for (long seed = 0; seed < RUNS; seed++) {
            String id = ClimateGenerator.generate(Rng.of(seed), PACK, world.grid(), world.continents(), world.relief())
                    .world()
                    .value();
            counts[
                    switch (id) {
                        case "cold" -> 0;
                        case "temperate" -> 1;
                        default -> 2;
                    }]++;
        }

        double[] expected = {RUNS / 4.0, RUNS / 2.0, RUNS / 4.0};
        double chiSquared = 0;
        for (int i = 0; i < counts.length; i++) {
            chiSquared += (counts[i] - expected[i]) * (counts[i] - expected[i]) / expected[i];
        }
        // 2 ступені свободи.
        assertThat(chiSquared).isLessThan(13.82);
    }
}
