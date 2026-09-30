package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import kolo.engine.content.AreaLevelDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.content.TestMaps;
import kolo.engine.error.ErrorCode;
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

class PlacementGeneratorTest {

    private static final ContentPack PACK = TestNames.PACK;
    private static final int MIN_PROVINCES = TestMaps.PLACEMENT.minProvinces();
    private static final AreaLevelDef SMALL = TestMaps.PLACEMENT.areas().get(0);
    private static final AreaLevelDef MEDIUM = TestMaps.PLACEMENT.areas().get(1);
    private static final AreaLevelDef LARGE = TestMaps.PLACEMENT.areas().get(2);

    @Test
    void everyCountryGetsConnectedTerritoryOnItsContinent() {
        for (long seed = 0; seed < 30; seed++) {
            World world = world(seed, TestMaps.ARCHIPELAGO, 2, 2 + (int) (seed % 8), 3 + (int) (seed % 3), 400, 1_000);

            PlacementMap placement =
                    PlacementGenerator.generate(Rng.of(seed), PACK, world.size(), world.grid(), world.continents());

            PlacementChecks.assertValid(world.grid(), world.continents(), placement, world.size(), MIN_PROVINCES);
        }
    }

    @Test
    void countriesOnSingleContinentTakeAllLandExceptUnclaimedShare() {
        World world = world(3, TestMaps.PANGAEA, 3, 3, 1, 300, 1_000);

        PlacementMap placement =
                PlacementGenerator.generate(Rng.of(3), PACK, world.size(), world.grid(), world.continents());

        PlacementChecks.assertValid(world.grid(), world.continents(), placement, world.size(), MIN_PROVINCES);
        assertThat(placement.countries().stream()
                        .mapToInt(PlacedCountry::target)
                        .sum())
                .isEqualTo(270);
        assertThat(placement.countries())
                .allSatisfy(country -> assertThat(country.cells()).hasSize(country.target()));
        assertThat(placement.claimedCells()).isEqualTo(270);
    }

    @Test
    void countriesGetAreaTagsAndTargetsFollowRolledAreas() {
        World world = world(5, TestMaps.PANGAEA, 2, 6, 1, 400, 500);

        PlacementMap placement =
                PlacementGenerator.generate(Rng.of(5), PACK, world.size(), world.grid(), world.continents());

        AreaLevelDef[] areas = placement.countries().stream()
                .map(country -> TestMaps.PLACEMENT.area(country.area()).orElseThrow())
                .toArray(AreaLevelDef[]::new);
        int[] targets = PlacementGenerator.targets(new int[] {380}, new int[8], areas, MIN_PROVINCES);
        for (int i = 0; i < 8; i++) {
            PlacedCountry country = placement.countries().get(i);
            assertThat(country.target()).isEqualTo(targets[i]);
            assertThat(country.tags()).isEqualTo(areas[i].tags());
        }
    }

