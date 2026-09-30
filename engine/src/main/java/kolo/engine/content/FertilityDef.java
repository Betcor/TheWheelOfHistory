package kolo.engine.content;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import kolo.engine.error.Checks;
import kolo.engine.state.Climate;
import kolo.engine.state.Terrain;

/**
 * Числа родючості провінцій (GD §3.5): таблиця без кидків. Родючість = основа поясу + поправка місцевості + волога ×
 * {@code moisturePct} / 100 (вниз) + бонус річки, обрізано до {@code 0..}{@value #MAX_VALUE}.
 *
 * @param climates основа на кожен пояс, {@code 0..}{@value #MAX_VALUE}
 * @param terrains поправка на кожен тип місцевості, {@code −}{@value #MAX_VALUE}{@code ..}{@value #MAX_VALUE}
 * @param moisturePct скільки відсотків вологи провінції додається, {@code 0..100}: волога відрізняє землі в межах
 *     одного поясу
 * @param river бонус провінції з річкою, {@code 0..}{@value #MAX_VALUE}
 */
public record FertilityDef(
        SortedMap<Climate, Integer> climates, SortedMap<Terrain, Integer> terrains, int moisturePct, int river) {

    /** Родючість — {@code 0..MAX_VALUE}, як показники держави. */
    public static final int MAX_VALUE = 100;

    public FertilityDef {
        climates = check("fertility.climates", climates, 0, List.of(Climate.values()), Climate::key);
        terrains = check("fertility.terrains", terrains, -MAX_VALUE, List.of(Terrain.values()), Terrain::key);
        Checks.inRange("fertility.moisture_pct", moisturePct, 0, 100);
        Checks.inRange("fertility.river", river, 0, MAX_VALUE);
    }

    /**
     * Родючість провінції.
     *
     * @param moisture волога провінції, {@code 0..}{@value ClimateDef#MAX_VALUE}
     * @param hasRiver чи провінцією тече річка
     */
    public int fertility(Climate climate, Terrain terrain, int moisture, boolean hasRiver) {
        Objects.requireNonNull(climate, "climate");
        Objects.requireNonNull(terrain, "terrain");
        Checks.inRange("moisture", moisture, 0, ClimateDef.MAX_VALUE);
        int value =
                climates.get(climate) + terrains.get(terrain) + moisture * moisturePct / 100 + (hasRiver ? river : 0);
        return Math.clamp(value, 0, MAX_VALUE);
    }

    private static <K extends Comparable<K>> SortedMap<K, Integer> check(
            String field, SortedMap<K, Integer> values, int min, List<K> expected, Function<K, Object> key) {
        TreeMap<K, Integer> copy = new TreeMap<>();
        for (Map.Entry<K, Integer> entry : Objects.requireNonNull(values, field).entrySet()) {
            String where = field + "." + key.apply(entry.getKey());
            copy.put(
                    entry.getKey(),
                    Checks.inRange(where, Objects.requireNonNull(entry.getValue(), where), min, MAX_VALUE));
        }
        return Defs.complete(field, copy, expected, key);
    }
}
