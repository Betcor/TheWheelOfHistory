package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.ContentPack;
import kolo.engine.content.TestMaps;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class SeaGeneratorPropertiesTest {

    private static final ContentPack PACK = TestNames.PACK;

    @Property(tries = 100)
    void seaCoversWaterWithConnectedZones(
            @ForAll long seed,
            @ForAll @IntRange(min = 1, max = 5) int continents,
            @ForAll @IntRange(min = 50, max = 600) int provinces) {
        ReliefGeneratorTest.World world = ReliefGeneratorTest.world(seed, continents, provinces);

        SeaMap sea = SeaGenerator.generate(Rng.of(seed), PACK, world.grid(), world.continents());

        SeaChecks.assertValid(world.grid(), world.continents(), sea, TestMaps.SEA);
    }

    @Property(tries = 30)
    void sameSeedGivesSameSea(@ForAll long seed, @ForAll @IntRange(min = 10, max = 300) int provinces) {
        ReliefGeneratorTest.World world = ReliefGeneratorTest.world(seed, 1, provinces);

        assertThat(SeaGenerator.generate(Rng.of(seed), PACK, world.grid(), world.continents()))
                .isEqualTo(SeaGenerator.generate(Rng.of(seed), PACK, world.grid(), world.continents()));
    }
}
