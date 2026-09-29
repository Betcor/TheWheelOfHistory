package kolo.engine.content;

import java.util.Objects;
import kolo.engine.error.Checks;

/**
 * Числа генерації релігій світу (GD §25.1).
 *
 * @param aspects скільки аспектів у божества, {@code 1..}{@value #MAX_PARTS}
 * @param dogmas скільки догматів у релігії, {@code 1..}{@value #MAX_PARTS}
 */
public record ReligionBalanceDef(CountRange aspects, CountRange dogmas) {

    /** Більше аспектів чи догматів перевантажили б картку релігії й розмили б її характер. */
    public static final int MAX_PARTS = 6;

    public ReligionBalanceDef {
        check("religion.aspects", aspects);
        check("religion.dogmas", dogmas);
    }

    private static void check(String field, CountRange range) {
        Objects.requireNonNull(range, field);
        Checks.inRange(field + ".min", range.min(), 1, MAX_PARTS);
        Checks.inRange(field + ".max", range.max(), range.min(), MAX_PARTS);
    }
}
