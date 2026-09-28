package kolo.engine.generation.name;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kolo.engine.content.ContentPack;
import kolo.engine.content.NameContent;
import kolo.engine.content.NameFinalDef;
import kolo.engine.content.NameParadigmDef;
import kolo.engine.content.NameStyleDef;
import kolo.engine.content.NameStyleId;
import kolo.engine.content.StateFormDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.rng.Rng;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NounPhrase;

/**
 * Генератор назв держав (GD §4.9): {@code [форма державності] + [корінь]}, одразу в усіх відмінках.
 *
 * <p>Корінь складається з частин мовного стилю: початок + (вставка) + кінцівка, а закінчення відмінків бере з
 * парадигми кінцівки. Назва не впливає на ігровий процес, тож стиль, частини й форма обираються рівноймовірно.
 */
public final class CountryNames {

    private CountryNames() {}

    /**
     * Випадкова назва для держави з цією підкласифікацією, у випадковому мовному стилі.
     *
     * @param rng окремий потік генерації назви: кількість кидків залежить лише від контенту
     * @throws ValidationException з {@link kolo.engine.error.ErrorCode#UNKNOWN_REFERENCE}, якщо підкласифікації
     *     немає в контенті
     */
    public static LocalizedName generate(Rng rng, ContentPack content, SubIdeologyId subIdeology) {
        Objects.requireNonNull(rng, "rng");
        NameStyleDef style =
                NameParts.pick(rng, List.copyOf(content.names().styles().values()));
        return generate(rng, content, subIdeology, style.id());
    }

    /**
     * Випадкова назва в заданому мовному стилі: тим самим стилем потім називають людей держави ({@link
     * PersonNames}).
     *
     * @throws ValidationException з {@link kolo.engine.error.ErrorCode#UNKNOWN_REFERENCE}, якщо підкласифікації чи
     *     стилю немає в контенті
     */
    public static LocalizedName generate(Rng rng, ContentPack content, SubIdeologyId subIdeology, NameStyleId styleId) {
        Objects.requireNonNull(rng, "rng");
        List<StateFormDef> forms = content.stateFormsFor(subIdeology);
        NameContent names = content.names();
        NameStyleDef style = names.style(Objects.requireNonNull(styleId, "style"))
                .orElseThrow(() -> new ValidationException(
                        ErrorCode.UNKNOWN_REFERENCE, ErrorDetails.of("field", "name_style", "value", styleId)));

        String stem = NameParts.stem(rng, style.starts(), style.middles(), style.middleChanceBp());
        NameFinalDef nameFinal = NameParts.pick(rng, style.finals());
        StateFormDef form = NameParts.pick(rng, forms);

        NounPhrase root = root(stem, nameFinal, names.paradigm(nameFinal));
        return name(form, root);
    }

    /**
     * Корінь у всіх відмінках: {@code stem + final.text + закінчення}, з великої літери.
     *
     * @param stem початок кореня разом зі вставкою, малими літерами
     */
    public static NounPhrase root(String stem, NameFinalDef nameFinal, NameParadigmDef paradigm) {
        Objects.requireNonNull(nameFinal, "final");
        Objects.requireNonNull(paradigm, "paradigm");
        return NameParts.decline(stem, nameFinal.text(), paradigm);
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
}
