package kolo.engine.wheel;

import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/** Перевірка ключів контенту: {@code snake_case} англійською. */
final class Ids {

    private Ids() {}

    /**
     * @param field назва поля для подробиць помилки
     * @throws ValidationException {@link ErrorCode#INVALID_KEY_FORMAT}, якщо ключ не {@code snake_case}
     */
    static String requireSnakeCase(String field, String id) {
        if (!isSnakeCase(id)) {
            throw new ValidationException(ErrorCode.INVALID_KEY_FORMAT, ErrorDetails.of("field", field, "value", id));
        }
        return id;
    }

    private static boolean isSnakeCase(String id) {
        if (id == null || id.isEmpty() || !isLowerLetter(id.charAt(0))) {
            return false;
        }
        for (int i = 1; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!isLowerLetter(c) && !(c >= '0' && c <= '9') && c != '_') {
                return false;
            }
        }
        return true;
    }

    private static boolean isLowerLetter(char c) {
        return c >= 'a' && c <= 'z';
    }
}
