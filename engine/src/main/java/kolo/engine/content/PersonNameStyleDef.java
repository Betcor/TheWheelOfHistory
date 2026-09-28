package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Sex;

/**
 * Мовний стиль імен людей: ім'я + прізвище. Id збігається з id стилю назв держав ({@link NameStyleDef}), тож
 * громадяни держави з північною назвою мають північні імена.
 *
 * <p>Початки імен спільні для обох статей, кінцівки — окремі: «Вел» + «ор» → «Велор», «Вел» + «ен» → «Велена».
 *
 * @param given склади імен
 * @param maleFinals кінцівки чоловічих імен; парадигми чоловічого роду
 * @param femaleFinals кінцівки жіночих імен; парадигми жіночого роду
 * @param surnames склади прізвищ
 * @param surnameFinals кінцівки прізвищ з парадигмами для обох статей
 */
public record PersonNameStyleDef(
        NameStyleId id,
        NamePartsDef given,
        List<NameFinalDef> maleFinals,
        List<NameFinalDef> femaleFinals,
        NamePartsDef surnames,
        List<SurnameFinalDef> surnameFinals) {

    /** @throws ValidationException якщо список кінцівок порожній або кінцівка в ньому повторюється */
    public PersonNameStyleDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(given, "given");
        Objects.requireNonNull(surnames, "surnames");
        String field = "person_name_style." + id;
        maleFinals = finals(
                field + ".male_finals",
                maleFinals.stream().map(NameFinalDef::text).toList(),
                maleFinals);
        femaleFinals = finals(
                field + ".female_finals",
                femaleFinals.stream().map(NameFinalDef::text).toList(),
                femaleFinals);
        surnameFinals = finals(
                field + ".surname_finals",
                surnameFinals.stream().map(SurnameFinalDef::text).toList(),
                surnameFinals);
    }

    /** Кінцівки імен для цієї статі. */
    public List<NameFinalDef> givenFinals(Sex sex) {
        return switch (Objects.requireNonNull(sex, "sex")) {
            case MALE -> maleFinals;
            case FEMALE -> femaleFinals;
        };
    }

    /** @param texts незмінні частини кінцівок: та сама кінцівка з іншою парадигмою дала б два набори форм */
    private static <T> List<T> finals(String field, List<String> texts, List<T> finals) {
        if (finals.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", field));
        }
        Defs.uniqueAll(field, texts);
        return List.copyOf(finals);
    }
}
