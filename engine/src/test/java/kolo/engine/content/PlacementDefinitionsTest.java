package kolo.engine.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.name.TestNames;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class PlacementDefinitionsTest {

    @Test
    void areaLevelKeepsFieldsAndTags() {
        AreaLevelDef area = TestMaps.area("large", 200, 20, 80);

        assertThat(area.id()).isEqualTo(new AreaLevelId("large"));
        assertThat(area.sharePct()).isEqualTo(200);
        assertThat(area.weight()).isEqualTo(20);
        assertThat(area.quality()).isEqualTo(80);
        assertThat(area.tags()).containsExactly("large_country");
    }

    @Test
    void areaLevelRejectsOutOfRangeNumbers() {
        assertFails(() -> TestMaps.area("zero", 0, 1, 50), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> TestMaps.area("vast", AreaLevelDef.MAX_SHARE_PCT + 1, 1, 50), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> TestMaps.area("weightless", 100, 0, 50), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> TestMaps.area("heavy", 100, AreaLevelDef.MAX_WEIGHT + 1, 50), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> TestMaps.area("perfect", 100, 1, 101), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new AreaLevelDef(new AreaLevelId("blank"), " ", "Опис", 100, 1, 50, List.of()),
                ErrorCode.BLANK_VALUE);
    }

    @Test
    void areaLevelsMustGrowStrictly() {
        AreaLevelDef.checkFollows(TestMaps.area("small", 50, 1, 20), TestMaps.area("large", 51, 1, 10));

        assertThatThrownBy(() ->
                        AreaLevelDef.checkFollows(TestMaps.area("small", 50, 1, 20), TestMaps.area("large", 50, 1, 80)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.OUT_OF_ORDER);
                    assertThat(e.details()).containsEntry("field", "area_level.large.share_pct");
                });
    }

    @Test
    void placementKeepsAreasInContentOrder() {
        PlacementDef placement = TestMaps.PLACEMENT;

        assertThat(placement.areas())
                .extracting(AreaLevelDef::id)
                .containsExactly(new AreaLevelId("small"), new AreaLevelId("medium"), new AreaLevelId("large"));
        assertThat(placement.area(new AreaLevelId("medium")))
                .map(AreaLevelDef::sharePct)
                .contains(100);
        assertThat(placement.area(new AreaLevelId("colossal"))).isEmpty();
        assertThat(placement.areasById())
                .containsOnlyKeys(new AreaLevelId("large"), new AreaLevelId("medium"), new AreaLevelId("small"));
        assertThat(placement.producedTags()).containsExactly("large_country", "medium_country", "small_country");
    }

    @Test
    void placementRejectsEmptyDuplicateAndUnorderedAreas() {
        AreaLevelDef small = TestMaps.area("small", 50, 1, 20);
        AreaLevelDef large = TestMaps.area("large", 200, 1, 80);

        assertFails(() -> new PlacementDef(List.of(), 2, 50, 3), ErrorCode.EMPTY_COLLECTION);
        assertFails(
                () -> new PlacementDef(List.of(small, TestMaps.area("small", 100, 1, 50)), 2, 50, 3),
                ErrorCode.DUPLICATE_ID);
        assertFails(() -> new PlacementDef(List.of(large, small), 2, 50, 3), ErrorCode.OUT_OF_ORDER);
    }

    @Test
    void placementRejectsOutOfRangeNumbers() {
        List<AreaLevelDef> areas = TestMaps.PLACEMENT.areas();

        assertFails(() -> new PlacementDef(areas, 0, 50, 3), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new PlacementDef(areas, PlacementDef.MAX_MIN_PROVINCES + 1, 50, 3), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new PlacementDef(areas, 2, PlacementDef.MAX_ROUGHNESS + 1, 3), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new PlacementDef(areas, 2, 50, 0), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new PlacementDef(areas, 2, 50, PlacementDef.MAX_NOISE_CELLS + 1), ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void packRequiresSmallestContinentToHoldOneCountry() {
        // Материк — щонайменше 10 провінцій, нічийних до 15%: держав уміщує 8 провінцій, не 9.
        TestNames.pack(TestMaps.content(TestMaps.placement(8)), TestMaps.BALANCE);

        assertThatThrownBy(() -> TestNames.pack(TestMaps.content(TestMaps.placement(9)), TestMaps.BALANCE))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
                    assertThat(e.details())
                            .containsEntry("field", "placement.min_provinces")
                            .containsEntry("value", 9)
                            .containsEntry("max", 8L);
                });
    }

    @Test
    void packRequiresEveryWorldToHoldMinimumOfEveryCountry() {
        // Материки великі (кожен ≥ 30), але 40 держав по 60 провінцій, обрізані до 400: 340 − 5 < 8 × 45.
        MapContent map = new MapContent(
                List.of(TestMaps.PANGAEA, TestMaps.ARCHIPELAGO),
                TestMaps.GRID,
                new ContinentsDef(new CountRange(1, 3), 60, 50, 4),
                TestMaps.RELIEF,
                TestMaps.CLIMATE,
                TestMaps.SEA,
                TestMaps.RIVERS,
                TestMaps.FERTILITY,
                TestMaps.placement(8),
                TestMaps.GEOGRAPHY,
                TestMaps.POPULATION);

        assertThatThrownBy(() -> TestNames.pack(map, TestMaps.world(new CountRange(300, 400))))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
                    assertThat(e.details()).containsKey("countries").containsKey("min");
                });
    }

    private static void assertFails(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
