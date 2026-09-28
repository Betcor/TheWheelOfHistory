package kolo.engine.generation.name;

import java.util.ArrayList;
import java.util.List;
import kolo.engine.content.NameParadigmDef;
import kolo.engine.error.Checks;
import kolo.engine.rng.Rng;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.NounPhrase;
import kolo.engine.util.Fixed;

/** Спільне для генераторів назв держав і імен людей: вибір складів і відмінювання за парадигмою. */
final class NameParts {

    private NameParts() {}

    /** Початок + (вставка), малими літерами. */
    static String stem(Rng rng, List<String> starts, List<String> middles, int middleChanceBp) {
        String start = pick(rng, starts);
        // Кидок робиться завжди, навіть без вставки: інакше кількість кидків залежала б від результату.
        boolean withMiddle = rng.nextInt(Fixed.BP_SCALE) < middleChanceBp;
        return withMiddle ? start + pick(rng, middles) : start;
    }

    /** {@code stem + text + закінчення} у кожному відмінку, з великої літери; рід — з парадигми. */
    static NounPhrase decline(String stem, String text, NameParadigmDef paradigm) {
        Checks.notBlank("stem", stem);
        String base = capitalize(stem + text);
        List<String> forms = new ArrayList<>(GrammaticalCase.values().length);
        for (GrammaticalCase grammaticalCase : GrammaticalCase.values()) {
            forms.add(base + paradigm.ending(grammaticalCase));
        }
        return new NounPhrase(paradigm.gender(), forms);
    }

    static <T> T pick(Rng rng, List<T> options) {
        return options.get(rng.nextInt(options.size()));
    }

    // Character.toUpperCase не залежить від локалі, на відміну від String.toUpperCase().
    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
