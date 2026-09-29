package kolo.engine.state;

import java.util.List;
import java.util.stream.IntStream;
import kolo.engine.error.Checks;

/**
 * Рівень вишколу армії (GD §4.4): від {@link #MIN} (ополчення) до {@link #MAX} (еліта), {@link #REGULAR} —
 * регулярна армія. Усередині рівня вишкіл росте прогресом {@code 0..100} — разом з арміями.
 *
 * <p>Межі — частина правил, а не балансу: на п'ять рівнів спиратимуться бої, навчання й поповнення.
 */
public final class Training {

    public static final int MIN = 1;
    public static final int REGULAR = 3;
    public static final int MAX = 5;

    private Training() {}

    /** Усі рівні від {@link #MIN} до {@link #MAX} за зростанням. */
    public static List<Integer> levels() {
        return IntStream.rangeClosed(MIN, MAX).boxed().toList();
    }

    /** {@code level} у межах {@code [MIN, MAX]}, інакше {@link kolo.engine.error.ErrorCode#VALUE_OUT_OF_RANGE}. */
    public static int check(String field, int level) {
        return Checks.inRange(field, level, MIN, MAX);
    }
}
