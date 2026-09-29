package kolo.engine.content;

import java.util.Objects;
import kolo.engine.error.Checks;

/**
 * Рядок таблиці кількості релігій світу (GD §25.1): світ до {@code maxCountries} держав включно отримує від
 * {@code religions.min()} до {@code religions.max()} релігій.
 *
 * @param maxCountries верхня межа кількості держав рядка, {@code ≥ 1}
 * @param religions скільки релігій, {@code 1..}{@value #MAX_RELIGIONS}
 */
public record ReligionCountDef(int maxCountries, CountRange religions) {

    /** Більше релігій розпорошило б держави: спільна віра (GD §25.2) перестала б щось значити. */
    public static final int MAX_RELIGIONS = 12;

    public ReligionCountDef {
        Checks.inRange("religion.count.max_countries", maxCountries, 1, Integer.MAX_VALUE);
        Objects.requireNonNull(religions, "religions");
        Checks.inRange("religion.count.min", religions.min(), 1, MAX_RELIGIONS);
        Checks.inRange("religion.count.max", religions.max(), religions.min(), MAX_RELIGIONS);
    }
}
