package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.ContentPack;
import kolo.engine.content.TestMaps;
import kolo.engine.generation.name.TestNames;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class RiverGeneratorPropertiesTest {

    private static final ContentPack PACK = TestNames.PACK;

    @Property(tries = 60)
    void everyProvinceDrainsToWater(
            @ForAll long seed,
            @ForAll @IntRange(min = 1, max = 5) int continents,
            @ForAll @IntRange(min = 50, max = 500) int provinces) {
        RiverGeneratorTest.Full world = RiverGeneratorTest.full(seed, continents, provinces);

        RiverMap rivers = RiverGenerator.generate(PACK, world.grid(), world.relief(), world.climate(), world.sea());

        RiverChecks.assertValid(world.grid(), world.relief(), world.climate(), world.sea(), rivers, TestMaps.RIVERS);
    }

    @Property(tries = 30)
    void sameMapGivesSameRivers(@ForAll long seed, @ForAll @IntRange(min = 10, max = 300) int provinces) {
        RiverGeneratorTest.Full world = RiverGeneratorTest.full(seed, 1, provinces);

        assertThat(RiverGenerator.generate(PACK, world.grid(), world.relief(), world.climate(), world.sea()))
                .isEqualTo(RiverGenerator.generate(PACK, world.grid(), world.relief(), world.climate(), world.sea()));
    }
}
