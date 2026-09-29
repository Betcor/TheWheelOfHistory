package kolo.engine.generation.map;

import java.util.List;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Материк — зв'язна група комірок суходолу.
 *
 * @param seed номер комірки, від якої ріс материк
 * @param sizeWeight результат колеса ваги материка: провінції суходолу діляться пропорційно вагам
 * @param cells номери комірок у {@link MapGrid#cells()} за зростанням, серед них — {@code seed}
 */
public record Continent(int seed, int sizeWeight, List<Integer> cells) {

    public Continent {
        Checks.inRange("seed", seed, 0, Integer.MAX_VALUE);
        Checks.inRange("size_weight", sizeWeight, 1, Integer.MAX_VALUE);
        cells = List.copyOf(cells);
        Checks.inRange("cells", cells.size(), 1, Integer.MAX_VALUE);
        int previous = -1;
        for (int cell : cells) {
            if (cell <= previous) {
                throw new ValidationException(ErrorCode.OUT_OF_ORDER, ErrorDetails.of("field", "cells", "value", cell));
            }
            previous = cell;
        }
        if (!cells.contains(seed)) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "seed", "value", seed));
        }
    }
}
