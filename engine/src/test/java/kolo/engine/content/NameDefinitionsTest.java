package kolo.engine.content;

import static kolo.engine.content.TestContent.mascHard;
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

        assertThatThrownBy(() -> new NameContent(List.of(mascHard()), List.of(style), List.of(form)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE);
                    assertThat(e.details())
                            .containsExactly(entry("field", "name_style.test.finals"), entry("value", "fem_hard"));
                });
        assertFails(() -> new NameContent(List.of(), List.of(style), List.of(form)), ErrorCode.EMPTY_COLLECTION);
        assertFails(
                () -> new NameContent(List.of(mascHard(), mascHard()), List.of(style("a")), List.of(form)),
                ErrorCode.DUPLICATE_ID);

        NameContent names = new NameContent(List.of(mascHard()), List.of(style("b"), style("a")), List.of(form));
        assertThat(names.styles().keySet()).extracting(NameStyleId::value).containsExactly("a", "b");
        assertThat(names.paradigm(FINALS.getFirst())).isEqualTo(mascHard());
        assertThat(names.stateForm(new StateFormId("republic"))).contains(form);
        assertThat(names.style(new NameStyleId("c"))).isEmpty();
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
