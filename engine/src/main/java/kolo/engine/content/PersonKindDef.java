package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import kolo.engine.error.Checks;
import kolo.engine.state.PersonKind;

/**
 * Тип відомої постаті в контенті (GD §12.3) і його сектор у колесі типу постаті (GD §4.8).
 *
 * <p>Типи не бувають кращими чи гіршими, тож лад, ІЛР і передісторія впливають на тип не перевагою, а добавками до
 * ваги за мітки держави — так само, як у фрагментів передісторії: теократія частіше дає пророків, хунта —
 * генералів і претендентів.
 *
 * @param name назва українською, напр. «Генерал»
 * @param description роль постаті, для підказки гравцеві
 * @param weight базова вага в колесі типу постаті, {@code 1..}{@value #MAX_WEIGHT}; відносна — колесо нормалізує
 *     ваги типів
 * @param weightTags добавки до ваги за мітки держави; можуть бути від'ємні
 * @param tags мітки для генерації постатей і подій
 */
public record PersonKindDef(
        PersonKind kind,
        String name,
        String description,
        int weight,
        SortedMap<String, Integer> weightTags,
        List<String> tags) {

    /** Найбільша відносна вага типу в колесі типу постаті. */
    public static final int MAX_WEIGHT = 10_000;

    public PersonKindDef {
        Objects.requireNonNull(kind, "kind");
        String field = "person_kind." + kind.key();
        Checks.notBlank(field + ".name", name);
        Checks.notBlank(field + ".description", description);
        Checks.inRange(field + ".weight", weight, 1, MAX_WEIGHT);
        weightTags = Defs.weightTags(field + ".weight_tags", weightTags, MAX_WEIGHT);
        tags = Defs.tags(field + ".tags", tags);
    }

    /** Вага для держави з цими мітками: базова плюс добавки за наявні мітки, у межах {@code 0..}{@value #MAX_WEIGHT}. */
    public int weightFor(Set<String> tags) {
        return Defs.weightFor(weight, weightTags, tags, MAX_WEIGHT);
    }
}
