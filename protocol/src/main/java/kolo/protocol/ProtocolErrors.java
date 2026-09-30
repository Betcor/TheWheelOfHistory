package kolo.protocol;

import java.util.Locale;
import java.util.TreeMap;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.GameException;
import kolo.engine.error.ProtocolException;

/** Помилки {@link ErrorCode#PROTOCOL_ERROR} з місцем усередині повідомлення. */
public final class ProtocolErrors {

    private ProtocolErrors() {}

    /**
     * Порушено структуру чи порядок повідомлень.
     *
     * @param location місце: шлях усередині повідомлення ({@code cells[3].site}) або частина розмови ({@code map})
     * @param problem опис для розробника, не для гравця: {@code unknown_field}, {@code map_not_started}…
     */
    public static ProtocolException malformed(String location, String problem) {
        return new ProtocolException(
                ErrorCode.PROTOCOL_ERROR, ErrorDetails.of("location", location, "problem", problem));
    }

    /** Значення в повідомленні невалідне: код і подробиці первинної помилки зберігаються. */
    public static ProtocolException because(String location, GameException cause) {
        TreeMap<String, Object> details = new TreeMap<>(cause.details());
        details.put("location", location);
        details.put("cause", cause.code().name().toLowerCase(Locale.ROOT));
        return new ProtocolException(ErrorCode.PROTOCOL_ERROR, details, cause);
    }
}
