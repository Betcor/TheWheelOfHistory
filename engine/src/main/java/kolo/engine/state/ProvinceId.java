package kolo.engine.state;

import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;

/**
 * Id провінції: {@code prv_} і номер комірки карти, напр. {@code prv_17}. Провінції — комірки суходолу; карта
 * після генерації не змінюється, тож номер — не з лічильника світу, а номер комірки.
 *
 * <p>Порівнюється як рядок: порядок обробки лише має бути однаковим усюди, а не числовим.
 */
public record ProvinceId(String value) implements Comparable<ProvinceId> {

    public static final String PREFIX = "prv_";

    /** @throws ValidationException з {@link ErrorCode#INVALID_KEY_FORMAT}, якщо формат не {@code prv_<номер>} */
    public ProvinceId {
        EntityIds.check("province_id", PREFIX, value);
    }

    /** @throws ValidationException, якщо номер від'ємний */
    public static ProvinceId of(long number) {
        Checks.inRange("province_id", number, 0, Long.MAX_VALUE);
        return new ProvinceId(PREFIX + number);
    }

    /** Номер комірки карти. */
    public long number() {
        return EntityIds.number(PREFIX, value);
    }

    @Override
    public int compareTo(ProvinceId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
