package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.ContentPack;
import kolo.engine.content.TestResources;
import kolo.engine.generation.name.TestNames;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class ResourceSuitabilityGeneratorPropertiesTest {

    private static final ContentPack PACK = TestNames.pack(TestResources.RESOURCES, TestResources.BALANCE);

    @Property(tries = 40)
    void everyLandCellFollowsTheTable(
            @ForAll long seed,
            @ForAll @IntRange(min = 1, max = 5) int continents,
            @ForAll @IntRange(min = 50, max = 500) int provinces) {
        ResourceSuitabilityGeneratorTest.World world =
                ResourceSuitabilityGeneratorTest.world(seed, continents, provinces);

        ResourceSuitabilityMap suitability =
                ResourceSuitabilityGenerator.generate(PACK, world.climate(), world.fertility());

        ResourceSuitabilityChecks.assertValid(PACK, world.climate(), world.fertility(), suitability);
    }

    @Property(tries = 20)
    void sameMapGivesSameSuitability(@ForAll long seed, @ForAll @IntRange(min = 10, max = 300) int provinces) {
        ResourceSuitabilityGeneratorTest.World world = ResourceSuitabilityGeneratorTest.world(seed, 1, provinces);

        assertThat(ResourceSuitabilityGenerator.generate(PACK, world.climate(), world.fertility()))
                .isEqualTo(ResourceSuitabilityGenerator.generate(PACK, world.climate(), world.fertility()));
    }
}
