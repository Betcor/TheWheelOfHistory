package kolo.engine.generation.country;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.content.CoastLevelId;
import kolo.engine.content.FertilityDef;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Terrain;

/**
 * Географія держави (GD §4.1, № 3) — результат {@link Geography}: підсумок її території без колеса.
 *
 * @param provinces комірки суходолу держави за зростанням
 * @param coastal провінції з виходом до моря за зростанням — серед {@code provinces}
 * @param coast рівень виходу до моря з контенту
 * @param seaZones морські зони, з якими межує держава, за зростанням, без повторів
 * @param terrains скільки провінцій кожного типу місцевості; лише наявні типи, разом — усі провінції
 * @param dominant переважна місцевість — тип з найбільшою кількістю провінцій, при рівності — перший у {@link
 *     Terrain}
 * @param fertility середня родючість провінцій, вниз, {@code 0..}{@value FertilityDef#MAX_VALUE}
 * @param tags мітки рівня виходу до моря й правил переважної місцевості — вхід для наступних коліс
 */
public record StartGeography(
        List<Integer> provinces,
        List<Integer> coastal,
        CoastLevelId coast,
        List<Integer> seaZones,
        SortedMap<Terrain, Integer> terrains,
        Terrain dominant,
        int fertility,
        SortedSet<String> tags) {

    public StartGeography {
        provinces = sorted("provinces", provinces);
        Checks.inRange("provinces", provinces.size(), 1, Integer.MAX_VALUE);
        coastal = sorted("coastal", coastal);
        if (!new TreeSet<>(provinces).containsAll(coastal)) {
            throw new ValidationException(ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "coastal"));
        }
        Objects.requireNonNull(coast, "coast");
        seaZones = sorted("sea_zones", seaZones);
        terrains = Collections.unmodifiableSortedMap(new TreeMap<>(terrains));
        long total = 0;
        for (Map.Entry<Terrain, Integer> entry : terrains.entrySet()) {
            Checks.inRange("terrains." + entry.getKey().key(), entry.getValue(), 1, provinces.size());
            total += entry.getValue();
        }
        if (total != provinces.size()) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "terrains", "value", total));
        }
        Objects.requireNonNull(dominant, "dominant");
        if (!terrains.containsKey(dominant) || terrains.get(dominant) < Collections.max(terrains.values())) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "dominant", "value", dominant.key()));
        }
        Checks.inRange("fertility", fertility, 0, FertilityDef.MAX_VALUE);
        tags = Collections.unmodifiableSortedSet(new TreeSet<>(tags));
    }

    /** Частка прибережних провінцій у відсотках, вгору: будь-який берег — щонайменше 1%. */
    public int coastalPct() {
        return percentUp(coastal.size(), provinces.size());
    }

    /** Разом частка провінцій цих типів місцевості у відсотках, вгору. */
    public int terrainPct(Collection<Terrain> kinds) {
        int count = 0;
        for (Terrain kind : kinds) {
            count += terrains.getOrDefault(kind, 0);
        }
        return percentUp(count, provinces.size());
    }

    /** Чи держава має вихід до моря. */
    public boolean hasCoast() {
        return !coastal.isEmpty();
    }

    static int percentUp(int part, int whole) {
        return Math.toIntExact(-Math.floorDiv(-100L * part, whole));
    }

    private static List<Integer> sorted(String field, List<Integer> cells) {
        int previous = -1;
        for (int cell : cells) {
            if (cell <= previous) {
                throw new ValidationException(ErrorCode.OUT_OF_ORDER, ErrorDetails.of("field", field, "value", cell));
            }
            previous = cell;
        }
        return List.copyOf(cells);
    }
}
