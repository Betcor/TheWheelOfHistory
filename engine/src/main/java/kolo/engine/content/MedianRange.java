package kolo.engine.content;

import kolo.engine.error.Checks;

/**
 * Межі сили держави відносно медіани, у відсотках: {@code 50..200} — від 0,5× до 2× медіани (GD §4.11).
 *
 * @param minPct {@code 1..100}: медіана завжди всередині
 * @param maxPct {@code 100..}{@link #MAX_PCT}
 */
public record MedianRange(int minPct, int maxPct) {

    /** 100× медіани: ширше коридор уже нічого не обмежує. */
    public static final int MAX_PCT = 10_000;

    public MedianRange {
        Checks.inRange("min_pct", minPct, 1, 100);
        Checks.inRange("max_pct", maxPct, 100, MAX_PCT);
    }

    /** Чи лежить {@code other} цілком у цих межах. */
    public boolean contains(MedianRange other) {
        return other.minPct >= minPct && other.maxPct <= maxPct;
    }
}
