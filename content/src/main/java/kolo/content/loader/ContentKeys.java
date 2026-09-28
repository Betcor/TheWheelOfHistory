package kolo.content.loader;

import java.util.function.Function;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/** Ключ у YAML → константа enum рушія, що має ключ контенту (напр. {@code energy_science}). */
final class ContentKeys {

    private ContentKeys() {}

    /** @throws ValidationException з {@link ErrorCode#UNKNOWN_REFERENCE}, якщо жодна константа не має такого ключа */
    static <E extends Enum<E>> E parse(String field, E[] values, Function<E, String> key, String text) {
        for (E value : values) {
            if (key.apply(value).equals(text)) {
                return value;
            }
        }
        throw new ValidationException(ErrorCode.UNKNOWN_REFERENCE, ErrorDetails.of("field", field, "value", text));
    }
}
