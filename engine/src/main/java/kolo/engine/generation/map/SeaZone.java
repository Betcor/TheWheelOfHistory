package kolo.engine.generation.map;

import java.util.List;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Морська зона — зв'язна частина моря, де стоять і б'ються флоти (GD §3.5, §10.1).
 *
 * @param body номер моря в {@link SeaMap#bodies()}
 * @param seed комірка, від якої росла зона
 * @param cells номери комірок за зростанням, серед них {@code seed}
 * @param neighbors номери сусідніх зон за зростанням — з якими є спільне ребро комірок; лише того самого моря
 */
public record SeaZone(int body, int seed, List<Integer> cells, List<Integer> neighbors) {

    public SeaZone {
        Checks.inRange("body", body, 0, Integer.MAX_VALUE);
        cells = List.copyOf(cells);
        neighbors = List.copyOf(neighbors);
        ascending("cells", cells);
        ascending("neighbors", neighbors);
        if (!cells.contains(seed)) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "seed", "value", seed));
        }
    }

    private static void ascending(String field, List<Integer> values) {
        int previous = -1;
        for (int value : values) {
            if (value <= previous) {
                throw new ValidationException(ErrorCode.OUT_OF_ORDER, ErrorDetails.of("field", field, "value", value));
            }
            previous = value;
        }
    }
}
