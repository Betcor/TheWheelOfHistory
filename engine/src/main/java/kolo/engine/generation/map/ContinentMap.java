package kolo.engine.generation.map;

import java.util.List;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.wheel.RollRecord;

/**
 * Суходіл і море карти — результат {@link ContinentGenerator}.
 *
 * @param continents материки в порядку коліс їхньої ваги; номер у списку — номер материка
 * @param cellContinents для кожної комірки сітки — номер її материка або {@link #SEA}
 * @param rolls обертання коліс ваги материків, по одному на материк
 */
public record ContinentMap(List<Continent> continents, List<Integer> cellContinents, List<RollRecord> rolls) {

    /** Комірка моря. */
    public static final int SEA = -1;

    public ContinentMap {
        continents = List.copyOf(continents);
        cellContinents = List.copyOf(cellContinents);
        rolls = List.copyOf(rolls);
        Checks.inRange("continents", continents.size(), 1, Integer.MAX_VALUE);
        int land = 0;
        for (int i = 0; i < continents.size(); i++) {
            for (int cell : continents.get(i).cells()) {
                Checks.inRange("cell", cell, 0, cellContinents.size() - 1);
                if (cellContinents.get(cell) != i) {
                    throw new ValidationException(
                            ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "cell_continents", "value", cell));
                }
            }
            land += continents.get(i).cells().size();
        }
        long marked = cellContinents.stream().filter(owner -> owner != SEA).count();
        if (marked != land) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "cell_continents", "value", marked));
        }
    }

    public boolean isLand(int cell) {
        return cellContinents.get(cell) != SEA;
    }

    /** Скільки комірок суходолу — провінцій. */
    public int landCells() {
        return continents.stream()
                .mapToInt(continent -> continent.cells().size())
                .sum();
    }
}
