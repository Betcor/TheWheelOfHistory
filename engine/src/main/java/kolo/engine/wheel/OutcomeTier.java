package kolo.engine.wheel;

/** Рівень результату сектора: від критичного провалу до критичного успіху. */
public enum OutcomeTier {
    CRIT_FAIL,
    FAIL,
    PARTIAL,
    SUCCESS,
    CRIT_SUCCESS;

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
