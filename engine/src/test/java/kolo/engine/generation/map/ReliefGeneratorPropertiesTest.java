package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.ContentPack;
import kolo.engine.content.TestMaps;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class ReliefGeneratorPropertiesTest {

    private static final ContentPack PACK = TestNames.PACK;

    @Property(tries = 100)
    void reliefCoversLandWithinLimits(
            @ForAll long seed,
            @ForAll @IntRange(min = 1, max = 5) int continents,
            @ForAll @IntRange(min = 50, max = 600) int provinces) {
        ReliefGeneratorTest.World world = ReliefGeneratorTest.world(seed, continents, provinces);

        ReliefMap relief = ReliefGenerator.generate(Rng.of(seed), PACK, world.grid(), world.continents());

        ReliefChecks.assertValid(world.grid(), world.continents(), relief, TestMaps.RELIEF);
    }

    @Property(tries = 30)
    void sameSeedGivesSameRelief(@ForAll long seed, @ForAll @IntRange(min = 10, max = 300) int provinces) {
        ReliefGeneratorTest.World world = ReliefGeneratorTest.world(seed, 1, provinces);

        assertThat(ReliefGenerator.generate(Rng.of(seed), PACK, world.grid(), world.continents()))
                .isEqualTo(ReliefGenerator.generate(Rng.of(seed), PACK, world.grid(), world.continents()));
    }
}
