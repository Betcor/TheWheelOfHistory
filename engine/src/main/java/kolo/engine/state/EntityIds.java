package kolo.engine.state;

import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/** Спільний формат id сутностей стану: префікс і номер, напр. {@code prv_17}. */
final class EntityIds {

    private static final String MAX_NUMBER = Long.toString(Long.MAX_VALUE);

    private EntityIds() {}

    /**
     * @param field назва поля для подробиць помилки, напр. {@code province_id}
     * @throws ValidationException з {@link ErrorCode#INVALID_KEY_FORMAT}, якщо формат не {@code <префікс><цифри>}
     */
    static void check(String field, String prefix, String value) {
        if (!isValid(prefix, value)) {
            throw new ValidationException(
                    ErrorCode.INVALID_KEY_FORMAT, ErrorDetails.of("field", field, "value", String.valueOf(value)));
        }
    }

    /** Номер id; формат уже перевірено. */
    static long number(String prefix, String value) {
        return Long.parseLong(value.substring(prefix.length()));
    }

    private static boolean isValid(String prefix, String value) {
        if (value == null || !value.startsWith(prefix)) {
            return false;
        }
        String digits = value.substring(prefix.length());
        // Без провідних нулів, щоб номер і id відповідали одне одному; не більше за long.
        if (digits.isEmpty()
                || (digits.charAt(0) == '0' && digits.length() > 1)
                || digits.length() > MAX_NUMBER.length()
                || (digits.length() == MAX_NUMBER.length() && digits.compareTo(MAX_NUMBER) > 0)) {
            return false;
        }
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
