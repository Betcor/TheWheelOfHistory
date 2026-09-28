package kolo.engine.generation.name;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kolo.engine.content.ContentPack;
import kolo.engine.content.NameContent;
import kolo.engine.content.NameFinalDef;
import kolo.engine.content.NameParadigmDef;
import kolo.engine.content.NameParadigmId;
import kolo.engine.content.NameStyleId;
import kolo.engine.content.PersonNameStyleDef;
import kolo.engine.content.SurnameFinalDef;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.error.ValidationException;
import kolo.engine.rng.Rng;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NounPhrase;
import kolo.engine.state.Sex;

/**
 * Генератор імен відомих людей (GD §12.3): ім'я + прізвище, одразу в усіх відмінках.
 *
 * <p>Ім'я й прізвище збираються зі складів мовного стилю так само, як корінь назви держави ({@link CountryNames}), але
 * відмінюються за парадигмами для людей: «Велора» (кого?), а не «Велору» (чого?). Прізвище відмінюється залежно від
 * статі: жіноче «Торвер» лишається незмінним. Ім'я не впливає на ігровий процес, тож усе обирається рівноймовірно.
 */
public final class PersonNames {

    private PersonNames() {}

    /**
     * Випадкове ім'я людини.
     *
     * @param rng окремий потік генерації імені
     * @param style мовний стиль — той самий, що й у назви держави цієї людини
     * @throws ValidationException з {@link ErrorCode#UNKNOWN_REFERENCE}, якщо стилю немає в контенті
     */
    public static LocalizedName generate(Rng rng, ContentPack content, NameStyleId style, Sex sex) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(sex, "sex");
        NameContent names = content.names();
        PersonNameStyleDef personStyle = names.personStyle(Objects.requireNonNull(style, "style"))
                .orElseThrow(() -> new ValidationException(
                        ErrorCode.UNKNOWN_REFERENCE, ErrorDetails.of("field", "person_name_style", "value", style)));

        String givenStem = NameParts.stem(
                rng,
                personStyle.given().starts(),
                personStyle.given().middles(),
                personStyle.given().middleChanceBp());
        NameFinalDef givenFinal = NameParts.pick(rng, personStyle.givenFinals(sex));
        String surnameStem = NameParts.stem(
                rng,
                personStyle.surnames().starts(),
                personStyle.surnames().middles(),
                personStyle.surnames().middleChanceBp());
        SurnameFinalDef surnameFinal = NameParts.pick(rng, personStyle.surnameFinals());

        NounPhrase given = NameParts.decline(givenStem, givenFinal.text(), paradigm(names, givenFinal.paradigm()));
        NounPhrase surname =
                NameParts.decline(surnameStem, surnameFinal.text(), paradigm(names, surnameFinal.paradigm(sex)));
        return name(given, surname);
    }

    /**
     * Повне ім'я — ім'я й прізвище в тому самому відмінку («Велора Торвера»); коротке — прізвище.
     *
     * @throws ValidationException з {@link ErrorCode#NAME_GENDER_MISMATCH}, якщо рід імені й прізвища різний
     */
    public static LocalizedName name(NounPhrase given, NounPhrase surname) {
        Objects.requireNonNull(given, "given");
        Objects.requireNonNull(surname, "surname");
        if (given.gender() != surname.gender()) {
            throw new ValidationException(
                    ErrorCode.NAME_GENDER_MISMATCH,
                    ErrorDetails.of(
                            "field",
                            "surname",
                            "value",
                            surname.nominative(),
                            "expected",
                            given.gender().key()));
        }
        List<String> forms = new ArrayList<>(GrammaticalCase.values().length);
        for (GrammaticalCase grammaticalCase : GrammaticalCase.values()) {
            forms.add(given.form(grammaticalCase) + " " + surname.form(grammaticalCase));
        }
        return new LocalizedName(new NounPhrase(given.gender(), forms), surname);
    }

    private static NameParadigmDef paradigm(NameContent names, NameParadigmId id) {
        // Посилання перевірено при створенні NameContent: відсутність — баг рушія, а не контенту.
        return names.paradigm(id)
                .orElseThrow(
                        () -> new InvariantViolationException(ErrorDetails.of("field", "name_paradigm", "value", id)));
    }
}
