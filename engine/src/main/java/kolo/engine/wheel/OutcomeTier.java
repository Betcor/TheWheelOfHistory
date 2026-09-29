package kolo.engine.wheel;

import java.util.Locale;

/** Рівень результату сектора: від критичного провалу до критичного успіху. */
public enum OutcomeTier {
    CRIT_FAIL,
    FAIL,
    PARTIAL,
    SUCCESS,
    CRIT_SUCCESS;

    /** Ключ у контенті, напр. {@code crit_success}. */
    public String key() {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return name().toLowerCase(Locale.ROOT);
    }

    /** Сектор росте з додатною перевагою. */
    public boolean isSuccess() {
        return this == SUCCESS || this == CRIT_SUCCESS;
    }

    /** Сектор зменшується з додатною перевагою. */
    public boolean isFailure() {
        return this == FAIL || this == CRIT_FAIL;
    }

    /** Сектор, що ніколи не зникає повністю: має мінімальну вагу {@link Wheel#MIN_CRITICAL_BP}. */
    public boolean isCritical() {
        return this == CRIT_FAIL || this == CRIT_SUCCESS;
    }
}
