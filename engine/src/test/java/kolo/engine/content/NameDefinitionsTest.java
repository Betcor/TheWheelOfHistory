package kolo.engine.content;

import static kolo.engine.content.TestContent.mascHard;
import static kolo.engine.content.TestContent.paradigms;
import static kolo.engine.content.TestContent.personStyle;
import static kolo.engine.content.TestContent.republic;
import static kolo.engine.content.TestContent.style;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.ArrayList;
import java.util.List;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.Sex;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class NameDefinitionsTest {

    private static final NameParadigmId MASC_HARD = new NameParadigmId("masc_hard");
    private static final List<NameFinalDef> FINALS = List.of(new NameFinalDef("ор", MASC_HARD));

    @Test
    void paradigmAllowsEmptyEndingsButNotMissingOnes() {
        NameParadigmDef paradigm = mascHard();

        assertThat(paradigm.ending(GrammaticalCase.NOMINATIVE)).isEmpty();
        assertThat(paradigm.ending(GrammaticalCase.INSTRUMENTAL)).isEqualTo("ом");

        List<String> missing = new ArrayList<>(paradigm.endings());
        missing.set(GrammaticalCase.DATIVE.ordinal(), null);
        assertFails(() -> new NameParadigmDef(MASC_HARD, GrammaticalGender.MASCULINE, missing), ErrorCode.BLANK_VALUE);
        assertFails(
                () -> new NameParadigmDef(MASC_HARD, GrammaticalGender.MASCULINE, List.of("", "у")),
                ErrorCode.VALUE_OUT_OF_RANGE);
        List<String> latin = new ArrayList<>(paradigm.endings());
        latin.set(GrammaticalCase.GENITIVE.ordinal(), "u");
        assertFails(
                () -> new NameParadigmDef(MASC_HARD, GrammaticalGender.MASCULINE, latin),
                ErrorCode.INVALID_NAME_FORMAT);
    }

    @Test
    void finalStartsWithVowel() {
        assertThat(new NameFinalDef("ор", MASC_HARD).text()).isEqualTo("ор");
        assertFails(() -> new NameFinalDef("мар", MASC_HARD), ErrorCode.INVALID_NAME_FORMAT);
        assertFails(() -> new NameFinalDef("Ор", MASC_HARD), ErrorCode.INVALID_NAME_FORMAT);
        assertFails(() -> new NameFinalDef("", MASC_HARD), ErrorCode.INVALID_NAME_FORMAT);
    }

    @Test
    void styleJunctionsAlternateConsonantsAndVowels() {
        assertThat(style("northern").starts()).containsExactly("вел", "тор");

        // Початок на голосну дав би збіг голосних на стику з кінцівкою: «веа» + «ор».
        assertThatThrownBy(() -> styleWith(List.of("ве"), List.of()))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.INVALID_NAME_FORMAT);
                    assertThat(e.details())
                            .containsExactly(entry("field", "name_style.test.starts"), entry("value", "ве"));
                });
        assertFails(() -> styleWith(List.of("вел"), List.of("ми")), ErrorCode.INVALID_NAME_FORMAT);
        assertFails(() -> styleWith(List.of("вел"), List.of("іма")), ErrorCode.INVALID_NAME_FORMAT);
        assertFails(() -> styleWith(List.of("ьвел"), List.of()), ErrorCode.INVALID_NAME_FORMAT);
        assertFails(() -> styleWith(List.of("vel"), List.of()), ErrorCode.INVALID_NAME_FORMAT);
        assertFails(() -> styleWith(List.of("вел", "вел"), List.of()), ErrorCode.DUPLICATE_ID);
    }

    @Test
    void styleNeedsStartsFinalsAndMiddlesWhenChanceIsPositive() {
        NameStyleId id = new NameStyleId("test");
        assertFails(() -> new NameStyleDef(id, "Тест", List.of(), List.of(), 0, FINALS), ErrorCode.EMPTY_COLLECTION);
        assertFails(
                () -> new NameStyleDef(id, "Тест", List.of("вел"), List.of(), 0, List.of()),
                ErrorCode.EMPTY_COLLECTION);
        assertFails(
                () -> new NameStyleDef(id, "Тест", List.of("вел"), List.of(), 1, FINALS), ErrorCode.EMPTY_COLLECTION);
        assertFails(
                () -> new NameStyleDef(id, "Тест", List.of("вел"), List.of("ім"), 10_001, FINALS),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new NameStyleDef(id, " ", List.of("вел"), List.of(), 0, FINALS), ErrorCode.BLANK_VALUE);
        assertFails(
                () -> new NameStyleDef(
                        id,
                        "Тест",
                        List.of("вел"),
                        List.of(),
                        0,
                        List.of(new NameFinalDef("ор", MASC_HARD), new NameFinalDef("ор", new NameParadigmId("x")))),
                ErrorCode.DUPLICATE_ID);
    }

    @Test
    void stateFormRendersRootInEveryCase() {
        StateFormDef form = republic(List.of(new IdeologyId("democracy")), List.of());

        assertThat(form.render(GrammaticalCase.NOMINATIVE, "Велор")).isEqualTo("Республіка Велор");
        assertThat(form.render(GrammaticalCase.INSTRUMENTAL, "Велор")).isEqualTo("Республікою Велор");
    }

    @Test
    void stateFormAppliesByIdeologyOrSubIdeology() {
        IdeologyId democracy = new IdeologyId("democracy");
        IdeologyId monarchy = new IdeologyId("monarchy");
        SubIdeologyId liberal = new SubIdeologyId("liberal_democracy");
        SubIdeologyId constitutional = new SubIdeologyId("constitutional_monarchy");

        StateFormDef byIdeology = republic(List.of(democracy), List.of());
        StateFormDef bySub = republic(List.of(), List.of(constitutional));

        assertThat(byIdeology.appliesTo(democracy, liberal)).isTrue();
        assertThat(byIdeology.appliesTo(monarchy, constitutional)).isFalse();
        assertThat(bySub.appliesTo(democracy, constitutional)).isTrue();
        assertThat(bySub.appliesTo(democracy, liberal)).isFalse();
    }

    @Test
    void stateFormTemplatesHaveExactlyOneRoot() {
        assertFails(() -> formWith("Республіка"), ErrorCode.INVALID_NAME_FORMAT);
        assertFails(() -> formWith("Республіка {root} {root}"), ErrorCode.INVALID_NAME_FORMAT);
        assertFails(() -> formWith("Республіка {country}"), ErrorCode.INVALID_NAME_FORMAT);
        assertFails(() -> formWith("Республіка {root} "), ErrorCode.INVALID_NAME_FORMAT);
        assertFails(() -> formWith(""), ErrorCode.BLANK_VALUE);
        assertThat(formWith("{root}").templates()).first().isEqualTo("{root}");
    }

    @Test
    void stateFormMustApplyToSomeoneWithoutRepeats() {
        assertFails(() -> republic(List.of(), List.of()), ErrorCode.EMPTY_COLLECTION);
        IdeologyId democracy = new IdeologyId("democracy");
        assertFails(() -> republic(List.of(democracy, democracy), List.of()), ErrorCode.DUPLICATE_ID);
    }

    @Test
    void nameContentChecksParadigmReferences() {
        NameStyleDef style = new NameStyleDef(
                new NameStyleId("test"),
                "Тест",
                List.of("вел"),
                List.of(),
                0,
                List.of(new NameFinalDef("ор", new NameParadigmId("fem_hard"))));
        StateFormDef form = republic(List.of(new IdeologyId("democracy")), List.of());

        assertThatThrownBy(() -> new NameContent(
                        List.of(mascHard()), List.of(style), List.of(form), List.of(personStyle("test"))))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE);
                    assertThat(e.details())
                            .containsExactly(entry("field", "name_style.test.finals"), entry("value", "fem_hard"));
                });
        assertFails(
                () -> new NameContent(List.of(), List.of(style), List.of(form), List.of(personStyle("test"))),
                ErrorCode.EMPTY_COLLECTION);
        assertFails(
                () -> new NameContent(
                        List.of(mascHard(), mascHard()), List.of(style("a")), List.of(form), List.of(personStyle("a"))),
                ErrorCode.DUPLICATE_ID);

        NameContent names = new NameContent(
                paradigms(),
                List.of(style("b"), style("a")),
                List.of(form),
                List.of(personStyle("a"), personStyle("b")));
        assertThat(names.styles().keySet()).extracting(NameStyleId::value).containsExactly("a", "b");
        assertThat(names.paradigm(FINALS.getFirst())).isEqualTo(mascHard());
        assertThat(names.stateForm(new StateFormId("republic"))).contains(form);
        assertThat(names.style(new NameStyleId("c"))).isEmpty();
    }

    @Test
    void namePartsFollowJunctionRules() {
        NamePartsDef parts = new NamePartsDef(List.of("вел", "тор"), List.of("ім"), 2500);
        assertThat(parts.starts()).containsExactly("вел", "тор");

        assertThatThrownBy(() -> new NamePartsDef(List.of("ве"), List.of(), 0))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.INVALID_NAME_FORMAT);
                    assertThat(e.details()).containsExactly(entry("field", "name_parts.starts"), entry("value", "ве"));
                });
        assertFails(() -> new NamePartsDef(List.of("вел"), List.of("мі"), 1), ErrorCode.INVALID_NAME_FORMAT);
        assertFails(() -> new NamePartsDef(List.of(), List.of(), 0), ErrorCode.EMPTY_COLLECTION);
        assertFails(() -> new NamePartsDef(List.of("вел"), List.of(), 1), ErrorCode.EMPTY_COLLECTION);
        assertFails(() -> new NamePartsDef(List.of("вел"), List.of("ім"), -1), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new NamePartsDef(List.of("вел", "вел"), List.of(), 0), ErrorCode.DUPLICATE_ID);
    }

    @Test
    void surnameFinalHasParadigmForEachSex() {
        NameParadigmId fixed = new NameParadigmId("fixed_fem");
        SurnameFinalDef surnameFinal = new SurnameFinalDef("ер", MASC_HARD, fixed);

        assertThat(surnameFinal.paradigm(Sex.MALE)).isEqualTo(MASC_HARD);
        assertThat(surnameFinal.paradigm(Sex.FEMALE)).isEqualTo(fixed);
        assertFails(() -> new SurnameFinalDef("торв", MASC_HARD, fixed), ErrorCode.INVALID_NAME_FORMAT);
        assertFails(() -> new SurnameFinalDef("Ер", MASC_HARD, fixed), ErrorCode.INVALID_NAME_FORMAT);
    }

    @Test
    void personStyleNeedsFinalsForBothSexesWithoutRepeats() {
        PersonNameStyleDef style = personStyle("northern");
        assertThat(style.givenFinals(Sex.MALE)).extracting(NameFinalDef::text).containsExactly("ор");
        assertThat(style.givenFinals(Sex.FEMALE)).extracting(NameFinalDef::text).containsExactly("ен");

        assertFails(
                () -> personStyleWith(List.of(), style.femaleFinals(), style.surnameFinals()),
                ErrorCode.EMPTY_COLLECTION);
        assertFails(
                () -> personStyleWith(style.maleFinals(), List.of(), style.surnameFinals()),
                ErrorCode.EMPTY_COLLECTION);
        assertFails(
                () -> personStyleWith(style.maleFinals(), style.femaleFinals(), List.of()), ErrorCode.EMPTY_COLLECTION);
        assertThatThrownBy(() -> personStyleWith(
                        List.of(new NameFinalDef("ор", MASC_HARD), new NameFinalDef("ор", new NameParadigmId("x"))),
                        style.femaleFinals(),
                        style.surnameFinals()))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_ID);
                    assertThat(e.details())
                            .containsExactly(
                                    entry("field", "person_name_style.test.male_finals"), entry("value", "ор"));
                });
    }

    @Test
    void nameContentChecksPersonStyles() {
        StateFormDef form = republic(List.of(new IdeologyId("democracy")), List.of());
        PersonNameStyleDef valid = personStyle("a");
        NameParadigmId masc = new NameParadigmId("masc_hard");
        NameParadigmId fem = new NameParadigmId("fem_hard");

        // Жіноча парадигма в чоловічому імені дала б «Велор» жіночого роду.
        assertThatThrownBy(() -> names(personStyleWith(
                        "a", List.of(new NameFinalDef("ор", fem)), valid.femaleFinals(), valid.surnameFinals())))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.NAME_GENDER_MISMATCH);
                    assertThat(e.details())
                            .containsExactly(
                                    entry("expected", "masculine"),
                                    entry("field", "person_name_style.a.male_finals"),
                                    entry("value", "fem_hard"));
                });
        assertFails(
                () -> names(personStyleWith(
                        "a", valid.maleFinals(), List.of(new NameFinalDef("ен", masc)), valid.surnameFinals())),
                ErrorCode.NAME_GENDER_MISMATCH);
        assertFails(
                () -> names(personStyleWith(
                        "a", valid.maleFinals(), valid.femaleFinals(), List.of(new SurnameFinalDef("ер", masc, masc)))),
                ErrorCode.NAME_GENDER_MISMATCH);
        assertFails(
                () -> names(personStyleWith(
                        "a",
                        valid.maleFinals(),
                        valid.femaleFinals(),
                        List.of(new SurnameFinalDef("ер", new NameParadigmId("missing"), fem)))),
                ErrorCode.UNKNOWN_REFERENCE);

        // Стиль імен без стилю назв і стиль назв без стилю імен.
        assertThatThrownBy(() -> names(valid, personStyle("b")))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE);
                    assertThat(e.details())
                            .containsExactly(entry("field", "person_name_style.b.id"), entry("value", "b"));
                });
        assertThatThrownBy(() ->
                        new NameContent(paradigms(), List.of(style("a"), style("b")), List.of(form), List.of(valid)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.MISSING_DEFINITION);
                    assertThat(e.details()).containsExactly(entry("field", "person_name_styles"), entry("value", "b"));
                });
        assertFails(() -> names(valid, valid), ErrorCode.DUPLICATE_ID);
        assertFails(
                () -> new NameContent(paradigms(), List.of(style("a")), List.of(form), List.of()),
                ErrorCode.EMPTY_COLLECTION);

        NameContent names = names(valid);
        assertThat(names.personStyles().keySet())
                .containsExactlyElementsOf(names.styles().keySet());
        assertThat(names.personStyle(new NameStyleId("a"))).contains(valid);
        assertThat(names.personStyle(new NameStyleId("c"))).isEmpty();
        assertThat(names.paradigm(fem)).contains(TestContent.femHard());
        assertThat(names.paradigm(new NameParadigmId("missing"))).isEmpty();
    }

    /** Назви зі стилем назв «a» і заданими стилями імен. */
    private static NameContent names(PersonNameStyleDef... personStyles) {
        return new NameContent(
                paradigms(),
                List.of(style("a")),
                List.of(republic(List.of(new IdeologyId("democracy")), List.of())),
                List.of(personStyles));
    }

    private static PersonNameStyleDef personStyleWith(
            List<NameFinalDef> male, List<NameFinalDef> female, List<SurnameFinalDef> surnameFinals) {
        return personStyleWith("test", male, female, surnameFinals);
    }

    private static PersonNameStyleDef personStyleWith(
            String id, List<NameFinalDef> male, List<NameFinalDef> female, List<SurnameFinalDef> surnameFinals) {
        PersonNameStyleDef base = personStyle(id);
        return new PersonNameStyleDef(base.id(), base.given(), male, female, base.surnames(), surnameFinals);
    }

    private static NameStyleDef styleWith(List<String> starts, List<String> middles) {
        return new NameStyleDef(new NameStyleId("test"), "Тест", starts, middles, middles.isEmpty() ? 0 : 1, FINALS);
    }

    private static StateFormDef formWith(String nominative) {
        List<String> templates = new ArrayList<>(
                republic(List.of(new IdeologyId("democracy")), List.of()).templates());
        templates.set(0, nominative);
        return new StateFormDef(
                new StateFormId("test"),
                GrammaticalGender.FEMININE,
                templates,
                List.of(new IdeologyId("democracy")),
                List.of());
    }

    private static void assertFails(ThrowingCallable create, ErrorCode code) {
        assertThatThrownBy(create)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
