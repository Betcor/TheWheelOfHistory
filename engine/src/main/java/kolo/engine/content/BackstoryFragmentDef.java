package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.stream.Stream;
import kolo.engine.error.Checks;

/**
 * Фрагмент передісторії держави (GD §4.7): подія до 1970 року з умовами, вагою, мітками й ефектами.
 *
 * <p>Колесо передісторії обирає 2–4 фрагменти поспіль; мітки, додані одним фрагментом, змінюють умови й ваги
 * наступних («програла війну» → «контрибуції»).
 *
 * @param weight базова вага в колесі передісторії, {@code 1..}{@value #MAX_WEIGHT}; відносна — колесо нормалізує
 *     ваги доступних фрагментів
 * @param quality наскільки фрагмент добрий для держави, {@code 0..100} (GD §2.1, стріки GD §4.10)
 * @param yearFrom найраніший рік події
 * @param yearTo найпізніший рік події, не пізніше {@value #LATEST_YEAR}
 * @param condition коли фрагмент доступний
 * @param weightTags добавки до ваги за мітки держави; можуть бути від'ємні
 * @param neighbor фрагмент пов'язує державу з сусідом; доступний лише тоді, коли сусід є, і згадує його в тексті
 * @param adds мітки, які фрагмент дає державі
 * @param durationYears скільки років діють модифікатори від 01.01.1970; {@code 0} — постійно
 * @param modifiers ефекти фрагмента
 * @param text шаблон тексту для хроніки й енциклопедії
 */
public record BackstoryFragmentDef(
        BackstoryFragmentId id,
        int weight,
        int quality,
        int yearFrom,
        int yearTo,
        TagCondition condition,
        SortedMap<String, Integer> weightTags,
        boolean neighbor,
        List<String> adds,
        int durationYears,
        List<ModifierDef> modifiers,
        BackstoryText text) {

    public static final int MAX_WEIGHT = 10_000;
    public static final int EARLIEST_YEAR = 1900;
    /** Гра починається 01.01.1970, тож передісторія — до кінця 1969 року. */
    public static final int LATEST_YEAR = 1969;

    public static final int MAX_DURATION_YEARS = 100;

    public BackstoryFragmentDef {
        Objects.requireNonNull(id, "id");
        String field = "backstory." + id;
        Checks.inRange(field + ".weight", weight, 1, MAX_WEIGHT);
        Checks.inRange(field + ".quality", quality, 0, 100);
        Checks.inRange(field + ".years.from", yearFrom, EARLIEST_YEAR, LATEST_YEAR);
        Checks.inRange(field + ".years.to", yearTo, yearFrom, LATEST_YEAR);
        Objects.requireNonNull(condition, "condition");
        weightTags = Defs.weightTags(field + ".weight_tags", weightTags, MAX_WEIGHT);
        adds = Defs.tags(field + ".adds", adds);
        Checks.inRange(field + ".duration", durationYears, 0, MAX_DURATION_YEARS);
        modifiers = List.copyOf(modifiers);
        Objects.requireNonNull(text, "text");
        // Сусід без згадки в тексті загубив би зв'язок для гравця; згадка без сусіда не мала б що підставити.
        if (neighbor != text.usesNeighbor()) {
            throw BackstoryText.invalid(field + ".text", text.template());
        }
    }

    /** Чи може колесо передісторії запропонувати фрагмент державі з цими мітками. */
    public boolean available(Set<String> tags, boolean hasNeighbor) {
        return (!neighbor || hasNeighbor) && condition.matches(tags);
    }

    /** Вага для держави з цими мітками: базова плюс добавки за наявні мітки, у межах {@code 0..}{@value #MAX_WEIGHT}. */
    public int weightFor(Set<String> tags) {
        return Defs.weightFor(weight, weightTags, tags, MAX_WEIGHT);
    }

    /** Усі мітки, від яких залежить фрагмент: умова й добавки до ваги. */
    public List<String> referencedTags() {
        return Stream.concat(condition.tags().stream(), weightTags.keySet().stream())
                .toList();
    }
}
