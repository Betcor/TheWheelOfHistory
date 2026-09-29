package kolo.engine.content;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/** Спільні перевірки визначень контенту. */
final class Defs {

    private Defs() {}

    /** Теги: {@code snake_case} без повторів; порядок зберігається. */
    static List<String> tags(String field, List<String> tags) {
        TreeSet<String> seen = new TreeSet<>();
        for (String tag : tags) {
            Checks.snakeCase(field, tag);
            if (!seen.add(tag)) {
                throw new ValidationException(ErrorCode.DUPLICATE_ID, ErrorDetails.of("field", field, "value", tag));
            }
        }
        return List.copyOf(tags);
    }

    /**
     * Добавки до ваги за мітки: мітки {@code snake_case}, значення {@code −max..max}.
     *
     * @return незмінна копія, впорядкована за мітками
     */
    static SortedMap<String, Integer> weightTags(String field, Map<String, Integer> weightTags, int max) {
        TreeMap<String, Integer> bonuses = new TreeMap<>();
        for (Map.Entry<String, Integer> bonus : weightTags.entrySet()) {
            Checks.snakeCase(field, bonus.getKey());
            String bonusField = field + "." + bonus.getKey();
            Integer value = bonus.getValue();
            if (value == null) {
                throw new ValidationException(ErrorCode.BLANK_VALUE, ErrorDetails.of("field", bonusField));
            }
            Checks.inRange(bonusField, value, -max, max);
            bonuses.put(bonus.getKey(), value);
        }
        return Collections.unmodifiableSortedMap(bonuses);
    }

    /** Базова вага плюс добавки за наявні мітки, у межах {@code 0..max}. */
    static int weightFor(int weight, SortedMap<String, Integer> weightTags, Set<String> tags, int max) {
        Objects.requireNonNull(tags, "tags");
        long total = weight;
        for (Map.Entry<String, Integer> bonus : weightTags.entrySet()) {
            if (tags.contains(bonus.getKey())) {
                total += bonus.getValue();
            }
        }
        return Math.clamp(total, 0, max);
    }

    /** Ключ з {@code id} уже зустрічався в {@code seen}. */
    static <K extends Comparable<K>> void unique(String field, TreeSet<K> seen, K id) {
        if (!seen.add(id)) {
            throw new ValidationException(ErrorCode.DUPLICATE_ID, ErrorDetails.of("field", field, "value", id));
        }
    }

    /** Жоден ключ у {@code ids} не повторюється. */
    static <K extends Comparable<K>> void uniqueAll(String field, List<K> ids) {
        TreeSet<K> seen = new TreeSet<>();
        for (K id : ids) {
            unique(field, seen, Objects.requireNonNull(id, field));
        }
    }

    /**
     * Кожен ключ з {@code expected} має визначення, інакше {@link ErrorCode#MISSING_DEFINITION}.
     *
     * @param display як показати ключ у подробицях помилки: ключ контенту, а не ім'я константи enum
     * @return незмінне представлення {@code map}
     */
    static <K extends Comparable<K>, V> SortedMap<K, V> complete(
            String field, TreeMap<K, V> map, Collection<K> expected, Function<K, Object> display) {
        for (K key : expected) {
            if (!map.containsKey(key)) {
                throw new ValidationException(
                        ErrorCode.MISSING_DEFINITION, ErrorDetails.of("field", field, "value", display.apply(key)));
            }
        }
        return Collections.unmodifiableSortedMap(map);
    }
}
