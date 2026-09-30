package kolo.engine.state;

import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;

/**
 * Id відомої людини: {@code per_} і номер зі спільного лічильника світу ({@link WorldState#nextIdSeq()}).
 *
 * <p>Порівнюється як рядок: порядок обробки лише має бути однаковим усюди, а не числовим.
 */
public record PersonId(String value) implements Comparable<PersonId> {

    public static final String PREFIX = "per_";

    /** @throws ValidationException з {@link ErrorCode#INVALID_KEY_FORMAT}, якщо формат не {@code per_<номер>} */
    public PersonId {
        EntityIds.check("person_id", PREFIX, value);
    }

    /** @throws ValidationException, якщо номер від'ємний */
    public static PersonId of(long number) {
        Checks.inRange("person_id", number, 0, Long.MAX_VALUE);
        return new PersonId(PREFIX + number);
    }

    /** Номер зі спільного лічильника світу. */
    public long number() {
        return EntityIds.number(PREFIX, value);
    }

    @Override
    public int compareTo(PersonId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
