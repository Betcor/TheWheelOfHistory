package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.ContentPack;
import kolo.engine.content.TestMaps;
import kolo.engine.generation.name.TestNames;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class ClimateGeneratorPropertiesTest {

    private static final ContentPack PACK = TestNames.PACK;

    @Property(tries = 100)
    void climateCoversLandAndFollowsContent(
            @ForAll long seed,
            @ForAll @IntRange(min = 1, max = 5) int continents,
            @ForAll @IntRange(min = 50, max = 600) int provinces) {
        ClimateGeneratorTest.World world = ClimateGeneratorTest.world(seed, continents, provinces);

        ClimateMap climate = ClimateGeneratorTest.generate(seed, PACK, world);

        ClimateChecks.assertValid(world.continents(), world.relief(), climate, TestMaps.CLIMATE);
    }

    @Property(tries = 30)
    void sameSeedGivesSameClimate(@ForAll long seed, @ForAll @IntRange(min = 10, max = 300) int provinces) {
        ClimateGeneratorTest.World world = ClimateGeneratorTest.world(seed, 1, provinces);

        assertThat(ClimateGeneratorTest.generate(seed, PACK, world))
                .isEqualTo(ClimateGeneratorTest.generate(seed, PACK, world));
    }
}
