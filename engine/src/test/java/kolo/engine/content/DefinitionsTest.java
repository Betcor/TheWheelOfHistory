package kolo.engine.content;

import static kolo.engine.content.TestContent.sub;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.ArrayList;
import java.util.List;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Development;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.PersonKind;
import kolo.engine.state.TechBranch;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class DefinitionsTest {

    @Test
    void ideologyNeedsAtLeastOneSubIdeology() {
        assertFails(
                () -> new IdeologyDef(new IdeologyId("democracy"), "Демократія", 100, List.of(), List.of(), List.of()),
                ErrorCode.EMPTY_COLLECTION);
    }

    @Test
    void subIdeologiesOfOneIdeologyAreUnique() {
        assertFails(
                () -> TestContent.ideology("democracy", "liberal_democracy", "liberal_democracy"),
                ErrorCode.DUPLICATE_ID);
    }

    @Test
    void ideologyFindsItsSubIdeology() {
        IdeologyDef democracy = TestContent.ideology("democracy", "liberal_democracy", "social_democracy");

        assertThat(democracy.subIdeology(new SubIdeologyId("social_democracy"))).contains(sub("social_democracy"));
        assertThat(democracy.subIdeology(new SubIdeologyId("revanchism"))).isEmpty();
    }

    @Test
    void namesAreRequired() {
        assertFails(
                () -> new SubIdeologyDef(new SubIdeologyId("revanchism"), " ", 100, List.of(), List.of()),
                ErrorCode.BLANK_VALUE);
        assertFails(
                () -> new DoctrineDef(new DoctrineId("armored"), null, List.of(), List.of()), ErrorCode.BLANK_VALUE);
        assertFails(() -> new ResourceDef(new ResourceId("iron"), "", List.of()), ErrorCode.BLANK_VALUE);
    }

    @Test
    void developmentLevelIsWithinDesignRange() {
        assertThat(new DevelopmentLevelDef(-3, "Глибоке відставання", "Лише базові технології.").level())
                .isEqualTo(Development.MIN);
        assertFails(() -> new DevelopmentLevelDef(-4, "Нижче дна", "—"), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new DevelopmentLevelDef(3, "Понад лідера", "—"), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new DevelopmentLevelDef(0, "Світовий рівень", " "), ErrorCode.BLANK_VALUE);
    }

    @Test
    void branchAndNuclearStatusNeedNames() {
        assertFails(() -> new TechBranchDef(TechBranch.SOCIETY, ""), ErrorCode.BLANK_VALUE);
        assertFails(() -> new NuclearStatusDef(NuclearStatus.ARSENAL, null, List.of()), ErrorCode.BLANK_VALUE);
        assertFails(
                () -> new NuclearStatusDef(NuclearStatus.ARSENAL, "Арсенал", List.of("NuclearPower")),
                ErrorCode.INVALID_KEY_FORMAT);
    }

    @Test
    void personKindNeedsNameAndDescription() {
        assertThat(new PersonKindDef(PersonKind.GENERAL, "Генерал", "Командує фронтом.", List.of("military")).tags())
                .containsExactly("military");
        assertFails(
                () -> new PersonKindDef(PersonKind.GENERAL, " ", "Командує фронтом.", List.of()),
                ErrorCode.BLANK_VALUE);
        assertFails(() -> new PersonKindDef(PersonKind.GENERAL, "Генерал", "", List.of()), ErrorCode.BLANK_VALUE);
    }

    @Test
    void traitWithoutKindsSuitsEveryone() {
        TraitDef charismatic = TestContent.trait("charismatic", List.of());
        TraitDef genius = TestContent.trait("genius", List.of(PersonKind.SCIENTIST));

        for (PersonKind kind : PersonKind.values()) {
            assertThat(charismatic.allows(kind)).isTrue();
            assertThat(genius.allows(kind)).isEqualTo(kind == PersonKind.SCIENTIST);
        }
    }

    @Test
    void traitRejectsRepeatsAndSelfIncompatibility() {
        assertFails(() -> TestContent.trait("loyal", List.of(), "loyal"), ErrorCode.SELF_REFERENCE);
        assertFails(() -> TestContent.trait("loyal", List.of(), "corrupt", "corrupt"), ErrorCode.DUPLICATE_ID);
        assertThatThrownBy(() -> TestContent.trait("genius", List.of(PersonKind.SCIENTIST, PersonKind.SCIENTIST)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_ID);
                    assertThat(e.details())
                            .containsExactly(entry("field", "trait.genius.kinds"), entry("value", "scientist"));
                });
        assertFails(
                () -> new TraitDef(new TraitId("genius"), "", List.of(), List.of(), List.of(), List.of()),
                ErrorCode.BLANK_VALUE);
    }

    @Test
    void tagsAreSnakeCaseAndUnique() {
        assertFails(
                () -> new ResourceDef(new ResourceId("oil"), "Нафта", List.of("Energy")), ErrorCode.INVALID_KEY_FORMAT);
        assertThatThrownBy(() -> new ResourceDef(new ResourceId("oil"), "Нафта", List.of("energy", "energy")))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_ID);
                    assertThat(e.details())
                            .containsExactly(entry("field", "resource.oil.tags"), entry("value", "energy"));
                });
    }

    @Test
    void collectionsAreDefensivelyCopied() {
        List<String> tags = new ArrayList<>(List.of("metal"));
        ResourceDef iron = new ResourceDef(new ResourceId("iron"), "Залізо", tags);
        tags.add("strategic");

        assertThat(iron.tags()).containsExactly("metal");
        assertThatThrownBy(() -> iron.tags().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    private static void assertFails(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
