package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.GrammaticalGender;

/**
 * Форма державності в повній назві (GD §4.9): «Народна Республіка {root}».
 *
 * <p>Корінь стоїть у називному відмінку як прикладка, відмінюється лише форма: «Народної Республіки Велор». Форма
 * доступна державі, якщо її підкласифікація є в {@code subIdeologies} або ідеологія — в {@code ideologies}.
 *
 * @param gender рід повної назви (за головним словом форми)
 * @param templates шаблони в порядку {@link GrammaticalCase}; кожен містить рівно один {@value #ROOT}
 * @param ideologies ідеології, кожній підкласифікації яких доступна форма
 * @param subIdeologies окремі підкласифікації, яким доступна форма
 */
public record StateFormDef(
        StateFormId id,
        GrammaticalGender gender,
        List<String> templates,
        List<IdeologyId> ideologies,
        List<SubIdeologyId> subIdeologies) {

    /** Місце кореня в шаблоні. */
    public static final String ROOT = "{root}";

    private static final int CASES = GrammaticalCase.values().length;

    /**
     * @throws ValidationException якщо шаблонів не сім, шаблон без {@value #ROOT} чи з іншими фігурними дужками, id
     *     повторюються або форма не доступна нікому
     */
    public StateFormDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(gender, "gender");
        String field = "state_form." + id;
        Checks.inRange(field + ".templates", templates.size(), CASES, CASES);
        for (GrammaticalCase grammaticalCase : GrammaticalCase.values()) {
            template(field + ".templates." + grammaticalCase.key(), templates.get(grammaticalCase.ordinal()));
        }
        templates = List.copyOf(templates);
        ideologies = List.copyOf(ideologies);
        subIdeologies = List.copyOf(subIdeologies);
        Defs.uniqueAll(field + ".ideologies", ideologies);
        Defs.uniqueAll(field + ".sub_ideologies", subIdeologies);
        if (ideologies.isEmpty() && subIdeologies.isEmpty()) {
            // Форма, недоступна жодній державі, — майже напевно пропущений рядок у контенті.
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", field + ".ideologies"));
        }
    }

    /** Чи доступна форма державі з цією ідеологією й підкласифікацією. */
    public boolean appliesTo(IdeologyId ideology, SubIdeologyId subIdeology) {
        Objects.requireNonNull(ideology, "ideology");
        Objects.requireNonNull(subIdeology, "subIdeology");
        return ideologies.contains(ideology) || subIdeologies.contains(subIdeology);
    }

    /** Повна назва у відмінку: шаблон з коренем у називному. */
    public String render(GrammaticalCase grammaticalCase, String root) {
        Checks.notBlank("root", root);
        return templates
                .get(Objects.requireNonNull(grammaticalCase, "case").ordinal())
                .replace(ROOT, root);
    }

    private static void template(String field, String template) {
        Checks.notBlank(field, template);
        int at = template.indexOf(ROOT);
        String rest = at < 0 ? template : template.substring(0, at) + template.substring(at + ROOT.length());
        if (at < 0
                || rest.indexOf('{') >= 0
                || rest.indexOf('}') >= 0
                || !template.strip().equals(template)) {
            throw NameText.invalid(field, template);
        }
    }
}
