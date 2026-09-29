package kolo.tools.sim;

import java.util.List;
import java.util.Objects;

/**
 * Неправильний виклик CLI: невідома команда чи параметр, бракує значення тощо. Не {@code GameException}: це помилка
 * того, хто запускає інструмент, а не стану гри.
 */
final class UsageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String key;
    private final transient List<Object> args;

    /** @param key ключ тексту в {@code sim_uk.properties} */
    UsageException(String key, Object... args) {
        super(key);
        this.key = Objects.requireNonNull(key, "key");
        this.args = List.of(args);
    }

    String key() {
        return key;
    }

    /** Текст для людини. */
    String text() {
        return SimText.text(key, args.toArray());
    }
}
