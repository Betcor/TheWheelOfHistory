package kolo.engine.error;

/** Типові перевірки значень, що кидають {@link ValidationException} з однаковими подробицями. */
public final class Checks {

    private Checks() {}

    /** {@code value} у межах {@code [min, max]}, інакше {@link ErrorCode#VALUE_OUT_OF_RANGE}. */
    public static int inRange(String field, int value, int min, int max) {
        inRange(field, (long) value, min, max);
        return value;
    }

    /** {@code value} у межах {@code [min, max]}, інакше {@link ErrorCode#VALUE_OUT_OF_RANGE}. */
    public static long inRange(String field, long value, long min, long max) {
        if (value < min || value > max) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE,
                    ErrorDetails.of("field", field, "value", value, "min", min, "max", max));
        }
        return value;
    }

    /** Рядок не {@code null} і не порожній, інакше {@link ErrorCode#BLANK_VALUE}. */
    public static String notBlank(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(ErrorCode.BLANK_VALUE, ErrorDetails.of("field", field));
        }
        return value;
    }

    /**
     * Ключ контенту у форматі {@code snake_case} англійською: з малої латинської літери, далі малі літери, цифри й
     * {@code _}. Інакше {@link ErrorCode#INVALID_KEY_FORMAT}.
     */
    public static String snakeCase(String field, String value) {
        if (!isSnakeCase(value)) {
            throw new ValidationException(
                    ErrorCode.INVALID_KEY_FORMAT, ErrorDetails.of("field", field, "value", value));
        }
        return value;
    }

    private static boolean isSnakeCase(String value) {
        if (value == null || value.isEmpty() || !isLowerLetter(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
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
