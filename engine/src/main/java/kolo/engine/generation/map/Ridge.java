package kolo.engine.generation.map;

import java.util.List;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Гірський хребет — ламана з комірок суходолу одного материка.
 *
 * @param continent номер материка в {@link ContinentMap#continents()}
 * @param cells номери комірок у порядку вздовж хребта, без повторів; щонайменше одна. Хребти можуть перетинатися.
 */
public record Ridge(int continent, List<Integer> cells) {

    public Ridge {
        Checks.inRange("continent", continent, 0, Integer.MAX_VALUE);
        cells = List.copyOf(cells);
        Checks.inRange("cells", cells.size(), 1, Integer.MAX_VALUE);
        TreeSet<Integer> seen = new TreeSet<>();
        for (int cell : cells) {
            if (!seen.add(cell)) {
                throw new ValidationException(ErrorCode.DUPLICATE_ID, ErrorDetails.of("field", "cells", "value", cell));
            }
        }
    }
}
