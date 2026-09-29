package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import kolo.engine.error.Checks;

/**
 * Устрій релігії (GD §25.1, колесо 4): єдина церква, незалежні громади, без духовенства. Впливатиме на стабільність,
 * розколи й роль пророків.
 *
 * @param weight базова вага в колесі устрою, {@code 1..}{@value #MAX_WEIGHT}; відносна
 * @param weightTags добавки до ваги за мітки архетипу, аспектів і догматів; можуть бути від'ємні
 * @param modifiers модифікатори держав цієї віри
 * @param tags мітки релігії з цим устроєм, напр. {@code polity_single_church}
 */
public record ReligionPolityDef(
        ReligionPolityId id,
        String name,
        String description,
        int weight,
        SortedMap<String, Integer> weightTags,
        List<ModifierDef> modifiers,
        List<String> tags) {

    /** Найбільша відносна вага устрою. */
    public static final int MAX_WEIGHT = 10_000;

    public ReligionPolityDef {
        Objects.requireNonNull(id, "id");
        String field = "religion_polity." + id;
        Checks.notBlank(field + ".name", name);
        Checks.notBlank(field + ".description", description);
        Checks.inRange(field + ".weight", weight, 1, MAX_WEIGHT);
        weightTags = Defs.weightTags(field + ".weight_tags", weightTags, MAX_WEIGHT);
        modifiers = List.copyOf(modifiers);
        tags = Defs.tags(field + ".tags", tags);
    }

    /** Вага для релігії з цими мітками: базова плюс добавки, у межах {@code 0..}{@value #MAX_WEIGHT}. */
    public int weightFor(Set<String> tags) {
        return Defs.weightFor(weight, weightTags, tags, MAX_WEIGHT);
    }
}
