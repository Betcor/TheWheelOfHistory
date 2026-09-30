package kolo.engine.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Climate;
import kolo.engine.state.Terrain;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class ResourceDefinitionsTest {

    private static final TreeMap<Climate, Integer> NO_CLIMATES = new TreeMap<>();

    @Test
    void terrainSuitabilityIsMultipliedByClimate() {
        DepositDef def = DepositDef.byTerrain(
                TestResources.terrains(Map.of(Terrain.DESERT, 40, Terrain.PLAIN, 15)),
                new TreeMap<>(Map.of(Climate.ARID, 150, Climate.POLAR, 0)));

        assertThat(def.suitability(Climate.ARID, Terrain.DESERT, 0)).isEqualTo(60);
        // Пояс без значення — 100%, множення — вниз.
        assertThat(def.suitability(Climate.TEMPERATE, Terrain.PLAIN, 90)).isEqualTo(15);
        assertThat(def.suitability(Climate.ARID, Terrain.PLAIN, 0)).isEqualTo(22);
        assertThat(def.suitability(Climate.POLAR, Terrain.DESERT, 0)).isZero();
        // Місцевість без значення — 0.
        assertThat(def.suitability(Climate.ARID, Terrain.MOUNTAINS, 100)).isZero();
    }

    @Test
    void fertilitySuitabilityStartsAtThreshold() {
        DepositDef def = DepositDef.byFertility(60);

        assertThat(def.suitability(Climate.POLAR, Terrain.MOUNTAINS, 59)).isZero();
        assertThat(def.suitability(Climate.POLAR, Terrain.MOUNTAINS, 60)).isEqualTo(60);
        assertThat(def.suitability(Climate.TEMPERATE, Terrain.PLAIN, 100)).isEqualTo(100);
        assertThat(DepositDef.byFertility(0).suitability(Climate.ARID, Terrain.DESERT, 0))
                .isZero();
    }

    @Test
    void maximumSuitabilityFitsTheLimit() {
        DepositDef def = DepositDef.byTerrain(
                TestResources.terrains(Map.of(Terrain.FOREST, DepositDef.MAX_TERRAIN)),
                new TreeMap<>(Map.of(Climate.TROPICAL, DepositDef.MAX_CLIMATE_PCT)));

        assertThat(def.suitability(Climate.TROPICAL, Terrain.FOREST, 0)).isEqualTo(DepositDef.MAX_SUITABILITY);
    }

    @Test
    void fertilityCannotBeMixedWithTerrainsOrClimates() {
        assertThatThrownBy(() -> new DepositDef(
                        TestResources.terrains(Map.of(Terrain.PLAIN, 10)), NO_CLIMATES, OptionalInt.of(50)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.CONFLICTING_FIELDS);
                    assertThat(e.details())
                            .containsExactly(
                                    entry("field", "deposits.fertility_from"), entry("value", "deposits.terrains"));
                });
        assertThatThrownBy(() ->
                        new DepositDef(new TreeMap<>(), new TreeMap<>(Map.of(Climate.ARID, 100)), OptionalInt.of(50)))
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        e -> assertThat(e.details()).contains(entry("value", "deposits.climates")));
    }

    @Test
    void resourceMustBeSomewhere() {
        assertFails(() -> DepositDef.byTerrain(new TreeMap<>(), NO_CLIMATES), ErrorCode.EMPTY_COLLECTION);
        assertFails(
                () -> DepositDef.byTerrain(TestResources.terrains(Map.of(Terrain.PLAIN, 0)), NO_CLIMATES),
                ErrorCode.EMPTY_COLLECTION);
        // Лише множники поясів — теж нікуди.
        assertFails(
                () -> DepositDef.byTerrain(new TreeMap<>(), new TreeMap<>(Map.of(Climate.ARID, 200))),
                ErrorCode.EMPTY_COLLECTION);
    }

    @Test
    void numbersMustBeInRange() {
        assertFails(
                () -> DepositDef.byTerrain(
                        TestResources.terrains(Map.of(Terrain.PLAIN, DepositDef.MAX_TERRAIN + 1)), NO_CLIMATES),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> DepositDef.byTerrain(TestResources.terrains(Map.of(Terrain.PLAIN, -1)), NO_CLIMATES),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> DepositDef.byTerrain(
                        TestResources.terrains(Map.of(Terrain.PLAIN, 10)),
                        new TreeMap<>(Map.of(Climate.ARID, DepositDef.MAX_CLIMATE_PCT + 1))),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> DepositDef.byFertility(-1), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> DepositDef.byFertility(FertilityDef.MAX_VALUE + 1), ErrorCode.VALUE_OUT_OF_RANGE);
        DepositDef def = DepositDef.byFertility(10);
        assertFails(() -> def.suitability(Climate.ARID, Terrain.PLAIN, 101), ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void tablesAreCopied() {
        TreeMap<Terrain, Integer> terrains = TestResources.terrains(Map.of(Terrain.HILLS, 30));
        DepositDef def = DepositDef.byTerrain(terrains, NO_CLIMATES);
        terrains.put(Terrain.PLAIN, 50);

        assertThat(def.terrains()).containsOnlyKeys(Terrain.HILLS);
        assertThatThrownBy(() -> def.terrains().put(Terrain.PLAIN, 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resourceWithoutDepositsIsNotOnTheMap() {
        ResourceDef spice = new ResourceDef(TestResources.SPICE, "Прянощі", List.of());
        assertThat(spice.deposits()).isEmpty();
        ResourceDef grain =
                new ResourceDef(TestResources.GRAIN, "Зерно", List.of(), Optional.of(DepositDef.byFertility(40)));
        assertThat(grain.deposits()).hasValue(DepositDef.byFertility(40));
    }

    @Test
    void depositCountFollowsProvinceTable() {
        ResourceBalanceDef balance = new ResourceBalanceDef(List.of(
                new ResourceCountDef(50, new CountRange(1, 2)),
                new ResourceCountDef(100, new CountRange(2, 3)),
                new ResourceCountDef(1000, new CountRange(3, 5))));

        assertThat(balance.deposits(1)).isEqualTo(new CountRange(1, 2));
        assertThat(balance.deposits(50)).isEqualTo(new CountRange(1, 2));
        assertThat(balance.deposits(51)).isEqualTo(new CountRange(2, 3));
        assertThat(balance.deposits(1000)).isEqualTo(new CountRange(3, 5));
        // Більше, ніж в останньому рядку, — як в останньому.
        assertThat(balance.deposits(5000)).isEqualTo(new CountRange(3, 5));
        assertFails(() -> balance.deposits(0), ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void depositTableMustBeNonEmptyAndOrdered() {
        assertFails(() -> new ResourceBalanceDef(List.of()), ErrorCode.EMPTY_COLLECTION);
        assertThatThrownBy(() -> new ResourceBalanceDef(List.of(
                        new ResourceCountDef(50, new CountRange(1, 2)),
                        new ResourceCountDef(50, new CountRange(2, 3)))))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.OUT_OF_ORDER);
                    assertThat(e.details()).contains(entry("field", "resources.count[1].max_provinces"));
                });
    }

    @Test
    void depositCountMustBeInRange() {
        assertFails(() -> new ResourceCountDef(0, new CountRange(1, 2)), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new ResourceCountDef(10, new CountRange(0, 2)), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new ResourceCountDef(10, new CountRange(1, ResourceCountDef.MAX_DEPOSITS + 1)),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertThat(new ResourceCountDef(10, new CountRange(1, ResourceCountDef.MAX_DEPOSITS))
                        .deposits()
                        .max())
                .isEqualTo(ResourceCountDef.MAX_DEPOSITS);
    }

    private static void assertFails(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
