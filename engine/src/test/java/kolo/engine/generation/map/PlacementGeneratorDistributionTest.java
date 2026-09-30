package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.AreaLevelDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.TestMaps;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/**
 * Колеса розміщення відповідають вагам. Seed-и фіксовані, тож тест детермінований; поріг — χ² для p = 0,001.
 */
class PlacementGeneratorDistributionTest {

    private static final ContentPack PACK = TestNames.PACK;
    private static final int RUNS = 2_000;

    @Test
    void areaLevelsFollowContentWeights() {
        // Площа не залежить від карти: одна карта на всі прогони.
        PlacementGeneratorTest.World world = PlacementGeneratorTest.world(0, TestMaps.PANGAEA, 1, 3, 1, 200, 1_000);
        int areas = TestMaps.PLACEMENT.areas().size();
        long[] counts = new long[areas];
        for (long seed = 0; seed < RUNS; seed++) {
            for (PlacedCountry country : PlacementGenerator.generate(
                            Rng.of(seed), PACK, world.size(), world.grid(), world.continents())
                    .countries()) {
                counts[index(country)]++;
            }
        }

        long total = RUNS * 4L;
        long weightSum = TestMaps.PLACEMENT.areas().stream()
                .mapToLong(AreaLevelDef::weight)
                .sum();
        double chiSquared = 0;
        for (int a = 0; a < areas; a++) {
            double expected = (double) total * TestMaps.PLACEMENT.areas().get(a).weight() / weightSum;
            chiSquared += (counts[a] - expected) * (counts[a] - expected) / expected;
        }
        // 2 ступені свободи.
        assertThat(chiSquared).isLessThan(13.82);
    }

    @Test
    void firstCountryChoosesContinentByItsShareOfLand() {
        PlacementGeneratorTest.World world = PlacementGeneratorTest.world(2, TestMaps.ARCHIPELAGO, 1, 0, 3, 300, 1_000);
        int[] capacities =
                PlacementGenerator.capacities(world.continents(), world.size().unclaimedBp());
        long[] counts = new long[capacities.length];
        for (long seed = 0; seed < RUNS; seed++) {
            counts[
                    PlacementGenerator.generate(Rng.of(seed), PACK, world.size(), world.grid(), world.continents())
                            .countries()
                            .getFirst()
                            .continent()]++;
        }

        long capacitySum = 0;
        for (int capacity : capacities) {
            capacitySum += capacity;
        }
        double chiSquared = 0;
        for (int c = 0; c < capacities.length; c++) {
            double expected = (double) RUNS * capacities[c] / capacitySum;
            chiSquared += (counts[c] - expected) * (counts[c] - expected) / expected;
        }
        // 2 ступені свободи.
        assertThat(chiSquared).isLessThan(13.82);
    }

    private static int index(PlacedCountry country) {
        for (int a = 0; a < TestMaps.PLACEMENT.areas().size(); a++) {
            if (TestMaps.PLACEMENT.areas().get(a).id().equals(country.area())) {
                return a;
            }
        }
        throw new AssertionError(country.area());
    }
}
