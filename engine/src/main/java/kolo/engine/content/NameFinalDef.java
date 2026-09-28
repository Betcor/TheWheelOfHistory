package kolo.engine.content;

import java.util.Objects;
import kolo.engine.error.ValidationException;

/**
 * Кінцівка кореня назви: незмінна частина й парадигма відмінювання. «ов» + {@code fem_hard} → «Велова», «Велови»…
 *
 * @param text незмінна частина; починається з голосної
 */
public record NameFinalDef(String text, NameParadigmId paradigm) {

    /** @throws ValidationException з {@link kolo.engine.error.ErrorCode#INVALID_NAME_FORMAT}, якщо формат не той */
    public NameFinalDef {
        NameText.part("name_final.text", text);
        if (!NameText.startsWithVowel(text)) {
            throw NameText.invalid("name_final.text", text);
        }
        Objects.requireNonNull(paradigm, "paradigm");
    }
}
