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
 * Форма назви віри (GD §25.1, колесо 5): «Шлях {figure}» → «Шлях Оріна», «Шляху Оріна».
 *
 * <p>Ім'я постаті (божества чи засновника) стоїть у відмінку {@code figureCase} в усіх формах — відмінюється лише
 * головне слово: «Шляхом Оріна». Форма доступна релігіям з архетипами зі списку {@code archetypes}.
 *
 * @param gender рід назви віри (за головним словом), з ним узгоджуються дієслова в хроніці
 * @param templates шаблони в порядку {@link GrammaticalCase}; кожен містить рівно один {@value #FIGURE}
 * @param figureCase відмінок імені постаті в назві, напр. родовий («Шлях Оріна»)
 * @param archetypes архетипи, яким доступна форма; хоча б один
 */
public record FaithFormDef(
        FaithFormId id,
        GrammaticalGender gender,
        List<String> templates,
        GrammaticalCase figureCase,
        List<ArchetypeId> archetypes) {

    /** Місце імені постаті в шаблоні. */
    public static final String FIGURE = "{figure}";

    private static final int CASES = GrammaticalCase.values().length;

    /**
     * @throws ValidationException якщо шаблонів не сім, шаблон без {@value #FIGURE} чи з іншими фігурними дужками,
     *     архетипи повторюються або форма не доступна жодному архетипу
     */
    public FaithFormDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(figureCase, "figureCase");
        String field = "faith_form." + id;
        Checks.inRange(field + ".templates", templates.size(), CASES, CASES);
        for (GrammaticalCase grammaticalCase : GrammaticalCase.values()) {
            template(field + ".templates." + grammaticalCase.key(), templates.get(grammaticalCase.ordinal()));
        }
        templates = List.copyOf(templates);
        archetypes = List.copyOf(archetypes);
        Defs.uniqueAll(field + ".archetypes", archetypes);
        if (archetypes.isEmpty()) {
            // Форма, недоступна жодній релігії, — майже напевно пропущений рядок у контенті.
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", field + ".archetypes"));
        }
    }

    /** Чи доступна форма релігії з цим архетипом. */
    public boolean appliesTo(ArchetypeId archetype) {
        return archetypes.contains(Objects.requireNonNull(archetype, "archetype"));
    }

    /**
     * Назва віри у відмінку.
     *
     * @param figure ім'я постаті вже у відмінку {@link #figureCase()}
     */
    public String render(GrammaticalCase grammaticalCase, String figure) {
        Checks.notBlank("figure", figure);
        return templates
                .get(Objects.requireNonNull(grammaticalCase, "case").ordinal())
                .replace(FIGURE, figure);
    }

    private static void template(String field, String template) {
        Checks.notBlank(field, template);
        int at = template.indexOf(FIGURE);
        String rest = at < 0 ? template : template.substring(0, at) + template.substring(at + FIGURE.length());
        if (at < 0
                || rest.indexOf('{') >= 0
                || rest.indexOf('}') >= 0
                || !template.strip().equals(template)) {
            throw NameText.invalid(field, template);
        }
    }
}
