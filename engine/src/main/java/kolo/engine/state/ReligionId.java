package kolo.engine.state;

import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;

/**
 * Id релігії: {@code rel_} і номер зі спільного лічильника світу ({@link WorldState#nextIdSeq()}).
 *
 * <p>Порівнюється як рядок: порядок обробки лише має бути однаковим усюди, а не числовим.
 */
public record ReligionId(String value) implements Comparable<ReligionId> {

    public static final String PREFIX = "rel_";

    /** @throws ValidationException з {@link ErrorCode#INVALID_KEY_FORMAT}, якщо формат не {@code rel_<номер>} */
    public ReligionId {
        EntityIds.check("religion_id", PREFIX, value);
    }

    /** @throws ValidationException, якщо номер від'ємний */
    public static ReligionId of(long number) {
        Checks.inRange("religion_id", number, 0, Long.MAX_VALUE);
        return new ReligionId(PREFIX + number);
    }

    /** Номер зі спільного лічильника світу. */
    public long number() {
        return EntityIds.number(PREFIX, value);
    }

    @Override
    public int compareTo(ReligionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
