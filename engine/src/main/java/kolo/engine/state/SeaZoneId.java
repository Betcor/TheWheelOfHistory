package kolo.engine.state;

import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;

/**
 * Id морської зони: {@code sea_} і номер зони карти, напр. {@code sea_3}. Як і провінції, зони задає карта, тож
 * номер — не з лічильника світу.
 *
 * <p>Порівнюється як рядок: порядок обробки лише має бути однаковим усюди, а не числовим.
 */
public record SeaZoneId(String value) implements Comparable<SeaZoneId> {

    public static final String PREFIX = "sea_";

    /** @throws ValidationException з {@link ErrorCode#INVALID_KEY_FORMAT}, якщо формат не {@code sea_<номер>} */
    public SeaZoneId {
        EntityIds.check("sea_zone_id", PREFIX, value);
    }

    /** @throws ValidationException, якщо номер від'ємний */
    public static SeaZoneId of(long number) {
        Checks.inRange("sea_zone_id", number, 0, Long.MAX_VALUE);
        return new SeaZoneId(PREFIX + number);
    }

    /** Номер зони карти. */
    public long number() {
        return EntityIds.number(PREFIX, value);
    }

    @Override
    public int compareTo(SeaZoneId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
