package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import kolo.engine.content.ContentPack;
import kolo.engine.content.DepositDef;
import kolo.engine.content.ResourceId;
import kolo.engine.content.TestResources;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.name.TestNames;
import kolo.engine.state.Terrain;
import org.junit.jupiter.api.Test;

class ResourceSuitabilityGeneratorTest {

    private static final ContentPack PACK = TestNames.pack(TestResources.RESOURCES, TestResources.BALANCE);

    @Test
    void suitabilityFollowsTheTableOnGeneratedWorlds() {
        for (long seed = 0; seed < 10; seed++) {
            World world = world(seed, 1 + (int) (seed % 5), 400);

            ResourceSuitabilityMap suitability =
                    ResourceSuitabilityGenerator.generate(PACK, world.climate(), world.fertility());

            ResourceSuitabilityChecks.assertValid(PACK, world.climate(), world.fertility(), suitability);
        }
    }

    @Test
    void resourcesLieWhereTheirTerrainIs() {
        World world = world(7, 2, 400);

        ResourceSuitabilityMap suitability =
                ResourceSuitabilityGenerator.generate(PACK, world.climate(), world.fertility());

        for (int cell : suitability.suitabilities().keySet()) {
            Terrain terrain = world.climate().terrains().get(cell);
            boolean ore = suitability.suitability(cell, TestResources.ORE) > 0;
            assertThat(ore).isEqualTo(terrain == Terrain.MOUNTAINS || terrain == Terrain.HILLS);
            assertThat(suitability.suitability(cell, TestResources.GRAIN) > 0)
                    .isEqualTo(world.fertility().fertility(cell).orElseThrow() >= 40);
            // Прянощі — лише торгівлею.
            assertThat(suitability.suitability(cell, TestResources.SPICE)).isZero();
        }
    }

    @Test
    void waterHasNoSuitability() {
        World world = world(3, 2, 200);

        ResourceSuitabilityMap suitability =
                ResourceSuitabilityGenerator.generate(PACK, world.climate(), world.fertility());

        for (int cell = 0; cell < world.full().grid().cells().size(); cell++) {
            assertThat(suitability.isLand(cell)).isEqualTo(!world.full().sea().isWater(cell));
            if (!suitability.isLand(cell)) {
                assertThat(suitability.suitability(cell, TestResources.ORE)).isZero();
            }
        }
    }

    @Test
    void mismatchedMapsAreRejected() {
        World world = world(1, 1, 100);
        World other = world(2, 1, 200);

        assertThatThrownBy(() -> ResourceSuitabilityGenerator.generate(PACK, world.climate(), other.fertility()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }

    @Test
    void mapRejectsValuesOutsideLimits() {
        ResourceId ore = TestResources.ORE;
        assertThat(map(Map.of(0, Map.of(ore, DepositDef.MAX_SUITABILITY))).suitability(0, ore))
                .isEqualTo(DepositDef.MAX_SUITABILITY);
        assertThat(map(Map.of(0, Map.of())).isLand(0)).isTrue();
        assertThat(map(Map.of(0, Map.of())).isLand(1)).isFalse();
        // Нульова придатність не зберігається.
        assertThatThrownBy(() -> map(Map.of(0, Map.of(ore, 0)))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> map(Map.of(0, Map.of(ore, DepositDef.MAX_SUITABILITY + 1))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> map(Map.of(-1, Map.of(ore, 5)))).isInstanceOf(ValidationException.class);
    }

    static ResourceSuitabilityMap map(Map<Integer, Map<ResourceId, Integer>> values) {
        TreeMap<Integer, SortedMap<ResourceId, Integer>> copy = new TreeMap<>();
        values.forEach((cell, resources) -> copy.put(cell, new TreeMap<>(resources)));
        return new ResourceSuitabilityMap(copy);
    }

    static World world(long seed, int continents, int provinces) {
        RiverGeneratorTest.Full full = RiverGeneratorTest.full(seed, continents, provinces);
        RiverMap rivers = RiverGenerator.generate(PACK, full.grid(), full.relief(), full.climate(), full.sea());
        FertilityMap fertility = FertilityGenerator.generate(PACK, full.climate(), rivers);
        return new World(full, fertility);
    }

    record World(RiverGeneratorTest.Full full, FertilityMap fertility) {
        ClimateMap climate() {
            return full.climate();
        }
    }
}
