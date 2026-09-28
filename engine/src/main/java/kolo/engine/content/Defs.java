package kolo.engine.content;

import java.util.List;
import java.util.TreeSet;
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

    /** Ключ з {@code id} уже зустрічався в {@code seen}. */
    static <K extends Comparable<K>> void unique(String field, TreeSet<K> seen, K id) {
        if (!seen.add(id)) {
            throw new ValidationException(ErrorCode.DUPLICATE_ID, ErrorDetails.of("field", field, "value", id));
        }
    }
}
