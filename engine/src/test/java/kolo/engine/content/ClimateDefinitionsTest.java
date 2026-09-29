package kolo.engine.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Climate;
import kolo.engine.state.Cover;
import kolo.engine.state.Relief;
import kolo.engine.state.Terrain;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class ClimateDefinitionsTest {

    private static final ClimateDef CLIMATE = TestMaps.CLIMATE;
    private static final List<Climate> WET = List.of(Climate.BOREAL, Climate.TEMPERATE, Climate.TROPICAL);
    private static final List<Relief> LOW = List.of(Relief.PLAIN, Relief.HILLS);

    @Test
    void climateFollowsThresholds() {
        // Полярний нижче 15, бореальний нижче 35, посушливий — волога нижче 30, тропічний з 70.
        assertThat(CLIMATE.climate(0, 100)).isEqualTo(Climate.POLAR);
        assertThat(CLIMATE.climate(14, 0)).isEqualTo(Climate.POLAR);
        assertThat(CLIMATE.climate(15, 0)).isEqualTo(Climate.BOREAL);
        assertThat(CLIMATE.climate(34, 100)).isEqualTo(Climate.BOREAL);
        assertThat(CLIMATE.climate(35, 29)).isEqualTo(Climate.ARID);
        assertThat(CLIMATE.climate(100, 29)).isEqualTo(Climate.ARID);
        assertThat(CLIMATE.climate(35, 30)).isEqualTo(Climate.TEMPERATE);
        assertThat(CLIMATE.climate(69, 100)).isEqualTo(Climate.TEMPERATE);
        assertThat(CLIMATE.climate(70, 30)).isEqualTo(Climate.TROPICAL);
        assertThat(CLIMATE.climate(ClimateDef.MAX_VALUE, ClimateDef.MAX_VALUE)).isEqualTo(Climate.TROPICAL);
        assertFails(() -> CLIMATE.climate(-1, 50), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> CLIMATE.climate(50, ClimateDef.MAX_VALUE + 1), ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void firstMatchingCoverInContentOrderWins() {
        // Болото (волога ≥ 80, висота ≤ 30) перевіряється раніше за ліс (волога ≥ 55).
        assertThat(CLIMATE.cover(Climate.TEMPERATE, Relief.PLAIN, 90, 20)).contains(Cover.SWAMP);
        assertThat(CLIMATE.cover(Climate.TEMPERATE, Relief.PLAIN, 90, 31)).contains(Cover.FOREST);
        assertThat(CLIMATE.cover(Climate.TEMPERATE, Relief.HILLS, 90, 20)).contains(Cover.FOREST);
        assertThat(CLIMATE.cover(Climate.TEMPERATE, Relief.PLAIN, 54, 20)).isEmpty();
        assertThat(CLIMATE.cover(Climate.POLAR, Relief.HILLS, 90, 50)).contains(Cover.TUNDRA);
        assertThat(CLIMATE.cover(Climate.ARID, Relief.PLAIN, 15, 20)).contains(Cover.DESERT);
        assertThat(CLIMATE.cover(Climate.ARID, Relief.PLAIN, 16, 20)).isEmpty();
        assertThat(CLIMATE.cover(Climate.POLAR, Relief.MOUNTAINS, 50, 90)).isEmpty();

        ClimateDef forestFirst = TestMaps.climate(
                CLIMATE.worlds(),
                List.of(
                        TestMaps.cover(Cover.FOREST, WET, LOW, 55, 100, 100),
                        TestMaps.cover(Cover.SWAMP, WET, List.of(Relief.PLAIN), 80, 100, 30),
                        TestMaps.cover(Cover.TUNDRA, List.of(Climate.POLAR), LOW, 0, 100, 100),
                        TestMaps.cover(Cover.DESERT, List.of(Climate.ARID), LOW, 0, 15, 100)));
        assertThat(forestFirst.cover(Climate.TEMPERATE, Relief.PLAIN, 90, 20)).contains(Cover.FOREST);
    }

    @Test
    void climateDefFindsZonesAndCovers() {
        assertThat(CLIMATE.zone(Climate.ARID).name()).isEqualTo("Пояс arid");
        assertThat(CLIMATE.coverDef(Cover.SWAMP).height()).isEqualTo(new CountRange(0, 30));
        assertThat(CLIMATE.worlds())
                .extracting(WorldClimateDef::id)
                .containsExactly(
                        new WorldClimateId("cold"), new WorldClimateId("temperate"), new WorldClimateId("warm"));
    }

    @Test
    void thresholdsMustIncrease() {
        assertFails(() -> climate(35, 35, 70, 30), ErrorCode.OUT_OF_ORDER);
        assertFails(() -> climate(15, 70, 70, 30), ErrorCode.OUT_OF_ORDER);
        assertFails(() -> climate(15, 35, ClimateDef.MAX_VALUE + 1, 30), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> climate(-1, 35, 70, 30), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> climate(15, 35, 70, ClimateDef.MAX_VALUE + 1), ErrorCode.VALUE_OUT_OF_RANGE);
        climate(0, 1, ClimateDef.MAX_VALUE, 0);
    }

    @Test
    void worldsMustBeNonEmptyAndUnique() {
        assertFails(() -> TestMaps.climate(List.of()), ErrorCode.EMPTY_COLLECTION);
        assertFails(
                () -> TestMaps.climate(List.of(TestMaps.world("cold", 1, -10), TestMaps.world("cold", 2, 0))),
                ErrorCode.DUPLICATE_ID);
    }

    @Test
    void worldClimateRejectsValuesOutsideLimits() {
        assertFails(() -> TestMaps.world("a", 0, 0), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> TestMaps.world("a", WorldClimateDef.MAX_WEIGHT + 1, 0), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> TestMaps.world("a", 1, WorldClimateDef.MAX_SHIFT + 1), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> TestMaps.world("a", 1, -WorldClimateDef.MAX_SHIFT - 1), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> TestMaps.world("Cold", 1, 0), ErrorCode.INVALID_KEY_FORMAT);
        assertFails(() -> new WorldClimateDef(new WorldClimateId("a"), " ", "Опис", 1, 0), ErrorCode.BLANK_VALUE);
    }

    @Test
    void temperatureAndMoistureRejectValuesOutsideLimits() {
        assertFails(() -> new ClimateTemperatureDef(40, 50, 30, 10), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new ClimateTemperatureDef(ClimateDef.MAX_VALUE + 1, 0, 30, 10), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new ClimateTemperatureDef(90, 0, 101, 10), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new ClimateTemperatureDef(90, 0, 30, -1), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new ClimateMoistureDef(ClimateDef.MAX_VALUE + 1, 10, 20), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new ClimateMoistureDef(80, -1, 20), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new ClimateMoistureDef(80, 10, ClimateDef.MAX_VALUE + 1), ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void zonesMustBeCompleteUniqueAndOrdered() {
        List<ClimateZoneDef> zones = TestMaps.zones();

        assertFails(() -> zones(zones.subList(0, 4)), ErrorCode.MISSING_DEFINITION);
        List<ClimateZoneDef> duplicate = new ArrayList<>(zones);
        duplicate.set(4, zones.get(0));
        assertFails(() -> zones(duplicate), ErrorCode.DUPLICATE_ID);
        List<ClimateZoneDef> swapped = new ArrayList<>(zones);
        swapped.set(0, zones.get(1));
        swapped.set(1, zones.get(0));
        assertFails(() -> zones(swapped), ErrorCode.OUT_OF_ORDER);
        assertFails(() -> new ClimateZoneDef(Climate.ARID, "", "Опис"), ErrorCode.BLANK_VALUE);
    }

    @Test
    void coversMustBeCompleteAndUnique() {
        CoverDef tundra = TestMaps.cover(Cover.TUNDRA, List.of(Climate.POLAR), LOW, 0, 100, 100);
        CoverDef swamp = TestMaps.cover(Cover.SWAMP, WET, List.of(Relief.PLAIN), 80, 100, 30);
        CoverDef desert = TestMaps.cover(Cover.DESERT, List.of(Climate.ARID), LOW, 0, 15, 100);
        CoverDef forest = TestMaps.cover(Cover.FOREST, WET, LOW, 55, 100, 100);

        assertThatThrownBy(() -> TestMaps.climate(CLIMATE.worlds(), List.of(tundra, swamp, desert)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.MISSING_DEFINITION);
                    assertThat(e.details()).containsEntry("value", "forest");
                });
        assertFails(
                () -> TestMaps.climate(CLIMATE.worlds(), List.of(tundra, swamp, desert, forest, swamp)),
                ErrorCode.DUPLICATE_ID);
    }

    @Test
    void coverRejectsMountainsEmptyListsAndBadRanges() {
        assertThatThrownBy(() -> TestMaps.cover(
                        Cover.TUNDRA, List.of(Climate.POLAR), List.of(Relief.PLAIN, Relief.MOUNTAINS), 0, 100, 100))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
                    assertThat(e.details())
                            .containsEntry("field", "cover.tundra.reliefs")
                            .containsEntry("value", "mountains");
                });
        assertFails(() -> TestMaps.cover(Cover.TUNDRA, List.of(), LOW, 0, 100, 100), ErrorCode.EMPTY_COLLECTION);
        assertFails(
                () -> TestMaps.cover(Cover.TUNDRA, List.of(Climate.POLAR), List.of(), 0, 100, 100),
                ErrorCode.EMPTY_COLLECTION);
        assertFails(
                () -> TestMaps.cover(Cover.TUNDRA, List.of(Climate.POLAR, Climate.POLAR), LOW, 0, 100, 100),
                ErrorCode.DUPLICATE_ID);
        assertFails(
                () -> TestMaps.cover(Cover.TUNDRA, List.of(Climate.POLAR), LOW, 0, ClimateDef.MAX_VALUE + 1, 100),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> TestMaps.cover(Cover.TUNDRA, List.of(Climate.POLAR), LOW, 0, 100, ReliefDef.MAX_HEIGHT + 1),
                ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void terrainIsCoverOrRelief() {
        assertThat(Terrain.of(Relief.HILLS, Optional.empty())).isEqualTo(Terrain.HILLS);
        assertThat(Terrain.of(Relief.PLAIN, Optional.of(Cover.SWAMP))).isEqualTo(Terrain.SWAMP);
        for (Relief relief : Relief.values()) {
            assertThat(Terrain.of(relief, Optional.empty()).key()).isEqualTo(relief.key());
        }
        for (Cover cover : Cover.values()) {
            assertThat(cover.terrain().key()).isEqualTo(cover.key());
        }
    }

    @Test
    void keysAreSnakeCase() {
        assertThat(Climate.TROPICAL.key()).isEqualTo("tropical");
        assertThat(Cover.SWAMP.key()).isEqualTo("swamp");
        assertThat(Terrain.MOUNTAINS.key()).isEqualTo("mountains");
    }

    private static ClimateDef climate(int polarBelow, int borealBelow, int tropicalFrom, int aridBelow) {
        return new ClimateDef(
                CLIMATE.worlds(),
                CLIMATE.temperature(),
                CLIMATE.moisture(),
                CLIMATE.noiseCells(),
                polarBelow,
                borealBelow,
                tropicalFrom,
                aridBelow,
                CLIMATE.zones(),
                CLIMATE.covers());
    }

    private static ClimateDef zones(List<ClimateZoneDef> zones) {
        return new ClimateDef(
                CLIMATE.worlds(),
                CLIMATE.temperature(),
                CLIMATE.moisture(),
                CLIMATE.noiseCells(),
                CLIMATE.polarBelow(),
                CLIMATE.borealBelow(),
                CLIMATE.tropicalFrom(),
                CLIMATE.aridBelow(),
                zones,
                CLIMATE.covers());
    }

    private static void assertFails(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
