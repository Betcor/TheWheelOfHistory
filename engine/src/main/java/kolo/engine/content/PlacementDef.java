package kolo.engine.content;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Числа розміщення держав на карті (GD §3.6, колеса 1–2 GD §4.1).
 *
 * @param areas рівні площі від найменшого до найбільшого — порядок секторів колеса площі
 * @param minProvinces скільки провінцій щонайменше має держава, {@code 1..}{@value #MAX_MIN_PROVINCES}; материк
 *     приймає нову державу, лише якщо на ньому вистачає землі на цей мінімум кожній
 * @param roughness порізаність кордонів, {@code 0..100} %: шум множить відстань від зерна держави на
 *     {@code 1 ± roughness}; 0 — держава росте колом
 * @param noiseCells розмір плям шуму кордонів у комірках, {@code 1..}{@value #MAX_NOISE_CELLS}
 */
public record PlacementDef(List<AreaLevelDef> areas, int minProvinces, int roughness, int noiseCells) {

    /** Держава понад сотню провінцій мінімуму не вміститься в найменший світ. */
    public static final int MAX_MIN_PROVINCES = 100;

    public static final int MAX_ROUGHNESS = 100;

    /** Як у материків: ширші плями на карті до 20 000 комірок уже не дають звивистих кордонів. */
    public static final int MAX_NOISE_CELLS = 100;

    /**
     * @throws ValidationException якщо рівнів немає ({@link ErrorCode#EMPTY_COLLECTION}), id повторюється
     *     ({@link ErrorCode#DUPLICATE_ID}) або рівні не за зростанням частки ({@link ErrorCode#OUT_OF_ORDER})
     */
    public PlacementDef {
        Objects.requireNonNull(areas, "placement.areas");
        if (areas.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", "placement.areas"));
        }
        TreeSet<AreaLevelId> seen = new TreeSet<>();
        AreaLevelDef previous = null;
        for (AreaLevelDef area : areas) {
            Objects.requireNonNull(area, "placement.area");
            Defs.unique("placement.areas.id", seen, area.id());
            if (previous != null) {
                AreaLevelDef.checkFollows(previous, area);
            }
            previous = area;
        }
        areas = List.copyOf(areas);
        Checks.inRange("placement.min_provinces", minProvinces, 1, MAX_MIN_PROVINCES);
        Checks.inRange("placement.roughness", roughness, 0, MAX_ROUGHNESS);
        Checks.inRange("placement.noise_cells", noiseCells, 1, MAX_NOISE_CELLS);
    }

    public Optional<AreaLevelDef> area(AreaLevelId id) {
        Objects.requireNonNull(id, "id");
        return areas.stream().filter(area -> area.id().equals(id)).findFirst();
    }

    /** Рівні площі за id. */
    public SortedMap<AreaLevelId, AreaLevelDef> areasById() {
        TreeMap<AreaLevelId, AreaLevelDef> byId = new TreeMap<>();
        areas.forEach(area -> byId.put(area.id(), area));
        return Collections.unmodifiableSortedMap(byId);
    }

    /** Мітки, які можуть дати рівні площі. */
    public TreeSet<String> producedTags() {
        TreeSet<String> tags = new TreeSet<>();
        areas.forEach(area -> tags.addAll(area.tags()));
        return tags;
    }
}
