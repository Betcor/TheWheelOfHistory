package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.content.TestMaps;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class ContinentGeneratorPropertiesTest {

    private static final ContentPack PACK = TestNames.PACK;

    @Property(tries = 100)
    void continentsKeepCountSizesAndSea(
            @ForAll long seed,
            @ForAll @IntRange(min = 3, max = 5) int continents,
            @ForAll @IntRange(min = 50, max = 600) int provinces) {
        MapTemplateDef template = TestMaps.ARCHIPELAGO;
        WorldSize size = ContinentGeneratorTest.size(template, continents, provinces);
        MapGrid grid = VoronoiGrid.generate(Rng.of(seed).fork("grid"), TestMaps.GRID, template.gridCells(provinces));

        ContinentMap map = ContinentGenerator.generate(Rng.of(seed), PACK, size, grid);

        ContinentChecks.assertValid(grid, map, size, TestMaps.CONTINENTS.minProvinces());
    }

    @Property(tries = 30)
    void sameSeedGivesSameContinents(@ForAll long seed, @ForAll @IntRange(min = 10, max = 300) int provinces) {
        MapTemplateDef template = TestMaps.PANGAEA;
        WorldSize size = ContinentGeneratorTest.size(template, 1, provinces);
        MapGrid grid = VoronoiGrid.generate(Rng.of(seed), TestMaps.GRID, template.gridCells(provinces));

        assertThat(ContinentGenerator.generate(Rng.of(seed), PACK, size, grid))
                .isEqualTo(ContinentGenerator.generate(Rng.of(seed), PACK, size, grid));
    }

    @Property(tries = 50)
    void targetsSumToProvincesAndKeepMinimum(
            @ForAll @IntRange(min = 1, max = 12) int count,
            @ForAll @IntRange(min = 1, max = 50) int minimum,
            @ForAll @IntRange(min = 0, max = 5_000) int extra,
            @ForAll long weightSeed) {
        Rng rng = Rng.of(weightSeed);
        int[] weights = new int[count];
        for (int i = 0; i < count; i++) {
            weights[i] = 1 + rng.nextInt(100);
        }
        int provinces = count * minimum + extra;

        int[] targets = ContinentGenerator.targets(provinces, minimum, weights);

        assertThat(java.util.Arrays.stream(targets).sum()).isEqualTo(provinces);
        for (int i = 0; i < count; i++) {
            assertThat(targets[i]).isGreaterThanOrEqualTo(minimum);
            // Частка за вагою з точністю до одного через округлення.
            long exact = (long) extra * weights[i];
            long sum = java.util.Arrays.stream(weights).sum();
            assertThat((long) (targets[i] - minimum) * sum).isBetween(exact - sum, exact + sum);
        }
    }
}
