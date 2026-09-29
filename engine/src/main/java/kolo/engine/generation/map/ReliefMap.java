package kolo.engine.generation.map;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.SortedMap;
import java.util.TreeMap;
import kolo.engine.content.ReliefDef;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Relief;
import kolo.engine.wheel.RollRecord;

/**
 * Висота й рельєф суходолу — результат {@link ReliefGenerator}. Море висоти не має.
 *
 * @param heights номер комірки суходолу → висота {@code 0..}{@value ReliefDef#MAX_HEIGHT}
 * @param reliefs номер комірки суходолу → рельєф за порогами контенту; ті самі комірки, що в {@code heights}
 * @param ridges хребти в порядку материків, а в межах материка — в порядку появи
 * @param rolls обертання коліс кількості хребтів, по одному на материк
 */
public record ReliefMap(
        SortedMap<Integer, Integer> heights,
        SortedMap<Integer, Relief> reliefs,
        List<Ridge> ridges,
        List<RollRecord> rolls) {

    public ReliefMap {
        heights = Collections.unmodifiableSortedMap(new TreeMap<>(heights));
        reliefs = Collections.unmodifiableSortedMap(new TreeMap<>(reliefs));
        ridges = List.copyOf(ridges);
        rolls = List.copyOf(rolls);
        for (Map.Entry<Integer, Integer> entry : heights.entrySet()) {
            Checks.inRange("cell", entry.getKey(), 0, Integer.MAX_VALUE);
            Checks.inRange("height", entry.getValue(), 0, ReliefDef.MAX_HEIGHT);
        }
        if (!heights.keySet().equals(reliefs.keySet())) {
            throw new ValidationException(ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "reliefs"));
        }
        for (Ridge ridge : ridges) {
            for (int cell : ridge.cells()) {
                if (!heights.containsKey(cell)) {
                    throw new ValidationException(
                            ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "ridge.cells", "value", cell));
                }
            }
        }
    }

    /** Висота комірки; порожньо — море. */
    public OptionalInt height(int cell) {
        Integer height = heights.get(cell);
        return height == null ? OptionalInt.empty() : OptionalInt.of(height);
    }

    /** Рельєф комірки; порожньо — море. */
    public Optional<Relief> relief(int cell) {
        return Optional.ofNullable(reliefs.get(cell));
    }

    /** Скільки комірок суходолу має рельєф {@code relief}. */
    public int count(Relief relief) {
        return (int) reliefs.values().stream().filter(value -> value == relief).count();
    }
}
