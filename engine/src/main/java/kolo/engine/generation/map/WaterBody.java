package kolo.engine.generation.map;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Водойма — зв'язна область комірок води.
 *
 * @param kind море чи озеро
 * @param cells номери комірок за зростанням, щонайменше одна
 */
public record WaterBody(WaterKind kind, List<Integer> cells) {

    public WaterBody {
        Objects.requireNonNull(kind, "kind");
        cells = List.copyOf(cells);
        Checks.inRange("cells", cells.size(), 1, Integer.MAX_VALUE);
        int previous = -1;
        for (int cell : cells) {
            if (cell <= previous) {
                throw new ValidationException(ErrorCode.OUT_OF_ORDER, ErrorDetails.of("field", "cells", "value", cell));
            }
            previous = cell;
        }
    }
}
