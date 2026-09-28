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
}
