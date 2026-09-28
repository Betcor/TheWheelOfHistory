package kolo.engine.content;

import java.util.Objects;
import kolo.engine.error.Checks;

/**
 * Кількості в ланцюжку коліс генерації країни (GD §4.1).
 *
 * @param backstoryFragments скільки фрагментів передісторії (GD §4.7), не менше одного
 * @param notablePeople скільки відомих людей на старті (GD §4.8), не менше одного
 */
public record GenerationBalanceDef(CountRange backstoryFragments, CountRange notablePeople) {

    /** Більше фрагментів чи постатей перевантажили б картку країни. */
    public static final int MAX_COUNT = 10;

    public GenerationBalanceDef {
        check("generation.backstory_fragments", backstoryFragments);
        check("generation.notable_people", notablePeople);
    }

    private static void check(String field, CountRange range) {
        Objects.requireNonNull(range, field);
        Checks.inRange(field + ".min", range.min(), 1, MAX_COUNT);
        Checks.inRange(field + ".max", range.max(), range.min(), MAX_COUNT);
    }
}
