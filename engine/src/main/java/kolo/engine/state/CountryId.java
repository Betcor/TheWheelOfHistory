package kolo.engine.state;

import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;

/**
 * Id держави: {@code cty_} і номер, напр. {@code cty_17}. Номер видає рушій зі спільного лічильника світу
 * ({@link WorldState#nextIdSeq()}); держави старту — номери розміщення на карті.
 *
 * <p>Порівнюється як рядок: порядок обробки лише має бути однаковим усюди, а не числовим.
 */
public record CountryId(String value) implements Comparable<CountryId> {

    public static final String PREFIX = "cty_";

    /** @throws ValidationException з {@link ErrorCode#INVALID_KEY_FORMAT}, якщо формат не {@code cty_<номер>} */
    public CountryId {
        EntityIds.check("country_id", PREFIX, value);
    }

    /** @throws ValidationException, якщо номер від'ємний */
    public static CountryId of(long number) {
        Checks.inRange("country_id", number, 0, Long.MAX_VALUE);
        return new CountryId(PREFIX + number);
    }

    /** Номер держави; у держав старту — номер розміщення на карті. */
    public long number() {
        return EntityIds.number(PREFIX, value);
    }

    @Override
    public int compareTo(CountryId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
