package kolo.engine.wheel;

/**
 * Результат обертання: сектор зі значенням для системи рушія й запис для збереження та клієнта.
 *
 * <p>Запис не містить {@code value}: значення може бути будь-якого типу, а запис має серіалізуватися однаково для
 * всіх коліс.
 */
public record WheelSpin<T>(Sector<T> outcome, RollRecord record) {

    public T value() {
        return outcome.value();
    }

    public OutcomeTier tier() {
        return outcome.tier();
    }
}
