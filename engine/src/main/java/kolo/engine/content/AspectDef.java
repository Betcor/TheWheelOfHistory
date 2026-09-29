package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import kolo.engine.error.Checks;

/**
 * Аспект божества (GD §25.1, колесо 2): війна, знання, родючість… Аспекти відкриватимуть дари, технології й події
 * (GD §25.3); поки що аспект описано назвою й мітками.
 *
 * <p>Аспекти не бувають кращими чи гіршими, тож архетип і вже обрані аспекти впливають на вибір не перевагою, а
 * добавками до ваги за мітки — як у типів постатей.
 *
 * @param weight базова вага в колесі аспекту, {@code 1..}{@value #MAX_WEIGHT}; відносна
 * @param weightTags добавки до ваги за мітки архетипу й уже обраних аспектів; можуть бути від'ємні
 * @param tags мітки релігії з цим аспектом, напр. {@code religion_war}
 */
public record AspectDef(
        AspectId id,
        String name,
        String description,
        int weight,
        SortedMap<String, Integer> weightTags,
        List<String> tags) {

    /** Найбільша відносна вага аспекту. */
    public static final int MAX_WEIGHT = 10_000;

    public AspectDef {
        Objects.requireNonNull(id, "id");
        String field = "aspect." + id;
        Checks.notBlank(field + ".name", name);
        Checks.notBlank(field + ".description", description);
        Checks.inRange(field + ".weight", weight, 1, MAX_WEIGHT);
        weightTags = Defs.weightTags(field + ".weight_tags", weightTags, MAX_WEIGHT);
        tags = Defs.tags(field + ".tags", tags);
    }

    /** Вага для релігії з цими мітками: базова плюс добавки, у межах {@code 0..}{@value #MAX_WEIGHT}. */
    public int weightFor(Set<String> tags) {
        return Defs.weightFor(weight, weightTags, tags, MAX_WEIGHT);
    }
}
