package kolo.engine.generation.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.wheel.RollRecord;

/**
 * Карта світу з розміщеними державами — результат {@link MapGenerator}: усе, що колеса генерації держави беруть з
 * карти.
 *
 * @param size розмір світу
 * @param grid сітка комірок
 * @param continents материки
 * @param relief висота й рельєф суходолу
 * @param climate клімат і місцевість суходолу
 * @param sea водойми й морські зони
 * @param rivers річки
 * @param fertility родючість суходолу
 * @param suitability придатність суходолу до родовищ
 * @param placement держави на карті — материк, площа й територія кожної
 */
public record WorldMap(
        WorldSize size,
        MapGrid grid,
        ContinentMap continents,
        ReliefMap relief,
        ClimateMap climate,
        SeaMap sea,
        RiverMap rivers,
        FertilityMap fertility,
        ResourceSuitabilityMap suitability,
        PlacementMap placement) {

    public WorldMap {
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(continents, "continents");
        Objects.requireNonNull(relief, "relief");
        Objects.requireNonNull(climate, "climate");
        Objects.requireNonNull(sea, "sea");
        Objects.requireNonNull(rivers, "rivers");
        Objects.requireNonNull(fertility, "fertility");
        Objects.requireNonNull(suitability, "suitability");
        Objects.requireNonNull(placement, "placement");
        if (placement.cellCountries().size() != grid.cells().size()) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE,
                    ErrorDetails.of(
                            "field",
                            "placement",
                            "value",
                            placement.cellCountries().size()));
        }
    }

    /** Скільки держав розміщено на карті. */
    public int countries() {
        return placement.countries().size();
    }

    /** Територія держави з номером {@code country}. */
    public PlacedCountry country(int country) {
        check(country);
        return placement.countries().get(country);
    }

    /**
     * Держави, чия територія межує з територією {@code country} по суходолу, — номери за зростанням. Сусідів через
     * море чи нічийну землю немає: передісторія пов'язує держави, що справді ділять кордон.
     */
    public SortedSet<Integer> neighbors(int country) {
        TreeSet<Integer> neighbors = new TreeSet<>();
        for (int cell : country(country).cells()) {
            for (int neighbor : grid.cells().get(cell).neighbors()) {
                int owner = placement.country(neighbor);
                if (owner != PlacementMap.NONE && owner != country) {
                    neighbors.add(owner);
                }
            }
        }
        return Collections.unmodifiableSortedSet(neighbors);
    }

    /**
     * Обертання коліс світу в порядку кидків: розмір світу, розміри материків, хребти, клімат світу. Колеса материка
     * й площі кожної держави — у {@link PlacedCountry#rolls()}.
     */
    public List<RollRecord> rolls() {
        List<RollRecord> rolls = new ArrayList<>(size.rolls());
        rolls.addAll(continents.rolls());
        rolls.addAll(relief.rolls());
        rolls.add(climate.roll());
        return List.copyOf(rolls);
    }

    private void check(int country) {
        Checks.inRange("country", country, 0, placement.countries().size() - 1);
    }
}
