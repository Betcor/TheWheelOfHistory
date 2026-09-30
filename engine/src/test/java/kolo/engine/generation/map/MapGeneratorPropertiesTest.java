package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.ContentPack;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/** Властивості всієї генерації карти на довільних seed. */
class MapGeneratorPropertiesTest {

    private static final ContentPack PACK = TestNames.PACK;

    @Property(tries = 30)
    void sameSeedGivesSameMap(
            @ForAll long seed, @ForAll @IntRange(min = 1, max = 3) int players, @ForAll NpcShare share) {
        WorldSizeInput input = WorldSizeInput.of(players, share);

        assertThat(MapGenerator.generate(Rng.of(seed), PACK, input))
                .isEqualTo(MapGenerator.generate(Rng.of(seed), PACK, input));
    }

    @Property(tries = 30)
    void everyCountryIsPlacedAndNeighborsAreSymmetric(
            @ForAll long seed, @ForAll @IntRange(min = 1, max = 3) int players, @ForAll NpcShare share) {
        WorldMap map = MapGenerator.generate(Rng.of(seed), PACK, WorldSizeInput.of(players, share));

        assertThat(map.countries()).isEqualTo(map.size().countries());
        for (int country = 0; country < map.countries(); country++) {
            assertThat(map.neighbors(country)).doesNotContain(country);
            for (int neighbor : map.neighbors(country)) {
                assertThat(map.neighbors(neighbor)).contains(country);
            }
        }
    }
}
