package kolo.engine.content;

import java.util.Objects;
import kolo.engine.state.Sex;

/**
 * Кінцівка прізвища: незмінна частина й окрема парадигма для кожної статі. Українські прізвища відмінюються
 * по-різному: «Торвер» — «Торвера» для чоловіка, але незмінне для жінки; «Велівськ|ий» — «Велівськ|а».
 *
 * @param text незмінна частина; починається з голосної
 * @param male парадигма чоловічого прізвища, чоловічого роду
 * @param female парадигма жіночого прізвища, жіночого роду
 */
public record SurnameFinalDef(String text, NameParadigmId male, NameParadigmId female) {

    /** @throws kolo.engine.error.ValidationException з {@link kolo.engine.error.ErrorCode#INVALID_NAME_FORMAT} */
    public SurnameFinalDef {
        NameText.finalText("surname_final.text", text);
        Objects.requireNonNull(male, "male");
        Objects.requireNonNull(female, "female");
    }

    public NameParadigmId paradigm(Sex sex) {
        return switch (Objects.requireNonNull(sex, "sex")) {
            case MALE -> male;
            case FEMALE -> female;
        };
    }
}
