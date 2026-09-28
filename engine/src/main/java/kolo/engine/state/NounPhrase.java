package kolo.engine.state;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.error.ValidationException;

/**
 * Іменне словосполучення в усіх відмінках, напр. «Республіка Велор», «Республіки Велор»…
 *
 * @param gender рід для узгодження дієслів і прикметників
 * @param forms форми в порядку {@link GrammaticalCase}; рівно по одній на відмінок
 */
public record NounPhrase(GrammaticalGender gender, List<String> forms) {

    private static final int CASES = GrammaticalCase.values().length;

    /** @throws ValidationException якщо форм не сім або якась порожня */
    public NounPhrase {
        Objects.requireNonNull(gender, "gender");
        forms = List.copyOf(forms);
        Checks.inRange("noun_phrase.forms", forms.size(), CASES, CASES);
        for (GrammaticalCase grammaticalCase : GrammaticalCase.values()) {
            Checks.notBlank("noun_phrase.forms." + grammaticalCase.key(), forms.get(grammaticalCase.ordinal()));
        }
    }

    public String form(GrammaticalCase grammaticalCase) {
        return forms.get(Objects.requireNonNull(grammaticalCase, "case").ordinal());
    }

    public String nominative() {
        return form(GrammaticalCase.NOMINATIVE);
    }

    @Override
    public String toString() {
        return nominative();
    }
}