    @Test
    void rollsAreNeutralContinentAndAreaWheels() {
        World world = world(9, TestMaps.ARCHIPELAGO, 1, 3, 3, 300, 1_000);

        PlacementMap placement =
                PlacementGenerator.generate(Rng.of(9), PACK, world.size(), world.grid(), world.continents());

        assertThat(placement.rolls()).hasSize(8).allSatisfy(roll -> {
            assertThat(roll.advantage()).isZero();
            assertThat(roll.modifiers()).isEmpty();
            assertThat(roll.turn()).isZero();
            assertThat(roll.sectors())
                    .allSatisfy(sector -> assertThat(sector.tier()).isEqualTo(OutcomeTier.PARTIAL));
        });
        // Перша держава: усі материки вільні, ваги — пропорційні їхнім часткам суходолу.
        assertThat(placement.rolls().getFirst().sectors())
                .extracting(RolledSector::id)
                .containsExactly("continent_0", "continent_1", "continent_2");
        assertThat(placement.rolls().get(1).sectors())
                .extracting(RolledSector::id, RolledSector::quality)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("small", 20),
                        org.assertj.core.groups.Tuple.tuple("medium", 50),
                        org.assertj.core.groups.Tuple.tuple("large", 80));
    }

    @Test
    void sameSeedGivesSamePlacement() {
        World world = world(21, TestMaps.ARCHIPELAGO, 3, 5, 4, 350, 800);

        assertThat(PlacementGenerator.generate(Rng.of(21), PACK, world.size(), world.grid(), world.continents()))
                .isEqualTo(
                        PlacementGenerator.generate(Rng.of(21), PACK, world.size(), world.grid(), world.continents()));
    }

    @Test
    void continentsOfAnotherGridAreRejected() {
        World world = world(1, TestMaps.PANGAEA, 1, 1, 1, 100, 500);
        MapGrid other = VoronoiGrid.generate(
                Rng.of(2), TestMaps.GRID, world.grid().cells().size() + 1);

        assertThatThrownBy(() -> PlacementGenerator.generate(Rng.of(1), PACK, world.size(), other, world.continents()))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
                    assertThat(e.details()).containsEntry("field", "continents");
                });
    }

    @Test
    void capacitiesLeaveUnclaimedShareOnEveryContinent() {
        World world = world(4, TestMaps.ARCHIPELAGO, 1, 1, 3, 300, 1_500);

        int[] capacities = PlacementGenerator.capacities(world.continents(), 1_500);

        for (int c = 0; c < capacities.length; c++) {
            int land = world.continents().continents().get(c).cells().size();
            assertThat(capacities[c]).isEqualTo(land * 85 / 100);
        }
    }

    @Test
    void targetsGiveMinimumAndSplitRestByAreas() {
        // 100 провінцій: по 2 мінімуму, решта 96 — як 50 : 200.
        assertThat(PlacementGenerator.targets(new int[] {100}, new int[] {0, 0}, new AreaLevelDef[] {SMALL, LARGE}, 2))
                .containsExactly(21, 79);
        // Рівні залишки — менший номер першим.
        assertThat(PlacementGenerator.targets(
                        new int[] {11}, new int[] {0, 0, 0}, new AreaLevelDef[] {MEDIUM, MEDIUM, MEDIUM}, 2))
                .containsExactly(4, 4, 3);
    }

    @Test
    void fullContinentPassesRestToOthers() {
        // Материк 0 вміщує 30 на двох, материк 1 — 100 на одну: обидва заповнюються, загалом 130.
        assertThat(PlacementGenerator.targets(
                        new int[] {30, 100}, new int[] {0, 0, 1}, new AreaLevelDef[] {MEDIUM, MEDIUM, MEDIUM}, 5))
                .containsExactly(15, 15, 100);
        // Лише один материк має держави: другий лишається нічийним цілком.
        assertThat(PlacementGenerator.targets(new int[] {50, 50}, new int[] {0}, new AreaLevelDef[] {SMALL}, 5))
                .containsExactly(50);
        // Материк 0 переповнений великими державами: він віддає лише свою частку, решта — державам материка 1.
        int[] targets = PlacementGenerator.targets(
                new int[] {20, 200}, new int[] {0, 0, 1, 1}, new AreaLevelDef[] {LARGE, LARGE, SMALL, SMALL}, 2);
        assertThat(targets).containsExactly(10, 10, 100, 100);
    }

    @Test
    void distributeUsesLargestRemainders() {
        assertThat(PlacementGenerator.distribute(10, new long[] {1, 1, 1})).containsExactly(4, 3, 3);
        assertThat(PlacementGenerator.distribute(5, new long[] {0, 1, 1})).containsExactly(0, 3, 2);
        assertThat(PlacementGenerator.distribute(7, new long[] {2, 5})).containsExactly(2, 5);
        assertThat(PlacementGenerator.distribute(0, new long[] {3})).containsExactly(0);
    }

    @Test
    void continentWeightsFollowFreeSpace() {
        // Перша держава: вага пропорційна частці материка.
        assertThat(PlacementGenerator.continentSectors(new int[] {30, 90}, new int[2], new long[2], 2, 5))
                .extracting(Sector::id, Sector::weightBp)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("continent_0", 2_500),
                        org.assertj.core.groups.Tuple.tuple("continent_1", 7_500));
        // Материк 1 уже обрала держава на всю його частку: вільний лише материк 0.
        assertThat(PlacementGenerator.continentSectors(new int[] {50, 50}, new int[] {0, 1}, new long[] {0, 200}, 2, 5))
                .extracting(Sector::id)
                .containsExactly("continent_0");
        // Материк 0 не вміщує ще одну державу з мінімумом 5.
        assertThat(PlacementGenerator.continentSectors(new int[] {8, 90}, new int[] {1, 0}, new long[] {100, 0}, 3, 5))
                .extracting(Sector::id)
                .containsExactly("continent_1");
        // Вільного простору ніде немає: вага — частка материка.
        assertThat(PlacementGenerator.continentSectors(
                        new int[] {100, 300}, new int[] {1, 1}, new long[] {300, 300}, 2, 5))
                .extracting(Sector::weightBp)
                .containsExactly(2_500, 7_500);
        // Жоден материк не вміщує ще одну державу.
        assertThat(PlacementGenerator.continentSectors(new int[] {9}, new int[] {1}, new long[] {100}, 2, 5))
                .isEmpty();
    }

    @Test
    void playersArePlacedFirstThenLargestNpc() {
        // Місцеві номери: 0 — гравець (глобальний 1), 1 і 2 — NPC (глобальні 3 і 4) з цілями 40 і 50.
        assertThat(PlacementGenerator.order(List.of(1, 3, 4), new int[] {10, 40, 50}, 2))
                .containsExactly(0, 2, 1);
        // Гравці — у порядку генерації, навіть якщо менші.
        assertThat(PlacementGenerator.order(List.of(0, 1, 5), new int[] {10, 90, 50}, 2))
                .containsExactly(0, 1, 2);
        // Рівні цілі NPC — менший номер першим.
        assertThat(PlacementGenerator.order(List.of(2, 3), new int[] {30, 30}, 2))
                .containsExactly(0, 1);
    }

    @Test
    void componentsSplitMaskIntoConnectedParts() {
        MapGrid grid = VoronoiGrid.generate(Rng.of(1), TestMaps.GRID, 60);
        boolean[] all = new boolean[60];
        Arrays.fill(all, true);
        int[] component = new int[60];

        assertThat(PlacementGenerator.components(grid, all, component)).containsExactly(60);
        assertThat(component).containsOnly(0);

        boolean[] none = new boolean[60];
        assertThat(PlacementGenerator.components(grid, none, component)).isEmpty();
        assertThat(component).containsOnly(-1);
    }

    @Test
    void seedPrefersFreeInlandFarFromClaimedLand() {
        MapGrid grid = VoronoiGrid.generate(Rng.of(8), TestMaps.GRID, 600);
        boolean[] land = new boolean[600];
        Arrays.fill(land, true);
        int[] owners = new int[600];
        Arrays.fill(owners, PlacementMap.NONE);
        int[] fromCoast = PlacementGenerator.distances(grid, land, PlacementGenerator.coast(grid, land));
        List<Integer> claimed = List.of(0);
        owners[0] = 0;

        int seed = PlacementGenerator.seed(Rng.of(8), grid, land, owners, fromCoast, claimed, 50);

        assertThat(owners[seed]).isEqualTo(PlacementMap.NONE);
        assertThat(grid.cells().get(seed).edge()).isFalse();
        assertThat(PlacementGenerator.distances(grid, land, new int[] {0})[seed])
                .isGreaterThan(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {Advantage.MIN, 0, Advantage.MAX})
    void advantageDoesNotChangeAreaWheel(int advantage) {
        // Сектори площі — PARTIAL: навіть гранична перевага лишає ваги контенту.
        List<Sector<AreaLevelDef>> sectors = PlacementGenerator.areaSectors(TestMaps.PLACEMENT);

        assertThat(Wheel.applyAdvantage(sectors, advantage, Wheel.MAX_STRENGTH))
                .extracting(Sector::weightBp)
                .containsExactly(3_000, 5_000, 2_000);
    }

    static World world(
            long seed, MapTemplateDef template, int players, int npc, int continents, int provinces, int unclaimedBp) {
        WorldSize size = new WorldSize(players, npc, template.id(), continents, 60, provinces, unclaimedBp, List.of());
        MapGrid grid = VoronoiGrid.generate(Rng.of(seed).fork("grid"), TestMaps.GRID, template.gridCells(provinces));
        ContinentMap map = ContinentGenerator.generate(Rng.of(seed).fork("continents"), PACK, size, grid);
        return new World(size, grid, map);
    }

    record World(WorldSize size, MapGrid grid, ContinentMap continents) {}
}
