package kolo.engine.content;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Climate;
import kolo.engine.state.Terrain;

/**
 * Де трапляються родовища ресурсу (GD §4.5): придатність провінції — таблиця без кидків. Колесо ресурсів держави
 * обирає ресурс вагою за сумою придатності її провінцій, а провінцію родовища — вагою за придатністю.
 *
 * <p>Два способи, які не змішуються:
 *
 * <ul>
 *   <li><b>за місцевістю</b> — придатність = {@code terrains[місцевість]} × {@code climates[пояс]} / 100 (вниз);
 *       місцевість без значення — 0, пояс без значення — 100%;
 *   <li><b>за родючістю</b> ({@code fertilityFrom}) — придатність = родючість провінції, якщо вона не нижча за поріг,
 *       інакше 0. Так «родючі землі» падають у найродючіші провінції.
 * </ul>
 *
 * @param terrains придатність за типом місцевості, {@code 0..}{@value #MAX_TERRAIN}; порожня — лише разом з {@code
 *     fertilityFrom}
 * @param climates множник поясу у відсотках, {@code 0..}{@value #MAX_CLIMATE_PCT}; лише разом з {@code terrains}
 * @param fertilityFrom поріг родючості {@code 0..}{@value FertilityDef#MAX_VALUE}; виключає {@code terrains} і
 *     {@code climates}
 */
public record DepositDef(
        SortedMap<Terrain, Integer> terrains, SortedMap<Climate, Integer> climates, OptionalInt fertilityFrom) {

    /** Придатність за місцевістю — у тій самій шкалі, що й родючість: 100 — найкраща провінція. */
    public static final int MAX_TERRAIN = 100;

    /** Пояс може щонайбільше потроїти придатність (нафта пустель), інакше затьмарив би місцевість. */
    public static final int MAX_CLIMATE_PCT = 300;

    /** Найбільша придатність провінції. */
    public static final int MAX_SUITABILITY = MAX_TERRAIN * MAX_CLIMATE_PCT / 100;

    /**
     * @throws ValidationException якщо разом задано поріг родючості й таблиці місцевості чи поясів ({@link
     *     ErrorCode#CONFLICTING_FIELDS}), без порогу всі значення місцевості нульові ({@link
     *     ErrorCode#EMPTY_COLLECTION}) або число поза межами
     */
    public DepositDef {
        terrains = copy("deposits.terrains", terrains, MAX_TERRAIN, Terrain::key);
        climates = copy("deposits.climates", climates, MAX_CLIMATE_PCT, Climate::key);
        Objects.requireNonNull(fertilityFrom, "fertilityFrom");
        if (fertilityFrom.isPresent()) {
            Checks.inRange("deposits.fertility_from", fertilityFrom.getAsInt(), 0, FertilityDef.MAX_VALUE);
            if (!terrains.isEmpty() || !climates.isEmpty()) {
                throw new ValidationException(
                        ErrorCode.CONFLICTING_FIELDS,
                        ErrorDetails.of(
                                "field",
                                "deposits.fertility_from",
                                "value",
                                terrains.isEmpty() ? "deposits.climates" : "deposits.terrains"));
            }
        } else if (terrains.values().stream().allMatch(value -> value == 0)) {
            // Ресурс, якого ніде немає, не з'явився б на колесі: це помилка контенту, а не намір.
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", "deposits.terrains"));
        }
    }

    /** Придатність за місцевістю з множниками поясів. */
    public static DepositDef byTerrain(SortedMap<Terrain, Integer> terrains, SortedMap<Climate, Integer> climates) {
        return new DepositDef(terrains, climates, OptionalInt.empty());
    }

    /** Придатність за родючістю від порогу. */
    public static DepositDef byFertility(int fertilityFrom) {
        return new DepositDef(
                Collections.emptySortedMap(), Collections.emptySortedMap(), OptionalInt.of(fertilityFrom));
    }

    /**
     * Придатність провінції, {@code 0..}{@value #MAX_SUITABILITY}.
     *
     * @param fertility родючість провінції, {@code 0..}{@value FertilityDef#MAX_VALUE}
     */
    public int suitability(Climate climate, Terrain terrain, int fertility) {
        Objects.requireNonNull(climate, "climate");
        Objects.requireNonNull(terrain, "terrain");
        Checks.inRange("fertility", fertility, 0, FertilityDef.MAX_VALUE);
        if (fertilityFrom.isPresent()) {
            return fertility >= fertilityFrom.getAsInt() ? fertility : 0;
        }
        return terrains.getOrDefault(terrain, 0) * climates.getOrDefault(climate, 100) / 100;
    }

    private static <K extends Comparable<K>> SortedMap<K, Integer> copy(
            String field, SortedMap<K, Integer> values, int max, Function<K, String> key) {
        TreeMap<K, Integer> copy = new TreeMap<>();
        for (Map.Entry<K, Integer> entry : Objects.requireNonNull(values, field).entrySet()) {
            String where = field + "." + key.apply(Objects.requireNonNull(entry.getKey(), field));
            copy.put(entry.getKey(), Checks.inRange(where, Objects.requireNonNull(entry.getValue(), where), 0, max));
        }
        return Collections.unmodifiableSortedMap(copy);
    }
}
