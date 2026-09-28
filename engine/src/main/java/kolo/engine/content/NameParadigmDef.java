package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.GrammaticalGender;

/**
 * Парадигма відмінювання кореня назви: закінчення для кожного відмінка.
 *
 * <p>Корінь не змінюється, змінюється лише закінчення: «Велов|а», «Велов|и», «Велов|ою». Тому кінцівки, після яких
 * корінь чергується (напр. «-ка» → «-ці»), у контенті мають іти з окремою парадигмою або не використовуватися.
 *
 * @param gender рід назви з цією парадигмою
 * @param endings закінчення в порядку {@link GrammaticalCase}; порожнє закінчення дозволене
 */
public record NameParadigmDef(NameParadigmId id, GrammaticalGender gender, List<String> endings) {

    private static final int CASES = GrammaticalCase.values().length;

    public NameParadigmDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(gender, "gender");
        Checks.inRange("name_paradigm." + id + ".endings", endings.size(), CASES, CASES);
        for (GrammaticalCase grammaticalCase : GrammaticalCase.values()) {
            NameText.ending(
                    "name_paradigm." + id + ".endings." + grammaticalCase.key(),
                    endings.get(grammaticalCase.ordinal()));
        }
        endings = List.copyOf(endings);
    }

    public String ending(GrammaticalCase grammaticalCase) {
        return endings.get(Objects.requireNonNull(grammaticalCase, "case").ordinal());
    }
}
