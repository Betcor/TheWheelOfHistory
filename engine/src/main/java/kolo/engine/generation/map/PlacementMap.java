package kolo.engine.generation.map;

import java.util.ArrayList;
import java.util.List;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.wheel.RollRecord;

/**
 * Держави на карті — результат {@link PlacementGenerator}.
 *
 * @param countries держави в порядку генерації: спершу гравці, потім NPC; номер у списку — номер держави
 * @param cellCountries для кожної комірки сітки — номер держави або {@link #NONE} (море чи нічийна земля)
 */
public record PlacementMap(List<PlacedCountry> countries, List<Integer> cellCountries) {

    /** Комірка без держави: море або нічийна земля. */
    public static final int NONE = -1;

    public PlacementMap {
        countries = List.copyOf(countries);
        cellCountries = List.copyOf(cellCountries);
        Checks.inRange("countries", countries.size(), 1, Integer.MAX_VALUE);
        long claimed = 0;
        for (int i = 0; i < countries.size(); i++) {
            for (int cell : countries.get(i).cells()) {
                Checks.inRange("cell", cell, 0, cellCountries.size() - 1);
                if (cellCountries.get(cell) != i) {
                    throw new ValidationException(
                            ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "cell_countries", "value", cell));
                }
            }
            claimed += countries.get(i).cells().size();
        }
        long marked = cellCountries.stream().filter(owner -> owner != NONE).count();
        if (marked != claimed) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "cell_countries", "value", marked));
        }
    }

    /** Номер держави комірки або {@link #NONE}. */
    public int country(int cell) {
        return cellCountries.get(cell);
    }

    public boolean isClaimed(int cell) {
        return cellCountries.get(cell) != NONE;
    }

    /** Скільки провінцій належить державам. */
    public int claimedCells() {
        return countries.stream().mapToInt(PlacedCountry::provinces).sum();
    }

    /** Обертання всіх держав у порядку кидків: материк і площа першої держави, потім другої… */
    public List<RollRecord> rolls() {
        List<RollRecord> rolls = new ArrayList<>();
        countries.forEach(country -> rolls.addAll(country.rolls()));
        return List.copyOf(rolls);
    }
}
