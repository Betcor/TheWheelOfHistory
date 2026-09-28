package kolo.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import org.junit.jupiter.api.Test;

class NounPhraseTest {

    private static final List<String> VELOR =
            List.of("Велор", "Велору", "Велору", "Велор", "Велором", "Велорі", "Велоре");

    @Test
    void formsFollowCaseOrder() {
        NounPhrase velor = new NounPhrase(GrammaticalGender.MASCULINE, VELOR);

        assertThat(velor.nominative()).isEqualTo("Велор");
        assertThat(velor.form(GrammaticalCase.INSTRUMENTAL)).isEqualTo("Велором");
        assertThat(velor.form(GrammaticalCase.LOCATIVE)).isEqualTo("Велорі");
        assertThat(velor.form(GrammaticalCase.VOCATIVE)).isEqualTo("Велоре");
        assertThat(velor).hasToString("Велор");
    }

    @Test
    void needsExactlyOneNonBlankFormPerCase() {
        assertThatThrownBy(() -> new NounPhrase(GrammaticalGender.MASCULINE, VELOR.subList(0, 6)))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
        List<String> blankVocative = new ArrayList<>(VELOR);
        blankVocative.set(GrammaticalCase.VOCATIVE.ordinal(), " ");
        assertThatThrownBy(() -> new NounPhrase(GrammaticalGender.MASCULINE, blankVocative))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.BLANK_VALUE);
                    assertThat(e.details()).containsEntry("field", "noun_phrase.forms.vocative");
                });
    }

    @Test
    void formsAreDefensivelyCopied() {
        List<String> forms = new ArrayList<>(VELOR);
        NounPhrase velor = new NounPhrase(GrammaticalGender.MASCULINE, forms);
        forms.set(0, "Інше");

        assertThat(velor.nominative()).isEqualTo("Велор");
    }

    @Test
    void localizedNameKeepsFullAndShortForms() {
        NounPhrase shortName = new NounPhrase(GrammaticalGender.MASCULINE, VELOR);
        NounPhrase fullName = new NounPhrase(
                GrammaticalGender.FEMININE,
                VELOR.stream().map(form -> "Республіка " + form).toList());
        LocalizedName name = new LocalizedName(fullName, shortName);

        assertThat(name).hasToString("Республіка Велор");
        assertThat(name.shortName().gender()).isEqualTo(GrammaticalGender.MASCULINE);
        assertThat(name.fullName().gender()).isEqualTo(GrammaticalGender.FEMININE);
        assertThatThrownBy(() -> new LocalizedName(fullName, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void keysAreContentKeys() {
        assertThat(GrammaticalCase.INSTRUMENTAL.key()).isEqualTo("instrumental");
        assertThat(GrammaticalGender.PLURAL.key()).isEqualTo("plural");
        assertThat(GrammaticalCase.values()).hasSize(7);
    }
}
