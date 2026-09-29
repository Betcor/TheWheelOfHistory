package kolo.engine.content;

import java.util.ArrayList;
import java.util.List;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Значення від {@code min} до {@code max} з кроком {@code step}: {@code min, min + step, …, max}. Так колесо з
 * широким діапазоном має кілька рівних секторів, а не по сектору на кожне ціле число.
 *
 * @param min {@code ≥ 0}
 * @param max {@code ≥ min}; {@code max − min} ділиться на {@code step}
 * @param step {@code ≥ 1}
 */
public record StepRange(int min, int max, int step) {

    public StepRange {
        Checks.inRange("min", min, 0, Integer.MAX_VALUE);
        Checks.inRange("max", max, min, Integer.MAX_VALUE);
        Checks.inRange("step", step, 1, Integer.MAX_VALUE);
        // Інакше max мовчки випав би з колеса.
        if ((max - min) % step != 0) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "max", "value", max, "step", step));
        }
    }

    /** Усі значення за зростанням. */
    public List<Integer> values() {
        List<Integer> values = new ArrayList<>();
        for (int value = min; value <= max; value += step) {
            values.add(value);
        }
        return List.copyOf(values);
    }
}
