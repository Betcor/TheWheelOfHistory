package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import kolo.engine.content.AreaLevelDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.TestMaps;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class PlacementGeneratorPropertiesTest {

    private static final ContentPack PACK = TestNames.PACK;

    @Property(tries = 60)
    void placementKeepsCountriesConnectedAndWithinShares(
            @ForAll long seed,
            @ForAll @IntRange(min = 1, max = 4) int players,
            @ForAll @IntRange(min = 0, max = 12) int npc,
            @ForAll @IntRange(min = 3, max = 5) int continents,
            @ForAll @IntRange(min = 200, max = 600) int provinces,
            @ForAll @IntRange(min = 0, max = 50) int unclaimedPct) {
        PlacementGeneratorTest.World world = PlacementGeneratorTest.world(
                seed, TestMaps.ARCHIPELAGO, players, npc, continents, provinces, unclaimedPct * 100);

        PlacementMap placement =
                PlacementGenerator.generate(Rng.of(seed), PACK, world.size(), world.grid(), world.continents());

        PlacementChecks.assertValid(
                world.grid(), world.continents(), placement, world.size(), TestMaps.PLACEMENT.minProvinces());
    }

    @Property(tries = 30)
    void sameSeedGivesSamePlacement(@ForAll long seed, @ForAll @IntRange(min = 1, max = 10) int npc) {
        PlacementGeneratorTest.World world =
                PlacementGeneratorTest.world(seed, TestMaps.PANGAEA, 2, npc, 1, 300, 1_000);

        assertThat(PlacementGenerator.generate(Rng.of(seed), PACK, world.size(), world.grid(), world.continents()))
                .isEqualTo(PlacementGenerator.generate(
                        Rng.of(seed), PACK, world.size(), world.grid(), world.continents()));
    }

    @Property(tries = 200)
    void targetsNeverExceedContinentSharesAndKeepMinimum(
            @ForAll long weightSeed,
            @ForAll @IntRange(min = 1, max = 8) int continents,
            @ForAll @IntRange(min = 1, max = 40) int countries,
            @ForAll @IntRange(min = 1, max = 5) int minimum) {
        Rng rng = Rng.of(weightSeed);
        int[] capacities = new int[continents];
        int[] counts = new int[continents];
        int[] countryContinents = new int[countries];
        AreaLevelDef[] areas = new AreaLevelDef[countries];
        for (int i = 0; i < countries; i++) {
            countryContinents[i] = rng.nextInt(continents);
            counts[countryContinents[i]]++;
            areas[i] = TestMaps.PLACEMENT
                    .areas()
                    .get(rng.nextInt(TestMaps.PLACEMENT.areas().size()));
        }
        for (int c = 0; c < continents; c++) {
            capacities[c] = counts[c] * minimum + rng.nextInt(300);
        }

        int[] targets = PlacementGenerator.targets(capacities, countryContinents, areas, minimum);

        long[] sums = new long[continents];
        for (int i = 0; i < countries; i++) {
            assertThat(targets[i]).isGreaterThanOrEqualTo(minimum);
            sums[countryContinents[i]] += targets[i];
        }
        long occupiedCapacity = 0;
        for (int c = 0; c < continents; c++) {
            assertThat(sums[c]).isLessThanOrEqualTo(capacities[c]);
            if (counts[c] > 0) {
                occupiedCapacity += capacities[c];
            }
        }
        // Займаються всі частки, до яких можна дістатися: материків без держав немає в поділі.
        long total = Arrays.stream(capacities).asLongStream().sum();
        assertThat(Arrays.stream(sums).sum()).isEqualTo(Math.min(total, occupiedCapacity));
    }
}
