package kolo.engine.generation.map;

import java.util.Collections;
import java.util.List;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Річкова система — річка з усіма притоками, що виходить до води в одному гирлі.
 *
 * @param mouth гирло: комірка суходолу, з якої річка впадає у воду; входить у {@code cells}
 * @param outlet комірка моря чи озера, куди впадає річка
 * @param cells усі річкові комірки системи за зростанням, щонайменше гирло
 */
public record River(int mouth, int outlet, List<Integer> cells) {

    public River {
        Checks.inRange("mouth", mouth, 0, Integer.MAX_VALUE);
        Checks.inRange("outlet", outlet, 0, Integer.MAX_VALUE);
        cells = List.copyOf(cells);
        Checks.inRange("cells", cells.size(), 1, Integer.MAX_VALUE);
        int previous = -1;
        for (int cell : cells) {
            if (cell <= previous) {
                throw new ValidationException(ErrorCode.OUT_OF_ORDER, ErrorDetails.of("field", "cells", "value", cell));
            }
            previous = cell;
        }
        if (Collections.binarySearch(cells, mouth) < 0 || Collections.binarySearch(cells, outlet) >= 0) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "river.mouth", "value", mouth));
        }
    }
}
