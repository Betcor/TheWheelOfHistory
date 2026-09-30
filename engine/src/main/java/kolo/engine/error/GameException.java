package kolo.engine.error;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Корінь ієрархії винятків гри.
 *
 * <p>Подробиці — впорядкована за ключами незмінна мапа, тож повідомлення й серіалізація детерміновані. Значення
 * зберігаються як {@link String}, {@link Number} або {@link Boolean}; усе інше (зокрема {@code null}) перетворюється на рядок, щоб
 * подробиці завжди можна було передати клієнтові в {@code ServerMessage.Error}. Обов'язкові подробиці коду ({@link
 * ErrorCode#requiredDetails()}) мусять бути.
 */
public abstract class GameException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;
    // transient: винятки передаються клієнтові через протокол (Jackson), а не Java-серіалізацією.
    private final transient SortedMap<String, Object> details;

    protected GameException(ErrorCode code, Map<String, ?> details) {
        this(code, details, null);
    }

    // isInstance(this) читає лише клас об'єкта, а не поля підкласу, тож «втеча» this тут безпечна.
    @SuppressWarnings("this-escape")
    protected GameException(ErrorCode code, Map<String, ?> details, Throwable cause) {
        this(code, normalize(details), cause);
    }

    @SuppressWarnings("this-escape")
    private GameException(ErrorCode code, SortedMap<String, Object> details, Throwable cause) {
        super(Objects.requireNonNull(code, "code").key() + " " + details, cause);
        if (!code.exceptionType().isInstance(this)) {
            throw new IllegalArgumentException(
                    "код " + code + " належить " + code.exceptionType().getSimpleName() + ", а не "
                            + getClass().getSimpleName());
        }
        if (!details.keySet().containsAll(code.requiredDetails())) {
            throw new IllegalArgumentException("коду " + code + " бракує подробиць: потрібні " + code.requiredDetails()
                    + ", є " + details.keySet());
        }
        this.code = code;
        this.details = details;
    }

    public ErrorCode code() {
        return code;
    }

    /** Подробиці для підстановки в текст помилки; впорядковані за ключем, незмінні. */
    public SortedMap<String, Object> details() {
        return details;
    }

    private static SortedMap<String, Object> normalize(Map<String, ?> details) {
        TreeMap<String, Object> result = new TreeMap<>();
        details.forEach((key, value) -> result.put(Objects.requireNonNull(key, "ключ подробиць"), simplify(value)));
        return Collections.unmodifiableSortedMap(result);
    }

    private static Object simplify(Object value) {
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return String.valueOf(value);
    }
}
