package kolo.engine.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Terrain;
import kolo.engine.wheel.OutcomeTier;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class GeographyDefinitionsTest {

    @Test
    void coastLevelIsLastWhoseThresholdFits() {
        GeographyDef geography = TestMaps.GEOGRAPHY;

        assertThat(geography.coastLevel(0).id()).isEqualTo(new CoastLevelId("landlocked"));
        assertThat(geography.coastLevel(1).id()).isEqualTo(new CoastLevelId("coastal"));
        assertThat(geography.coastLevel(49).id()).isEqualTo(new CoastLevelId("coastal"));
        assertThat(geography.coastLevel(50).id()).isEqualTo(new CoastLevelId("maritime"));
        assertThat(geography.coastLevel(100).tags()).containsExactly("coastal", "maritime");
        assertFails(() -> geography.coastLevel(101), ErrorCode.VALUE_OUT_OF_RANGE);
        assertThat(geography.producedTags())
                .containsExactly("coastal", "highland", "landlocked", "maritime", "mountainous");
    }

    @Test
    void geographyRejectsEmptyDuplicateUnorderedAndNonZeroFirstCoastLevels() {
        CoastLevelDef none = TestMaps.coast("none", 0, List.of());
        CoastLevelDef some = TestMaps.coast("some", 10, List.of());

        assertFails(() -> new GeographyDef(List.of(), List.of()), ErrorCode.EMPTY_COLLECTION);
        assertFails(
                () -> new GeographyDef(List.of(none, TestMaps.coast("none", 20, List.of())), List.of()),
                ErrorCode.DUPLICATE_ID);
        assertFails(
                () -> new GeographyDef(List.of(none, some, TestMaps.coast("more", 10, List.of())), List.of()),
                ErrorCode.OUT_OF_ORDER);
        assertFails(() -> new GeographyDef(List.of(some), List.of()), ErrorCode.VALUE_OUT_OF_RANGE);
        assertThat(new GeographyDef(List.of(none), List.of()).coastLevel(100)).isEqualTo(none);
    }

    @Test
    void coastLevelRejectsBadFields() {
        assertFails(() -> TestMaps.coast("over", 101, List.of()), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> TestMaps.coast("under", -1, List.of()), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> TestMaps.coast("twice", 0, List.of("a", "a")), ErrorCode.DUPLICATE_ID);
        assertFails(
                () -> new CoastLevelDef(new CoastLevelId("blank"), "Назва", " ", 0, 0, List.of()),
                ErrorCode.BLANK_VALUE);
        assertFails(() -> TestMaps.coast("rich", 0, 101, List.of()), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> TestMaps.coast("poor", 0, -101, List.of()), ErrorCode.VALUE_OUT_OF_RANGE);
        assertThat(TestMaps.coast("poor", 0, -100, List.of()).gdpAdvantage()).isEqualTo(-100);
    }

    @Test
    void terrainTagMatchesFromThreshold() {
        TerrainTagDef rule = new TerrainTagDef(List.of(Terrain.HILLS, Terrain.MOUNTAINS), 50, List.of("highland"));

        assertThat(rule.matches(49)).isFalse();
        assertThat(rule.matches(50)).isTrue();
        assertThat(rule.terrains()).containsExactly(Terrain.HILLS, Terrain.MOUNTAINS);
    }

    @Test
    void terrainTagRejectsEmptyDuplicateAndOutOfRange() {
        List<String> tags = List.of("tag");
        assertFails(() -> new TerrainTagDef(List.of(), 10, tags), ErrorCode.EMPTY_COLLECTION);
        assertFails(() -> new TerrainTagDef(List.of(Terrain.PLAIN, Terrain.PLAIN), 10, tags), ErrorCode.DUPLICATE_ID);
        assertFails(() -> new TerrainTagDef(List.of(Terrain.PLAIN), 0, tags), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new TerrainTagDef(List.of(Terrain.PLAIN), 101, tags), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new TerrainTagDef(List.of(Terrain.PLAIN), 10, List.of()), ErrorCode.EMPTY_COLLECTION);
    }

    @Test
    void populationKeepsLevelsInContentOrder() {
        PopulationDef population = TestMaps.POPULATION;

        assertThat(population.levels())
                .extracting(level -> level.id().value())
                .containsExactly("tiny", "small", "medium", "large", "huge");
        assertThat(population.level(new PopulationLevelId("medium")))
                .map(PopulationLevelDef::populationK)
                .contains(5_000);
        assertThat(population.level(new PopulationLevelId("billions"))).isEmpty();
        assertThat(population.producedTags()).containsExactly("large_population", "small_population");
        assertThat(List.of(
                        population.areaAdvantage(),
                        population.fertilityAdvantage(),
                        population.provinceBase(),
                        population.coastBonus()))
                .containsExactly(20, 100, 10, 20);
    }

    @Test
    void populationRejectsEmptyDuplicateAndUnorderedLevels() {
        PopulationLevelDef few = level("few", 100, OutcomeTier.FAIL);
        PopulationLevelDef many = level("many", 1_000, OutcomeTier.SUCCESS);

        assertFails(() -> new PopulationDef(List.of(), 0, 0, 1, 0), ErrorCode.EMPTY_COLLECTION);
        assertFails(
                () -> new PopulationDef(List.of(few, level("few", 200, OutcomeTier.FAIL)), 0, 0, 1, 0),
                ErrorCode.DUPLICATE_ID);
        assertFails(() -> new PopulationDef(List.of(many, few), 0, 0, 1, 0), ErrorCode.OUT_OF_ORDER);
        // Більше населення не може мати нижчий рівень результату.
        assertFails(
                () -> new PopulationDef(List.of(many, level("more", 2_000, OutcomeTier.FAIL)), 0, 0, 1, 0),
                ErrorCode.OUT_OF_ORDER);
    }

    @Test
    void populationRejectsOutOfRangeNumbers() {
        List<PopulationLevelDef> levels = TestMaps.POPULATION.levels();

        assertFails(() -> new PopulationDef(levels, -1, 0, 1, 0), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new PopulationDef(levels, PopulationDef.MAX_ADVANTAGE + 1, 0, 1, 0),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new PopulationDef(levels, 0, PopulationDef.MAX_ADVANTAGE + 1, 1, 0),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new PopulationDef(levels, 0, 0, 0, 0), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new PopulationDef(levels, 0, 0, 1, PopulationDef.MAX_PROVINCE_WEIGHT + 1),
                ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void populationLevelRejectsBadFields() {
        assertFails(() -> level("zero", 0, OutcomeTier.PARTIAL), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> level("too_many", PopulationLevelDef.MAX_POPULATION_K + 1, OutcomeTier.PARTIAL),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new PopulationLevelDef(
                        new PopulationLevelId("heavy"),
                        "Назва",
                        "Опис",
                        100,
                        OutcomeTier.PARTIAL,
                        PopulationLevelDef.MAX_WEIGHT + 1,
                        50,
                        List.of()),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new PopulationLevelDef(
                        new PopulationLevelId("perfect"), "Назва", "Опис", 100, OutcomeTier.PARTIAL, 1, 101, List.of()),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertThatThrownBy(() -> new PopulationLevelDef(
                        new PopulationLevelId("no_tier"), "Назва", "Опис", 100, null, 1, 50, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    private static PopulationLevelDef level(String id, int populationK, OutcomeTier tier) {
        return new PopulationLevelDef(new PopulationLevelId(id), "Назва", "Опис", populationK, tier, 1, 50, List.of());
    }

    private static void assertFails(ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
