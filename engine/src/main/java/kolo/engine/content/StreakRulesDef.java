package kolo.engine.content;

import kolo.engine.error.Checks;

/**
 * Стріки генерації (GD §4.10): кілька дуже добрих результатів поспіль ведуть до колеса «Золотої доби», дуже
 * поганих — до колеса «Андердога».
 *
 * @param veryGoodQuality якість результату, починаючи з якої він дуже добрий
 * @param veryBadQuality якість, до якої включно результат дуже поганий; менша за {@code veryGoodQuality}
 * @param length скільки таких результатів поспіль утворюють стрік, {@code 2..}{@link #MAX_LENGTH}
 */
public record StreakRulesDef(int veryGoodQuality, int veryBadQuality, int length) {

    public static final int MAX_LENGTH = 10;

    public StreakRulesDef {
        Checks.inRange("streaks.very_bad_quality", veryBadQuality, 0, 99);
        Checks.inRange("streaks.very_good_quality", veryGoodQuality, veryBadQuality + 1, 100);
        Checks.inRange("streaks.length", length, 2, MAX_LENGTH);
    }

    /** @param quality якість результату колеса, {@code 0..100} */
    public boolean isVeryGood(int quality) {
        return quality >= veryGoodQuality;
    }

    /** @param quality якість результату колеса, {@code 0..100} */
    public boolean isVeryBad(int quality) {
        return quality <= veryBadQuality;
    }
}
