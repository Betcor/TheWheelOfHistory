package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.content.CoastLevelId;
import kolo.engine.content.ContentPack;
import kolo.engine.content.TerrainTagDef;
import kolo.engine.content.TestMaps;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.map.PlacedCountry;
import kolo.engine.generation.name.TestNames;
import kolo.engine.state.Terrain;
import org.junit.jupiter.api.Test;

class GeographyTest {

    private static final ContentPack PACK = TestNames.PACK;
    private static final CoastLevelId COASTAL = new CoastLevelId("coastal");

    @Test
    void geographyIsSummaryOfTerritory() {
        boolean landlocked = false;
        boolean maritime = false;
        for (long seed = 0; seed < 20; seed++) {
            TestTerritory.World world = TestTerritory.world(PACK, seed);
            for (int i = 0; i < world.placement().countries().size(); i++) {
                PlacedCountry country = world.placement().countries().get(i);
                StartGeography geography = world.geography(PACK, i);

                GeographyChecks.assertValid(
                        PACK, country.cells(), world.sea(), world.climate(), world.fertility(), geography);
                landlocked |= geography.tags().contains("landlocked");
                maritime |= geography.tags().contains("maritime");
            }
        }
        assertThat(landlocked).isTrue();
        assertThat(maritime).isTrue();
    }

    @Test
    void terrainRulesAddTagsFromThreshold() {
        List<Terrain> all = List.of(Terrain.values());
        ContentPack pack = TestNames.pack(
                TestMaps.content(TestMaps.geography(List.of(
                        new TerrainTagDef(all, 100, List.of("everywhere")),
                        new TerrainTagDef(List.of(Terrain.SWAMP), 100, List.of("all_swamp"))))),
                TestMaps.BALANCE);
        TestTerritory.World world = TestTerritory.world(pack, 3);

        for (int i = 0; i < world.placement().countries().size(); i++) {
            StartGeography geography = world.geography(pack, i);
            assertThat(geography.tags()).contains("everywhere").doesNotContain("all_swamp");
            GeographyChecks.assertValid(
                    pack,
                    world.placement().countries().get(i).cells(),
                    world.sea(),
                    world.climate(),
                    world.fertility(),
                    geography);
        }
    }

    @Test
    void orderAndRepeatsOfProvincesDoNotMatter() {
        TestTerritory.World world = TestTerritory.world(PACK, 5);
        List<Integer> cells = world.placement().countries().getFirst().cells();
        List<Integer> shuffled = new java.util.ArrayList<>(cells.reversed());
        shuffled.add(cells.getFirst());

        assertThat(Geography.generate(PACK, shuffled, world.sea(), world.climate(), world.fertility()))
                .isEqualTo(world.geography(PACK, 0));
    }

    @Test
    void waterEmptyAndForeignProvincesAreRejected() {
        TestTerritory.World world = TestTerritory.world(PACK, 7);
        int water = 0;
        while (world.fertility().fertility(water).isPresent()) {
            water++;
        }
        int outside = world.grid().cells().size();

        for (List<Integer> provinces : List.of(List.of(water), List.of(outside), List.of(-1))) {
            assertThatThrownBy(
                            () -> Geography.generate(PACK, provinces, world.sea(), world.climate(), world.fertility()))
                    .isInstanceOfSatisfying(ValidationException.class, e -> {
                        assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
                        assertThat(e.details()).containsEntry("field", "provinces");
                    });
        }
        assertThatThrownBy(() -> Geography.generate(PACK, List.of(), world.sea(), world.climate(), world.fertility()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.EMPTY_COLLECTION));
    }

    @Test
    void percentsRoundUp() {
        assertThat(StartGeography.percentUp(0, 5)).isZero();
        assertThat(StartGeography.percentUp(1, 200)).isEqualTo(1);
        assertThat(StartGeography.percentUp(1, 3)).isEqualTo(34);
        assertThat(StartGeography.percentUp(99, 100)).isEqualTo(99);
        assertThat(StartGeography.percentUp(5, 5)).isEqualTo(100);

        StartGeography geography =
                geography(List.of(1, 2, 3), List.of(2), terrains(Terrain.PLAIN, 2, Terrain.HILLS, 1));
        assertThat(geography.coastalPct()).isEqualTo(34);
        assertThat(geography.terrainPct(List.of(Terrain.HILLS))).isEqualTo(34);
        assertThat(geography.terrainPct(List.of(Terrain.HILLS, Terrain.PLAIN))).isEqualTo(100);
        assertThat(geography.terrainPct(List.of(Terrain.SWAMP))).isZero();
    }

    @Test
    void startGeographyValidatesFields() {
        TreeMap<Terrain, Integer> terrains = terrains(Terrain.PLAIN, 2, Terrain.HILLS, 1);
        assertFails(() -> geography(List.of(), List.of(), new TreeMap<>()), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> geography(List.of(2, 1, 3), List.of(), terrains), ErrorCode.OUT_OF_ORDER);
        assertFails(() -> geography(List.of(1, 2, 3), List.of(4), terrains), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> geography(List.of(1, 2, 3), List.of(), terrains(Terrain.PLAIN, 2, Terrain.HILLS, 2)),
                ErrorCode.VALUE_OUT_OF_RANGE);
        // Переважна місцевість — лише тип з найбільшою кількістю.
        assertFails(
                () -> new StartGeography(
                        List.of(1, 2, 3), List.of(), COASTAL, List.of(), terrains, Terrain.HILLS, 50, new TreeSet<>()),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new StartGeography(
                        List.of(1, 2, 3), List.of(), COASTAL, List.of(), terrains, Terrain.PLAIN, 101, new TreeSet<>()),
                ErrorCode.VALUE_OUT_OF_RANGE);
    }

    private static StartGeography geography(
            List<Integer> provinces, List<Integer> coastal, TreeMap<Terrain, Integer> terrains) {
        return new StartGeography(provinces, coastal, COASTAL, List.of(), terrains, Terrain.PLAIN, 50, new TreeSet<>());
    }

    private static TreeMap<Terrain, Integer> terrains(Terrain a, int countA, Terrain b, int countB) {
        return new TreeMap<>(Map.of(a, countA, b, countB));
    }

    private static void assertFails(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
