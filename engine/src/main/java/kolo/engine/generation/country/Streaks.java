package kolo.engine.generation.country;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakRulesDef;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Лічильник стріків генерації (GD §4.10): скільки дуже добрих чи дуже поганих результатів коліс ідуть поспіль.
 *
 * <p>Ланцюжок коліс передає лічильнику якість кожного результату в порядку коліс ({@link #next}). Нейтральні колеса —
 * лад, кількості, відомі люди, назва, колеса самих стріків — лічильнику не передаються: вони не обривають стрік і
 * не подовжують його. Будь-який інший результат не з крайніх діапазонів стрік обриває.
 *
 * <p>Коли стрік досягає довжини з балансу, лічильник скидається, а стрік спрацьовує, якщо колесо цього виду ще не
 * крутилося: кожен вид — щонайбільше раз за генерацію держави.
 *
 * @param veryGood скільки дуже добрих результатів поспіль зараз
 * @param veryBad скільки дуже поганих результатів поспіль зараз; ненульовим може бути лише один із лічильників
 * @param fired види стріків, що вже спрацювали
 */
public record Streaks(int veryGood, int veryBad, SortedSet<StreakKind> fired) {

    /** Початок генерації: жодного результату ще не було. */
    public static final Streaks START = new Streaks(0, 0, new TreeSet<>());

    public Streaks {
        Checks.inRange("streaks.very_good", veryGood, 0, Integer.MAX_VALUE);
        Checks.inRange("streaks.very_bad", veryBad, 0, Integer.MAX_VALUE);
        if (veryGood > 0 && veryBad > 0) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "streaks.very_bad", "value", veryBad));
        }
        fired = Collections.unmodifiableSortedSet(new TreeSet<>(fired));
    }

    /**
     * Лічильник після ще одного результату колеса.
     *
     * @param quality якість результату, {@code 0..100}
     */
    public Step next(StreakRulesDef rules, int quality) {
        Objects.requireNonNull(rules, "rules");
        Checks.inRange("quality", quality, 0, 100);
        if (rules.isVeryGood(quality)) {
            return reached(veryGood + 1, 0, rules, StreakKind.GOLDEN_AGE, veryGood + 1);
        }
        if (rules.isVeryBad(quality)) {
            return reached(0, veryBad + 1, rules, StreakKind.UNDERDOG, veryBad + 1);
        }
        return new Step(new Streaks(0, 0, fired), Optional.empty());
    }

    private Step reached(int good, int bad, StreakRulesDef rules, StreakKind kind, int length) {
        if (length < rules.length()) {
            return new Step(new Streaks(good, bad, fired), Optional.empty());
        }
        if (fired.contains(kind)) {
            return new Step(new Streaks(0, 0, fired), Optional.empty());
        }
        TreeSet<StreakKind> nowFired = new TreeSet<>(fired);
        nowFired.add(kind);
        return new Step(new Streaks(0, 0, nowFired), Optional.of(kind));
    }

    /**
     * Результат кроку лічильника.
     *
     * @param streaks лічильник після результату
     * @param triggered вид стріку, колесо якого треба крутити зараз; порожньо, якщо стрік не спрацював
     */
    public record Step(Streaks streaks, Optional<StreakKind> triggered) {

        public Step {
            Objects.requireNonNull(streaks, "streaks");
            Objects.requireNonNull(triggered, "triggered");
        }
    }
}
