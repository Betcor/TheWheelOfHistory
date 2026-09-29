package kolo.engine.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.TreeMap;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.state.NpcShare;
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
                () -> new MapTemplateDef(new MapTemplateId("a"), " ", "Опис", 10, 100, new CountRange(1, 1)),
                ErrorCode.BLANK_VALUE);
        assertFails(
                () -> new MapTemplateDef(new MapTemplateId("a"), "Назва", "", 10, 100, new CountRange(1, 1)),
                ErrorCode.BLANK_VALUE);
        assertFails(() -> new MapTemplateId("Pangaea"), ErrorCode.INVALID_KEY_FORMAT);
    }

    @Test
    void mapContentKeepsContentOrderAndFindsById() {
        MapContent content = TestMaps.CONTENT;

        assertThat(content.templates()).containsExactly(TestMaps.PANGAEA, TestMaps.ARCHIPELAGO);
        assertThat(content.template(new MapTemplateId("archipelago"))).contains(TestMaps.ARCHIPELAGO);
        assertThat(content.template(new MapTemplateId("ring_world"))).isEmpty();
    }

    @Test
    void mapContentRejectsEmptyAndDuplicateTemplates() {
        assertFails(() -> new MapContent(List.of()), ErrorCode.EMPTY_COLLECTION);
        assertFails(
                () -> new MapContent(List.of(TestMaps.PANGAEA, TestMaps.template("pangaea", 1, 100, 1, 1))),
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
    void npcShareKeysAreSnakeCase() {
        assertThat(NpcShare.FEW.key()).isEqualTo("few");
        assertThat(NpcShare.NORMAL.key()).isEqualTo("normal");
        assertThat(NpcShare.MANY.key()).isEqualTo("many");
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
