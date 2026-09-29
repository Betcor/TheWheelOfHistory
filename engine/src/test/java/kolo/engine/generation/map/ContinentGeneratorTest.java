package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.content.MapTemplateId;
import kolo.engine.content.TestMaps;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContinentGeneratorTest {

    private static final ContentPack PACK = TestNames.PACK;
    private static final int MIN_PROVINCES = TestMaps.CONTINENTS.minProvinces();

    @Test
    void continentsGetExactCountAndProvincesSeparatedBySea() {
        for (long seed = 0; seed < 30; seed++) {
            WorldSize size = size(TestMaps.ARCHIPELAGO, 3 + (int) (seed % 3), 300);
            MapGrid grid = grid(seed, TestMaps.ARCHIPELAGO, size);

            ContinentMap map = ContinentGenerator.generate(Rng.of(seed), PACK, size, grid);

            ContinentChecks.assertValid(grid, map, size, MIN_PROVINCES);
        }
    }

    @Test
    void singleContinentTakesAllProvinces() {
        WorldSize size = size(TestMaps.PANGAEA, 1, 200);
        MapGrid grid = grid(3, TestMaps.PANGAEA, size);

        ContinentMap map = ContinentGenerator.generate(Rng.of(3), PACK, size, grid);

        ContinentChecks.assertValid(grid, map, size, MIN_PROVINCES);
        assertThat(map.continents().getFirst().cells()).hasSize(200);
        assertThat(map.cellContinents().stream().filter(owner -> owner == ContinentMap.SEA))
                .hasSize(grid.cells().size() - 200);
    }

    @Test
    void continentSizesFollowRolledWeights() {
        WorldSize size = size(TestMaps.ARCHIPELAGO, 4, 400);
        MapGrid grid = grid(5, TestMaps.ARCHIPELAGO, size);

        ContinentMap map = ContinentGenerator.generate(Rng.of(5), PACK, size, grid);

        int[] weights =
                map.continents().stream().mapToInt(Continent::sizeWeight).toArray();
        int[] targets = ContinentGenerator.targets(400, MIN_PROVINCES, weights);
        for (int c = 0; c < weights.length; c++) {
            assertThat(map.continents().get(c).cells()).hasSize(targets[c]);
        }
    }

    @Test
    void rollsAreNeutralSizeWheelsInContinentOrder() {
        WorldSize size = size(TestMaps.ARCHIPELAGO, 3, 300);
        ContinentMap map = ContinentGenerator.generate(Rng.of(9), PACK, size, grid(9, TestMaps.ARCHIPELAGO, size));

        assertThat(map.rolls()).hasSize(3).allSatisfy(roll -> {
            assertThat(roll.kind()).isEqualTo(ContinentGenerator.SIZE_KIND);
            assertThat(roll.advantage()).isZero();
            assertThat(roll.modifiers()).isEmpty();
            assertThat(roll.turn()).isZero();
            assertThat(roll.sectors()).extracting(RolledSector::id).containsExactly("size_1", "size_2", "size_3");
            assertThat(roll.sectors())
                    .allSatisfy(sector -> assertThat(sector.tier()).isEqualTo(OutcomeTier.PARTIAL));
        });
        for (int c = 0; c < 3; c++) {
            assertThat(map.rolls().get(c).resultSectorId())
                    .isEqualTo("size_" + map.continents().get(c).sizeWeight());
        }
    }

    @Test
    void sameSeedGivesSameContinents() {
        WorldSize size = size(TestMaps.ARCHIPELAGO, 5, 350);
        MapGrid grid = grid(21, TestMaps.ARCHIPELAGO, size);

        assertThat(ContinentGenerator.generate(Rng.of(21), PACK, size, grid))
                .isEqualTo(ContinentGenerator.generate(Rng.of(21), PACK, size, grid));
    }

    @Test
    void weightsDoNotDependOnGrid() {
        WorldSize size = size(TestMaps.ARCHIPELAGO, 5, 300);
        ContinentMap small = ContinentGenerator.generate(Rng.of(4), PACK, size, grid(1, TestMaps.ARCHIPELAGO, size));
        ContinentMap large = ContinentGenerator.generate(
                Rng.of(4), PACK, size, VoronoiGrid.generate(Rng.of(2), TestMaps.GRID, 2_000));

        assertThat(large.continents())
                .extracting(Continent::sizeWeight)
                .isEqualTo(
                        small.continents().stream().map(Continent::sizeWeight).toList());
    }

    @Test
    void targetsGiveMinimumAndSplitRestByWeightsWithLargestRemainders() {
        assertThat(ContinentGenerator.targets(100, 10, new int[] {1, 1, 2})).containsExactly(28, 27, 45);
        assertThat(ContinentGenerator.targets(100, 10, new int[] {1, 2, 3, 4})).containsExactly(16, 22, 28, 34);
        assertThat(ContinentGenerator.targets(30, 10, new int[] {1, 4, 2})).containsExactly(10, 10, 10);
        assertThat(ContinentGenerator.targets(35, 10, new int[] {3})).containsExactly(35);
        // Рівні залишки — менший номер першим.
        assertThat(ContinentGenerator.targets(32, 10, new int[] {1, 1, 1})).containsExactly(11, 11, 10);
    }

    @Test
    void seedsAreFarFromEachOtherAndFromEdge() {
        MapGrid grid = VoronoiGrid.generate(Rng.of(8), TestMaps.GRID, 1_500);

        int[] seeds = ContinentGenerator.seeds(Rng.of(8), grid, 4).orElseThrow();

        assertThat(seeds).doesNotHaveDuplicates();
        for (int seed : seeds) {
            assertThat(grid.cells().get(seed).edge()).isFalse();
        }
        for (int i = 0; i < seeds.length; i++) {
            int[] distance = ContinentGenerator.distances(grid, new int[] {seeds[i]});
            for (int j = i + 1; j < seeds.length; j++) {
                assertThat(distance[seeds[j]]).isGreaterThan(5);
            }
        }
    }

    @Test
    void distancesCountStepsFromNearestSource() {
        MapGrid grid = VoronoiGrid.generate(Rng.of(1), TestMaps.GRID, 50);

        int[] distance = ContinentGenerator.distances(grid, new int[] {0});

        assertThat(distance[0]).isZero();
        for (int neighbor : grid.cells().get(0).neighbors()) {
            assertThat(distance[neighbor]).isEqualTo(1);
        }
        for (int cell = 0; cell < 50; cell++) {
            for (int neighbor : grid.cells().get(cell).neighbors()) {
                assertThat(Math.abs(distance[cell] - distance[neighbor])).isLessThanOrEqualTo(1);
            }
        }
    }

    @Test
    void tooFewProvincesForContinentsAreRejected() {
        WorldSize size = size(TestMaps.ARCHIPELAGO, 5, 5 * MIN_PROVINCES - 1);
        MapGrid grid = VoronoiGrid.generate(Rng.of(1), TestMaps.GRID, 500);

        assertThatThrownBy(() -> ContinentGenerator.generate(Rng.of(1), PACK, size, grid))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
                    assertThat(e.details()).containsEntry("field", "provinces").containsEntry("min", 50L);
                });
    }

    @Test
    void gridSmallerThanProvincesIsRejected() {
        WorldSize size = size(TestMaps.PANGAEA, 1, 200);
        MapGrid grid = VoronoiGrid.generate(Rng.of(1), TestMaps.GRID, 199);

        assertThatThrownBy(() -> ContinentGenerator.generate(Rng.of(1), PACK, size, grid))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }

    @Test
    void unknownTemplateIsRejected() {
        WorldSize size = new WorldSize(2, 2, new MapTemplateId("ring_world"), 1, 60, 100, 500, List.of());
        MapGrid grid = VoronoiGrid.generate(Rng.of(1), TestMaps.GRID, 200);

        assertThatThrownBy(() -> ContinentGenerator.generate(Rng.of(1), PACK, size, grid))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE));
    }

    @Test
    void landWithoutRoomForSeaIsInvariantViolation() {
        // Уся сітка — суходіл трьох материків: між ними не лишається моря.
        WorldSize size = size(TestMaps.ARCHIPELAGO, 3, 60);
        MapGrid grid = VoronoiGrid.generate(Rng.of(1), TestMaps.GRID, 60);

        assertThatThrownBy(() -> ContinentGenerator.generate(Rng.of(1), PACK, size, grid))
                .isInstanceOf(InvariantViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {Advantage.MIN, 0, Advantage.MAX})
    void advantageDoesNotChangeSizeWheel(int advantage) {
        // Сектори ваги материка — PARTIAL: навіть гранична перевага лишає рівні ваги.
        assertThat(Wheel.applyAdvantage(
                        WorldSizeWheel.rangeSectors("size_", new CountRange(1, 4)), advantage, Wheel.MAX_STRENGTH))
                .extracting(Sector::weightBp)
                .containsExactly(2500, 2500, 2500, 2500);
    }

    static WorldSize size(MapTemplateDef template, int continents, int provinces) {
        return new WorldSize(2, 2, template.id(), continents, 60, provinces, 500, List.of());
    }

    static MapGrid grid(long seed, MapTemplateDef template, WorldSize size) {
        return VoronoiGrid.generate(Rng.of(seed).fork("grid"), TestMaps.GRID, template.gridCells(size.provinces()));
    }
}
