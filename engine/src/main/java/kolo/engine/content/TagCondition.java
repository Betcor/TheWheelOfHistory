package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Умова на мітки держави, за якою контент обирає фрагменти передісторії, а згодом — події й тексти хроніки.
 *
 * @param requires мають бути всі
 * @param requiresAny має бути хоча б одна; порожній список — без цієї вимоги
 * @param excludes не має бути жодної
 */
public record TagCondition(List<String> requires, List<String> requiresAny, List<String> excludes) {

    /** Умова, якій відповідає будь-яка держава. */
    public static final TagCondition NONE = new TagCondition(List.of(), List.of(), List.of());

    /**
     * @throws ValidationException якщо мітка не {@code snake_case} або стоїть у кількох списках одразу: така умова
     *     суперечлива або надлишкова
     */
    public TagCondition {
        requires = Defs.tags("condition.requires", requires);
        requiresAny = Defs.tags("condition.requires_any", requiresAny);
        excludes = Defs.tags("condition.excludes", excludes);
        TreeSet<String> seen = new TreeSet<>();
        for (List<String> list : List.of(requires, requiresAny, excludes)) {
            for (String tag : list) {
                if (!seen.add(tag)) {
                    throw new ValidationException(
                            ErrorCode.DUPLICATE_ID, ErrorDetails.of("field", "condition", "value", tag));
                }
            }
        }
    }

    /** Чи задовольняє набір міток держави умову. */
    public boolean matches(Set<String> tags) {
        Objects.requireNonNull(tags, "tags");
        return tags.containsAll(requires)
                && (requiresAny.isEmpty() || requiresAny.stream().anyMatch(tags::contains))
                && excludes.stream().noneMatch(tags::contains);
    }

    /** Усі мітки, згадані в умові: для перевірки, що кожна з них хоч звідкись береться. */
    public List<String> tags() {
        return Stream.of(requires, requiresAny, excludes).flatMap(List::stream).toList();
    }
}
