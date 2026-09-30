package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.ContentPack;
import kolo.engine.content.TestMaps;
import kolo.engine.generation.name.TestNames;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class FertilityGeneratorPropertiesTest {

    private static final ContentPack PACK = TestNames.PACK;

    @Property(tries = 40)
    void everyLandCellFollowsTheTable(
            @ForAll long seed,
            @ForAll @IntRange(min = 1, max = 5) int continents,
            @ForAll @IntRange(min = 50, max = 500) int provinces) {
        RiverGeneratorTest.Full world = RiverGeneratorTest.full(seed, continents, provinces);
        RiverMap rivers = RiverGenerator.generate(PACK, world.grid(), world.relief(), world.climate(), world.sea());

        FertilityMap fertility = FertilityGenerator.generate(PACK, world.climate(), rivers);

        FertilityChecks.assertValid(world.climate(), rivers, fertility, TestMaps.FERTILITY);
    }

    @Property(tries = 20)
    void sameMapGivesSameFertility(@ForAll long seed, @ForAll @IntRange(min = 10, max = 300) int provinces) {
        RiverGeneratorTest.Full world = RiverGeneratorTest.full(seed, 1, provinces);
        RiverMap rivers = RiverGenerator.generate(PACK, world.grid(), world.relief(), world.climate(), world.sea());

        assertThat(FertilityGenerator.generate(PACK, world.climate(), rivers))
                .isEqualTo(FertilityGenerator.generate(PACK, world.climate(), rivers));
    }
}
