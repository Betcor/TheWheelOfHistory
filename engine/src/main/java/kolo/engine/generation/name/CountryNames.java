package kolo.engine.generation.name;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kolo.engine.content.ContentPack;
import kolo.engine.content.NameContent;
import kolo.engine.content.NameFinalDef;
import kolo.engine.content.NameParadigmDef;
import kolo.engine.content.NameStyleDef;
import kolo.engine.content.StateFormDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.error.Checks;
import kolo.engine.error.ValidationException;
import kolo.engine.rng.Rng;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NounPhrase;
import kolo.engine.util.Fixed;

/**
 * Генератор назв держав (GD §4.9): {@code [форма державності] + [корінь]}, одразу в усіх відмінках.
 *
 * <p>Корінь складається з частин мовного стилю: початок + (вставка) + кінцівка, а закінчення відмінків бере з
 * парадигми кінцівки. Назва не впливає на ігровий процес, тож стиль, частини й форма обираються рівноймовірно.
 */
public final class CountryNames {

    private CountryNames() {}

    /**
     * Випадкова назва для держави з цією підкласифікацією.
     *
     * @param rng окремий потік генерації назви: кількість кидків залежить лише від контенту
     * @throws ValidationException з {@link kolo.engine.error.ErrorCode#UNKNOWN_REFERENCE}, якщо підкласифікації
     *     немає в контенті
     */
    public static LocalizedName generate(Rng rng, ContentPack content, SubIdeologyId subIdeology) {
        Objects.requireNonNull(rng, "rng");
        List<StateFormDef> forms = content.stateFormsFor(subIdeology);
        NameContent names = content.names();

        NameStyleDef style = pick(rng, List.copyOf(names.styles().values()));
        String start = pick(rng, style.starts());
        // Кидок робиться завжди, навіть без вставки: інакше кількість кидків залежала б від результату.
        boolean withMiddle = rng.nextInt(Fixed.BP_SCALE) < style.middleChanceBp();
        String middle = withMiddle ? pick(rng, style.middles()) : "";
        NameFinalDef nameFinal = pick(rng, style.finals());
        StateFormDef form = pick(rng, forms);

        NounPhrase root = root(start + middle, nameFinal, names.paradigm(nameFinal));
        return name(form, root);
    }

    /**
     * Корінь у всіх відмінках: {@code stem + final.text + закінчення}, з великої літери.
     *
     * @param stem початок кореня разом зі вставкою, малими літерами
     */
    public static NounPhrase root(String stem, NameFinalDef nameFinal, NameParadigmDef paradigm) {
        Checks.notBlank("stem", stem);
        Objects.requireNonNull(nameFinal, "final");
        Objects.requireNonNull(paradigm, "paradigm");
        String base = capitalize(stem + nameFinal.text());
        List<String> forms = new ArrayList<>(GrammaticalCase.values().length);
        for (GrammaticalCase grammaticalCase : GrammaticalCase.values()) {
            forms.add(base + paradigm.ending(grammaticalCase));
        }
        return new NounPhrase(paradigm.gender(), forms);
    }

    /** Повна назва — форма державності з коренем у називному; коротка — сам корінь. */
    public static LocalizedName name(StateFormDef form, NounPhrase root) {
        Objects.requireNonNull(form, "form");
        List<String> forms = new ArrayList<>(GrammaticalCase.values().length);
        for (GrammaticalCase grammaticalCase : GrammaticalCase.values()) {
            forms.add(form.render(grammaticalCase, root.nominative()));
        }
        return new LocalizedName(new NounPhrase(form.gender(), forms), root);
    }

    private static <T> T pick(Rng rng, List<T> options) {
        return options.get(rng.nextInt(options.size()));
    }

    // Character.toUpperCase не залежить від локалі, на відміну від String.toUpperCase().
    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
