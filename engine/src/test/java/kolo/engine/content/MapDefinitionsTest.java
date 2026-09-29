package kolo.engine.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.TreeMap;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.name.TestNames;
import kolo.engine.state.NpcShare;
import kolo.engine.state.Relief;
import kolo.engine.state.WorldLimits;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class MapDefinitionsTest {

    @Test
    void templateKeepsItsValues() {
        MapTemplateDef template = TestMaps.template("archipelago", 15, 120, 5, 8);

        assertThat(template.id()).isEqualTo(new MapTemplateId("archipelago"));
        assertThat(template.weight()).isEqualTo(15);
        assertThat(template.provincesPct()).isEqualTo(120);
        assertThat(template.continents()).isEqualTo(new CountRange(5, 8));
    }

    @Test
    void templateRejectsValuesOutsideLimits() {
        assertFails(() -> TestMaps.template("a", 0, 100, 1, 1), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> TestMaps.template("a", MapTemplateDef.MAX_WEIGHT + 1, 100, 1, 1), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> TestMaps.template("a", 10, 0, 1, 1), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> TestMaps.template("a", 10, MapTemplateDef.MAX_PROVINCES_PCT + 1, 1, 1),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> TestMaps.template("a", 10, 100, 0, 1), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> TestMaps.template("a", 10, 100, 1, MapTemplateDef.MAX_CONTINENTS + 1),
                ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void templateRequiresNameAndDescription() {
        assertFails(
                () -> new MapTemplateDef(new MapTemplateId("a"), " ", "Опис", 10, 100, 50, new CountRange(1, 1)),
                ErrorCode.BLANK_VALUE);
        assertFails(
                () -> new MapTemplateDef(new MapTemplateId("a"), "Назва", "", 10, 100, 50, new CountRange(1, 1)),
                ErrorCode.BLANK_VALUE);
        assertFails(() -> new MapTemplateId("Pangaea"), ErrorCode.INVALID_KEY_FORMAT);
    }

    @Test
    void templateRejectsLandShareOutsideLimits() {
        assertFails(
                () -> TestMaps.template("a", 10, 100, MapTemplateDef.MIN_LAND_PCT - 1, 1, 1),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> TestMaps.template("a", 10, 100, MapTemplateDef.MAX_LAND_PCT + 1, 1, 1),
                ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void gridCellsAddSeaForLandShareRoundingUp() {
        assertThat(TestMaps.template("a", 10, 100, 50, 1, 1).gridCells(400)).isEqualTo(800);
        assertThat(TestMaps.template("a", 10, 100, 30, 1, 1).gridCells(400)).isEqualTo(1334);
        assertThat(TestMaps.template("a", 10, 100, 30, 1, 1).gridCells(3)).isEqualTo(10);
        assertFails(() -> TestMaps.template("a", 10, 100, 30, 1, 1).gridCells(0), ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void seaSplitsIntoZonesRoundingToNearest() {
        SeaDef sea = new SeaDef(10, 40);

        assertThat(sea.minCells()).isEqualTo(10);
        assertThat(sea.zones(1)).isEqualTo(1);
        assertThat(sea.zones(19)).isEqualTo(1);
        assertThat(sea.zones(59)).isEqualTo(1);
        assertThat(sea.zones(60)).isEqualTo(2);
        assertThat(sea.zones(4_000)).isEqualTo(100);
        assertThat(new SeaDef(1, 1).zones(7)).isEqualTo(7);
        assertFails(() -> sea.zones(0), ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void seaRejectsValuesOutsideLimits() {
        assertFails(() -> new SeaDef(0, 40), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new SeaDef(SeaDef.MAX_MIN_CELLS + 1, 40), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new SeaDef(10, 0), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new SeaDef(10, SeaDef.MAX_ZONE_CELLS + 1), ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void continentsKeepTheirValues() {
        ContinentsDef continents = new ContinentsDef(new CountRange(1, 4), 20, 50, 6);

        assertThat(continents.sizeWeight()).isEqualTo(new CountRange(1, 4));
        assertThat(continents.minProvinces()).isEqualTo(20);
        assertThat(continents.roughness()).isEqualTo(50);
        assertThat(continents.noiseCells()).isEqualTo(6);
    }

    @Test
    void continentsRejectValuesOutsideLimits() {
        CountRange weight = new CountRange(1, 4);
        assertFails(() -> new ContinentsDef(new CountRange(0, 4), 20, 50, 6), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new ContinentsDef(new CountRange(1, ContinentsDef.MAX_SIZE_WEIGHT + 1), 20, 50, 6),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new ContinentsDef(weight, 0, 50, 6), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new ContinentsDef(weight, ContinentsDef.MAX_MIN_PROVINCES + 1, 50, 6),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new ContinentsDef(weight, 20, -1, 6), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new ContinentsDef(weight, 20, ContinentsDef.MAX_ROUGHNESS + 1, 6), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new ContinentsDef(weight, 20, 50, 0), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new ContinentsDef(weight, 20, 50, ContinentsDef.MAX_NOISE_CELLS + 1),
                ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void reliefGivesHighestLevelNotAboveHeight() {
        ReliefDef relief = TestMaps.relief(new CountRange(1, 2), 20, 40, 70);

        assertThat(relief.relief(0)).isEqualTo(Relief.PLAIN);
        assertThat(relief.relief(39)).isEqualTo(Relief.PLAIN);
        assertThat(relief.relief(40)).isEqualTo(Relief.HILLS);
        assertThat(relief.relief(69)).isEqualTo(Relief.HILLS);
        assertThat(relief.relief(70)).isEqualTo(Relief.MOUNTAINS);
        assertThat(relief.relief(ReliefDef.MAX_HEIGHT)).isEqualTo(Relief.MOUNTAINS);
        assertThat(relief.level(Relief.HILLS).minHeight()).isEqualTo(40);
        assertFails(() -> relief.relief(ReliefDef.MAX_HEIGHT + 1), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> relief.relief(-1), ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void reliefRejectsValuesOutsideLimits() {
        assertFails(
                () -> TestMaps.relief(new CountRange(0, ReliefDef.MAX_RIDGES + 1), 20, 40, 70),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> TestMaps.relief(new CountRange(1, 2), 0, 40, 70), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> TestMaps.relief(new CountRange(1, 2), ReliefDef.MAX_RIDGE_MIN_PROVINCES + 1, 40, 70),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> relief(0, 20, 60, 20, 20, 20, 3), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> relief(ReliefDef.MAX_RIDGE_LENGTH_PCT + 1, 20, 60, 20, 20, 20, 3), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> relief(100, ReliefDef.MAX_RIDGE_WANDER + 1, 60, 20, 20, 20, 3), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> relief(100, 20, ReliefDef.MAX_HEIGHT + 1, 20, 20, 20, 3), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> relief(100, 20, 60, 0, 20, 20, 3), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> relief(100, 20, 60, 20, -1, 20, 3), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> relief(100, 20, 60, 20, 20, ReliefDef.MAX_HEIGHT + 1, 3), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> relief(100, 20, 60, 20, 20, 20, 0), ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void reliefLevelsMustBeCompleteUniqueAndOrdered() {
        ReliefLevelDef plain = TestMaps.level(Relief.PLAIN, 0);
        ReliefLevelDef hills = TestMaps.level(Relief.HILLS, 40);
        ReliefLevelDef mountains = TestMaps.level(Relief.MOUNTAINS, 70);

        assertThatThrownBy(() -> levels(List.of(plain, hills))).isInstanceOfSatisfying(ValidationException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.MISSING_DEFINITION);
            assertThat(e.details()).containsEntry("value", "mountains");
        });
        assertFails(() -> levels(List.of(plain, hills, hills, mountains)), ErrorCode.DUPLICATE_ID);
        assertFails(() -> levels(List.of(plain, mountains, hills)), ErrorCode.OUT_OF_ORDER);
        // Пороги мусять строго зростати.
        assertFails(() -> TestMaps.relief(new CountRange(1, 2), 20, 70, 70), ErrorCode.OUT_OF_ORDER);
        // Рівнина — з нуля.
        assertThatThrownBy(() -> levels(List.of(TestMaps.level(Relief.PLAIN, 10), hills, mountains)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
                    assertThat(e.details()).containsEntry("field", "relief.plain.min_height");
                });
    }

    @Test
    void reliefLevelRequiresNameDescriptionAndHeightInRange() {
        assertFails(() -> new ReliefLevelDef(Relief.HILLS, " ", "Опис", 40), ErrorCode.BLANK_VALUE);
        assertFails(() -> new ReliefLevelDef(Relief.HILLS, "Пагорби", "", 40), ErrorCode.BLANK_VALUE);
        assertFails(
                () -> new ReliefLevelDef(Relief.HILLS, "Пагорби", "Опис", ReliefDef.MAX_HEIGHT + 1),
                ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void reliefKeysAreSnakeCase() {
        assertThat(Relief.PLAIN.key()).isEqualTo("plain");
        assertThat(Relief.HILLS.key()).isEqualTo("hills");
        assertThat(Relief.MOUNTAINS.key()).isEqualTo("mountains");
    }

    @Test
    void mapContentKeepsContentOrderAndFindsById() {
        MapContent content = TestMaps.CONTENT;

        assertThat(content.templates()).containsExactly(TestMaps.PANGAEA, TestMaps.ARCHIPELAGO);
        assertThat(content.template(new MapTemplateId("archipelago"))).contains(TestMaps.ARCHIPELAGO);
        assertThat(content.template(new MapTemplateId("ring_world"))).isEmpty();
        assertThat(content.continents()).isEqualTo(TestMaps.CONTINENTS);
        assertThat(content.relief()).isEqualTo(TestMaps.RELIEF);
        assertThat(content.climate()).isEqualTo(TestMaps.CLIMATE);
        assertThat(content.sea()).isEqualTo(TestMaps.SEA);
    }

    @Test
    void mapContentRejectsEmptyAndDuplicateTemplates() {
        assertFails(
                () -> new MapContent(
                        List.of(), TestMaps.GRID, TestMaps.CONTINENTS, TestMaps.RELIEF, TestMaps.CLIMATE, TestMaps.SEA),
                ErrorCode.EMPTY_COLLECTION);
        assertFails(
                () -> new MapContent(
                        List.of(TestMaps.PANGAEA, TestMaps.template("pangaea", 1, 100, 1, 1)),
                        TestMaps.GRID,
                        TestMaps.CONTINENTS,
                        TestMaps.RELIEF,
                        TestMaps.CLIMATE,
                        TestMaps.SEA),
                ErrorCode.DUPLICATE_ID);
    }

    @Test
    void stepRangeListsValuesFromMinToMax() {
        assertThat(new StepRange(60, 100, 5).values()).containsExactly(60, 65, 70, 75, 80, 85, 90, 95, 100);
        assertThat(new StepRange(7, 7, 3).values()).containsExactly(7);
    }

    @Test
    void stepRangeRejectsStepThatSkipsMax() {
        assertThatThrownBy(() -> new StepRange(60, 100, 7)).isInstanceOfSatisfying(ValidationException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
            assertThat(e.details()).containsEntry("field", "max").containsEntry("step", 7);
        });
        assertFails(() -> new StepRange(10, 5, 1), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new StepRange(0, 5, 0), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new StepRange(-1, 5, 1), ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void worldBalanceGivesRangeOfEachShare() {
        WorldBalanceDef world = TestMaps.BALANCE;

        assertThat(world.npcExtra(NpcShare.FEW)).isEqualTo(new CountRange(0, 1));
        assertThat(world.npcExtra(NpcShare.MANY)).isEqualTo(new CountRange(10, 20));
    }

    @Test
    void worldBalanceRequiresEveryShare() {
        TreeMap<NpcShare, CountRange> npc = new TreeMap<>(TestMaps.BALANCE.npcExtra());
        npc.remove(NpcShare.MANY);

        assertThatThrownBy(() -> world(npc, new StepRange(500, 1500, 500), new CountRange(1, 10)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.MISSING_DEFINITION);
                    assertThat(e.details()).containsEntry("value", "many");
                });
    }

    @Test
    void worldBalanceRejectsValuesOutsideLimits() {
        TreeMap<NpcShare, CountRange> npc = new TreeMap<>(TestMaps.BALANCE.npcExtra());
        StepRange unclaimed = new StepRange(500, 1500, 500);
        CountRange provinces = new CountRange(1, 10);
        TreeMap<NpcShare, CountRange> tooMany = new TreeMap<>(npc);
        tooMany.put(NpcShare.MANY, new CountRange(1, WorldLimits.MAX_COUNTRIES + 1));

        assertFails(() -> world(tooMany, unclaimed, provinces), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> world(npc, new StepRange(0, WorldBalanceDef.MAX_UNCLAIMED_BP + 1, 1), provinces),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> world(npc, unclaimed, new CountRange(0, 10)), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new WorldBalanceDef(npc, new StepRange(0, 10, 5), unclaimed, provinces),
                ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void packRequiresSmallestWorldToHoldMinimumOfEveryContinent() {
        // Архіпелаг — до 5 материків по 10 провінцій: найменший світ — щонайменше 50.
        TestNames.pack(TestMaps.CONTENT, TestMaps.world(new CountRange(50, 3000)));

        assertThatThrownBy(() -> TestNames.pack(TestMaps.CONTENT, TestMaps.world(new CountRange(49, 3000))))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
                    assertThat(e.details())
                            .containsEntry("field", "map_template.archipelago.continents.max")
                            .containsEntry("value", 49)
                            .containsEntry("min", 50L);
                });
    }

    @Test
    void packRequiresLargestWorldGridToFitCellLimit() {
        // Суходолу 10%: 2000 провінцій — рівно 20 000 комірок, 2001 — уже більше.
        MapContent map = TestMaps.content(List.of(TestMaps.template("sparse", 1, 100, 10, 1, 1)), TestMaps.CONTINENTS);
        TestNames.pack(map, TestMaps.world(new CountRange(100, 2000)));

        assertThatThrownBy(() -> TestNames.pack(map, TestMaps.world(new CountRange(100, 2001))))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
                    assertThat(e.details())
                            .containsEntry("field", "map_template.sparse.land_pct")
                            .containsEntry("value", 20_010)
                            .containsEntry("max", MapGridDef.MAX_CELLS);
                });
    }

    @Test
    void npcShareKeysAreSnakeCase() {
        assertThat(NpcShare.FEW.key()).isEqualTo("few");
        assertThat(NpcShare.NORMAL.key()).isEqualTo("normal");
        assertThat(NpcShare.MANY.key()).isEqualTo("many");
    }

    private static ReliefDef relief(
            int lengthPct, int wander, int ridgeHeight, int falloff, int baseHeight, int amplitude, int noiseCells) {
        return new ReliefDef(
                new CountRange(1, 2),
                20,
                lengthPct,
                wander,
                ridgeHeight,
                falloff,
                baseHeight,
                amplitude,
                noiseCells,
                TestMaps.RELIEF.levels());
    }

    private static ReliefDef levels(List<ReliefLevelDef> levels) {
        return new ReliefDef(new CountRange(1, 2), 20, 100, 20, 60, 20, 20, 20, 3, levels);
    }

    private static WorldBalanceDef world(TreeMap<NpcShare, CountRange> npc, StepRange unclaimed, CountRange provinces) {
        return new WorldBalanceDef(npc, new StepRange(60, 100, 20), unclaimed, provinces);
    }

    private static void assertFails(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
