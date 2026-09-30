package kolo.engine.generation.map;

import java.util.List;
import java.util.Objects;
import kolo.engine.content.AreaLevelId;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.wheel.RollRecord;

/**
 * Територія однієї держави — результат {@link PlacementGenerator}.
 *
 * @param continent номер материка в {@link ContinentMap#continents()} (колесо 1 GD §4.1)
 * @param area рівень площі з колеса площі (колесо 2 GD §4.1)
 * @param tags мітки рівня площі — вхід для наступних коліс генерації
 * @param target скільки провінцій держава мала отримати після масштабування площ
 * @param seed комірка, від якої росла територія, — серед {@code cells}
 * @param cells комірки суходолу держави за зростанням; зазвичай рівно {@code target}; якщо в усіх спробах розкладки
 *     сусіди замикали якусь державу, вона менша, а недобір дістається іншій державі того ж материка
 * @param rolls обертання коліс материка й площі, у порядку кидків
 */
public record PlacedCountry(
        int continent,
        AreaLevelId area,
        List<String> tags,
        int target,
        int seed,
        List<Integer> cells,
        List<RollRecord> rolls) {

    public PlacedCountry {
        Checks.inRange("continent", continent, 0, Integer.MAX_VALUE);
        Objects.requireNonNull(area, "area");
        tags = List.copyOf(tags);
        Checks.inRange("target", target, 1, Integer.MAX_VALUE);
        Checks.inRange("seed", seed, 0, Integer.MAX_VALUE);
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
        rolls = List.copyOf(rolls);
    }

    /** Провінцій держави. */
    public int provinces() {
        return cells.size();
    }
}
