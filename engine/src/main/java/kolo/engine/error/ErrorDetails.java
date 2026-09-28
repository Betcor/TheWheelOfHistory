package kolo.engine.error;

import java.util.SortedMap;
import java.util.TreeMap;

/** Побудова подробиць винятку. */
public final class ErrorDetails {

    private ErrorDetails() {}

    /**
     * Подробиці з пар «ключ, значення»: {@code ErrorDetails.of("field", "stability", "value", 120)}.
     *
     * <p>Не {@code Map.of}: її порядок ітерації не визначений, а в рушії порядок має бути стабільним.
     *
     * @throws IllegalArgumentException якщо аргументів непарна кількість або ключ не рядок — це помилка
     *     програміста, а не гри
     */
    public static SortedMap<String, Object> of(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("непарна кількість аргументів: " + keysAndValues.length);
        }
        TreeMap<String, Object> result = new TreeMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            if (!(keysAndValues[i] instanceof String key)) {
                throw new IllegalArgumentException("ключ подробиць має бути рядком: " + keysAndValues[i]);
            }
            result.put(key, keysAndValues[i + 1]);
        }
        return result;
    }
}
