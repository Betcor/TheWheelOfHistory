package kolo.engine.archfixture;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import kolo.engine.error.Checks;

/** Дозволені конструкції, схожі на заборонені: правила не повинні на них спрацьовувати. */
public final class Allowed {

    private Allowed() {}

    public static Map<String, Integer> ordered(List<String> values) {
        Map<String, Integer> byInsertion = new LinkedHashMap<>();
        for (String value : values) {
            byInsertion.merge(value, 1, Integer::sum);
        }
        TreeSet<String> sorted = new TreeSet<>(byInsertion.keySet());
        Map<String, List<String>> grouped =
                values.stream().collect(Collectors.groupingBy(v -> v, TreeMap::new, Collectors.toList()));
        TreeMap<String, Integer> result = new TreeMap<>(byInsertion);
        result.put("sorted", sorted.size() + grouped.size());
        return result;
    }

    public static int[] copy(int[] source) {
        int[] target = new int[source.length];
        System.arraycopy(source, 0, target, 0, source.length);
        return target;
    }

    public static int clamp(long value) {
        return Math.clamp(value, 0, 100);
    }

    public static int checked(int value) {
        return Checks.inRange("value", value, 0, 100);
    }

    public static int required(Integer value) {
        return Objects.requireNonNull(value, "value");
    }
}
