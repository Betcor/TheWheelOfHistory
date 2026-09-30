package kolo.engine.generation.map;

import java.util.Collections;
import java.util.Map;
import java.util.OptionalInt;
import java.util.SortedMap;
import java.util.TreeMap;
import kolo.engine.content.FertilityDef;
import kolo.engine.error.Checks;

/**
 * Родючість суходолу — результат {@link FertilityGenerator}; майбутнє {@code Province.fertility}.
 *
 * @param fertilities комірка суходолу → родючість {@code 0..}{@value FertilityDef#MAX_VALUE}
 */
public record FertilityMap(SortedMap<Integer, Integer> fertilities) {

    public FertilityMap {
        fertilities = Collections.unmodifiableSortedMap(new TreeMap<>(fertilities));
        for (Map.Entry<Integer, Integer> entry : fertilities.entrySet()) {
            Checks.inRange("cell", entry.getKey(), 0, Integer.MAX_VALUE);
            Checks.inRange("fertility", entry.getValue(), 0, FertilityDef.MAX_VALUE);
        }
    }

    /** Родючість комірки; порожньо — вода. */
    public OptionalInt fertility(int cell) {
        Integer fertility = fertilities.get(cell);
        return fertility == null ? OptionalInt.empty() : OptionalInt.of(fertility);
    }
}
