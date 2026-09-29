package kolo.engine.content;

import static kolo.engine.content.TestReligions.archetype;
import static kolo.engine.content.TestReligions.aspect;
import static kolo.engine.content.TestReligions.dogma;
import static kolo.engine.content.TestReligions.path;
import static kolo.engine.content.TestReligions.polity;
import static kolo.engine.content.TestReligions.temple;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.GrammaticalGender;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class ReligionDefinitionsTest {

    private static final ArchetypeId MONOTHEISM = new ArchetypeId("monotheism");
    private static final ArchetypeId POLYTHEISM = new ArchetypeId("polytheism");

    // ---- Визначення ----

    @Test
    void archetypeFieldsAreChecked() {
        ArchetypeDef archetype = archetype("dualism", "Два начала");
        assertThat(archetype.figure()).isEqualTo("Два начала");
        assertThat(archetype.tags()).containsExactly("archetype_dualism");

        assertFails(
                () -> new ArchetypeDef(MONOTHEISM, "Монотеїзм", "Опис", " ", 100, List.of()), ErrorCode.BLANK_VALUE);
        assertFails(
                () -> new ArchetypeDef(MONOTHEISM, "Монотеїзм", "Опис", "Бог", 0, List.of()),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new ArchetypeDef(MONOTHEISM, "Монотеїзм", "Опис", "Бог", ArchetypeDef.MAX_WEIGHT + 1, List.of()),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new ArchetypeDef(MONOTHEISM, "Монотеїзм", "Опис", "Бог", 100, List.of("a", "a")),
                ErrorCode.DUPLICATE_ID);
    }

    @Test
    void aspectWeightDependsOnTags() {
        AspectDef war = aspect("war", Map.of("archetype_polytheism", 50, "religion_sea", -150));

        assertThat(war.weightFor(Set.of())).isEqualTo(100);
        assertThat(war.weightFor(Set.of("archetype_polytheism"))).isEqualTo(150);
        // Підсумок обрізається до нуля: аспект з вагою 0 не випадає.
        assertThat(war.weightFor(Set.of("religion_sea"))).isZero();
        assertFails(
                () -> new AspectDef(new AspectId("war"), "Війна", "Опис", 0, new TreeMap<>(), List.of()),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new AspectDef(
                        new AspectId("war"), "Війна", "Опис", 100, new TreeMap<>(Map.of("Bad-Tag", 5)), List.of()),
                ErrorCode.INVALID_KEY_FORMAT);
    }

    @Test
    void dogmaCannotBeIncompatibleWithItselfOrRepeatReferences() {
        DogmaDef holyWar = dogma("holy_war", Map.of("religion_war", 100), "pacifism");
        assertThat(holyWar.incompatible()).containsExactly(new DogmaId("pacifism"));
        assertThat(holyWar.weightFor(Set.of("religion_war"))).isEqualTo(200);

        assertThatThrownBy(() -> dogma("holy_war", Map.of(), "holy_war"))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.SELF_REFERENCE);
                    assertThat(e.details()).containsExactly(entry("field", "dogma.holy_war.incompatible"));
                });
        assertFails(() -> dogma("holy_war", Map.of(), "pacifism", "pacifism"), ErrorCode.DUPLICATE_ID);
    }

    @Test
    void polityWeightDependsOnTags() {
        ReligionPolityDef church = polity("single_church", Map.of("archetype_monotheism", 100));

        assertThat(church.weightFor(Set.of("archetype_monotheism"))).isEqualTo(200);
        assertThat(church.tags()).containsExactly("polity_single_church");
        assertFails(
                () -> new ReligionPolityDef(
                        new ReligionPolityId("single_church"), "", "Опис", 100, new TreeMap<>(), List.of(), List.of()),
                ErrorCode.BLANK_VALUE);
    }

    @Test
    void faithFormRendersFigureInItsCase() {
        FaithFormDef path = path(List.of(MONOTHEISM));

        assertThat(path.figureCase()).isEqualTo(GrammaticalCase.GENITIVE);
        assertThat(path.render(GrammaticalCase.NOMINATIVE, "Оріна")).isEqualTo("Шлях Оріна");
        assertThat(path.render(GrammaticalCase.INSTRUMENTAL, "Оріна")).isEqualTo("Шляхом Оріна");
        assertThat(path.appliesTo(MONOTHEISM)).isTrue();
        assertThat(path.appliesTo(POLYTHEISM)).isFalse();
    }

    @Test
    void faithFormTemplatesAreChecked() {
        List<String> seven = new ArrayList<>(path(List.of(MONOTHEISM)).templates());

        assertFails(() -> form(seven.subList(0, 6), List.of(MONOTHEISM)), ErrorCode.VALUE_OUT_OF_RANGE);
        for (String broken : List.of("Шлях", "Шлях {figure} {figure}", "Шлях {root}", " Шлях {figure}")) {
            List<String> templates = new ArrayList<>(seven);
            templates.set(GrammaticalCase.DATIVE.ordinal(), broken);
            assertThatThrownBy(() -> form(templates, List.of(MONOTHEISM)))
                    .isInstanceOfSatisfying(ValidationException.class, e -> {
                        assertThat(e.code()).isEqualTo(ErrorCode.INVALID_NAME_FORMAT);
                        assertThat(e.details()).contains(entry("field", "faith_form.path.templates.dative"));
                    });
        }
        assertFails(() -> form(seven, List.of()), ErrorCode.EMPTY_COLLECTION);
        assertFails(() -> form(seven, List.of(MONOTHEISM, MONOTHEISM)), ErrorCode.DUPLICATE_ID);
    }

    // ---- Шаблон релігій ----

    @Test
    void keepsContentOrderAndLooksUpById() {
        ReligionContent religions = TestReligions.content();

        assertThat(religions.archetypes()).extracting(ArchetypeDef::id).containsExactly(MONOTHEISM, POLYTHEISM);
        assertThat(religions.aspects())
                .extracting(aspect -> aspect.id().value())
                .containsExactly("war", "knowledge", "sea");
        assertThat(religions.dogma(new DogmaId("pacifism"))).isPresent();
        assertThat(religions.polity(new ReligionPolityId("absent"))).isEmpty();
        assertThat(religions.faithForm(new FaithFormId("temple"))).isPresent();
        assertThat(religions.faithFormsFor(MONOTHEISM))
                .extracting(form -> form.id().value())
                .containsExactly("path");
        assertThat(religions.faithFormsFor(POLYTHEISM))
                .extracting(form -> form.id().value())
                .containsExactly("path", "temple");
        assertThat(religions.producedTags())
                .contains("archetype_monotheism", "religion_war", "dogma_pacifism", "polity_communities");
    }

    @Test
    void dogmaIncompatibilityIsSymmetric() {
        ReligionContent religions = TestReligions.content();
        DogmaId holyWar = new DogmaId("holy_war");
        DogmaId pacifism = new DogmaId("pacifism");
        DogmaId scholars = new DogmaId("scholar_honor");

        assertThat(religions.compatible(holyWar, pacifism)).isFalse();
        assertThat(religions.compatible(pacifism, holyWar)).isFalse();
        assertThat(religions.compatible(holyWar, scholars)).isTrue();
        assertThat(religions.compatible(holyWar, holyWar)).isFalse();
        assertFails(() -> religions.compatible(holyWar, new DogmaId("absent")), ErrorCode.UNKNOWN_REFERENCE);
    }

    @Test
    void everyCollectionIsRequiredAndIdsAreUnique() {
        ReligionContent base = TestReligions.content();

        assertFailsWith(
                () -> new ReligionContent(List.of(), base.aspects(), base.dogmas(), base.polities(), base.faithForms()),
                ErrorCode.EMPTY_COLLECTION,
                "archetypes");
        assertFailsWith(
                () -> new ReligionContent(
                        base.archetypes(), base.aspects(), base.dogmas(), List.of(), base.faithForms()),
                ErrorCode.EMPTY_COLLECTION,
                "religion_polities");
        assertFailsWith(
                () -> new ReligionContent(
                        base.archetypes(), twice(base.aspects()), base.dogmas(), base.polities(), base.faithForms()),
                ErrorCode.DUPLICATE_ID,
                "aspects");
    }

    @Test
    void referencesMustExist() {
        ReligionContent base = TestReligions.content();

        List<DogmaDef> dogmas = new ArrayList<>(base.dogmas());
        dogmas.add(dogma("asceticism", Map.of(), "prosperity"));
        assertFailsWith(
                () -> new ReligionContent(
                        base.archetypes(), base.aspects(), dogmas, base.polities(), base.faithForms()),
                ErrorCode.UNKNOWN_REFERENCE,
                "dogma.asceticism.incompatible");

        List<FaithFormDef> forms = List.of(path(List.of(MONOTHEISM, POLYTHEISM, new ArchetypeId("animism"))));
        assertFailsWith(
                () -> new ReligionContent(base.archetypes(), base.aspects(), base.dogmas(), base.polities(), forms),
                ErrorCode.UNKNOWN_REFERENCE,
                "faith_form.path.archetypes");
    }

    @Test
    void everyArchetypeNeedsAFaithForm() {
        ReligionContent base = TestReligions.content();

        assertThatThrownBy(() -> new ReligionContent(
                        base.archetypes(),
                        base.aspects(),
                        base.dogmas(),
                        base.polities(),
                        List.of(temple(List.of(POLYTHEISM)))))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.MISSING_DEFINITION);
                    assertThat(e.details())
                            .containsExactly(entry("field", "faith_forms"), entry("value", "monotheism"));
                });
    }

    @Test
    void weightTagsMayOnlyUseTagsOfEarlierWheels() {
        ReligionContent base = TestReligions.content();

        // Аспект обирається раніше за догмат, тож не може залежати від його мітки.
        List<AspectDef> aspects = new ArrayList<>(base.aspects());
        aspects.add(aspect("death", Map.of("dogma_holy_war", 10)));
        assertFailsWith(
                () -> new ReligionContent(
                        base.archetypes(), aspects, base.dogmas(), base.polities(), base.faithForms()),
                ErrorCode.UNKNOWN_REFERENCE,
                "aspect.death.weight_tags");

        List<DogmaDef> dogmas = new ArrayList<>(base.dogmas());
        dogmas.add(dogma("charity", Map.of("polity_communities", 10)));
        assertFailsWith(
                () -> new ReligionContent(
                        base.archetypes(), base.aspects(), dogmas, base.polities(), base.faithForms()),
                ErrorCode.UNKNOWN_REFERENCE,
                "dogma.charity.weight_tags");

        List<ReligionPolityDef> polities = new ArrayList<>(base.polities());
        polities.add(polity("no_clergy", Map.of("unknown_tag", 10)));
        assertFailsWith(
                () -> new ReligionContent(
                        base.archetypes(), base.aspects(), base.dogmas(), polities, base.faithForms()),
                ErrorCode.UNKNOWN_REFERENCE,
                "religion_polity.no_clergy.weight_tags");

        // Мітки тих самих і попередніх коліс — дозволено.
        List<ReligionPolityDef> allowed = new ArrayList<>(base.polities());
        allowed.add(polity(
                "no_clergy",
                Map.of("archetype_monotheism", 1, "religion_sea", 1, "dogma_pacifism", 1, "polity_communities", 1)));
        assertThat(new ReligionContent(base.archetypes(), base.aspects(), base.dogmas(), allowed, base.faithForms())
                        .polities())
                .hasSize(3);
    }

    @Test
    void religionBalanceLimitsCounts() {
        assertThat(TestReligions.BALANCE.aspects()).isEqualTo(new CountRange(2, 3));
        assertFails(
                () -> new ReligionBalanceDef(new CountRange(0, 2), new CountRange(1, 2)), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new ReligionBalanceDef(new CountRange(1, 2), new CountRange(1, ReligionBalanceDef.MAX_PARTS + 1)),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertThat(new ReligionBalanceDef(
                                new CountRange(ReligionBalanceDef.MAX_PARTS, ReligionBalanceDef.MAX_PARTS),
                                new CountRange(1, 1))
                        .aspects()
                        .max())
                .isEqualTo(ReligionBalanceDef.MAX_PARTS);
    }

    private static FaithFormDef form(List<String> templates, List<ArchetypeId> archetypes) {
        return new FaithFormDef(
                new FaithFormId("path"), GrammaticalGender.MASCULINE, templates, GrammaticalCase.GENITIVE, archetypes);
    }

    private static <T> List<T> twice(List<T> values) {
        List<T> result = new ArrayList<>(values);
        result.add(values.getFirst());
        return result;
    }

    private static void assertFailsWith(ThrowingCallable call, ErrorCode code, String field) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ValidationException.class, e -> {
            assertThat(e.code()).isEqualTo(code);
            assertThat(e.details()).contains(entry("field", field));
        });
    }

    private static void assertFails(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
