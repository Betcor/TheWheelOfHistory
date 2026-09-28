package kolo.engine.state;

import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Id держави: {@code cty_} і номер, напр. {@code cty_17}. Номер видає рушій зі спільного лічильника світу.
 *
 * <p>Порівнюється як рядок: порядок обробки держав лише має бути однаковим усюди, а не числовим.
 */
public record CountryId(String value) implements Comparable<CountryId> {

    public static final String PREFIX = "cty_";

    /** @throws ValidationException з {@link ErrorCode#INVALID_KEY_FORMAT}, якщо формат не {@code cty_<цифри>} */
    public CountryId {
        if (!isValid(value)) {
            throw new ValidationException(
                    ErrorCode.INVALID_KEY_FORMAT,
                    ErrorDetails.of("field", "country_id", "value", String.valueOf(value)));
        }
    }

    public static CountryId of(long number) {
        return new CountryId(PREFIX + number);
    }

    @Override
    public int compareTo(CountryId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }

    private static boolean isValid(String value) {
        if (value == null || !value.startsWith(PREFIX) || value.length() == PREFIX.length()) {
            return false;
        }
        for (int i = PREFIX.length(); i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
