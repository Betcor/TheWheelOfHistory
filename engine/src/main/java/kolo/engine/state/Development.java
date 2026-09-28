package kolo.engine.state;

import java.util.List;
import java.util.stream.IntStream;
import kolo.engine.error.Checks;

/**
 * Рівень технологічної розвиненості в галузі відносно світового рівня 1970 року (GD §4.3): від {@link #MIN}
 * (глибоке відставання) до {@link #MAX} (лідер), {@link #WORLD} — світовий рівень.
 *
 * <p>Межі — частина правил, а не балансу: від них залежить, які технології дерева відкриті на старті.
 */
public final class Development {

    public static final int MIN = -3;
    public static final int WORLD = 0;
    public static final int MAX = 2;

    private Development() {}

    /** Усі рівні від {@link #MIN} до {@link #MAX} за зростанням. */
    public static List<Integer> levels() {
        return IntStream.rangeClosed(MIN, MAX).boxed().toList();
    }

    /** {@code level} у межах {@code [MIN, MAX]}, інакше {@link kolo.engine.error.ErrorCode#VALUE_OUT_OF_RANGE}. */
    public static int check(String field, int level) {
        return Checks.inRange(field, level, MIN, MAX);
    }
}
