package kolo.engine.generation.map;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import kolo.engine.content.ClimateDef;
import kolo.engine.content.WorldClimateId;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Climate;
import kolo.engine.state.Cover;
import kolo.engine.state.Terrain;
import kolo.engine.wheel.RollRecord;

/**
 * Клімат суходолу — результат {@link ClimateGenerator}. Море клімату не має. Усі мапи з номерами комірок суходолу як
 * ключами; {@code covers} — лише комірки з покривом.
 *
 * @param world клімат світу з колеса
 * @param temperatures температура {@code 0..}{@value ClimateDef#MAX_VALUE}
 * @param moistures волога {@code 0..}{@value ClimateDef#MAX_VALUE}; ті самі комірки
 * @param climates кліматичний пояс; ті самі комірки
 * @param covers покрив — підмножина тих самих комірок
 * @param terrains тип місцевості: покрив або рельєф; ті самі комірки
 * @param roll обертання колеса клімату світу
 */
public record ClimateMap(
        WorldClimateId world,
        SortedMap<Integer, Integer> temperatures,
        SortedMap<Integer, Integer> moistures,
        SortedMap<Integer, Climate> climates,
        SortedMap<Integer, Cover> covers,
        SortedMap<Integer, Terrain> terrains,
        RollRecord roll) {

    public ClimateMap {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(roll, "roll");
        temperatures = copy(temperatures);
        moistures = copy(moistures);
        climates = copy(climates);
        covers = copy(covers);
        terrains = copy(terrains);
        for (Map.Entry<Integer, Integer> entry : temperatures.entrySet()) {
            Checks.inRange("cell", entry.getKey(), 0, Integer.MAX_VALUE);
            Checks.inRange("temperature", entry.getValue(), 0, ClimateDef.MAX_VALUE);
        }
        for (int moisture : moistures.values()) {
            Checks.inRange("moisture", moisture, 0, ClimateDef.MAX_VALUE);
        }
        sameCells("moistures", temperatures, moistures);
        sameCells("climates", temperatures, climates);
        sameCells("terrains", temperatures, terrains);
        if (!temperatures.keySet().containsAll(covers.keySet())) {
            throw new ValidationException(ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "covers"));
        }
        for (Map.Entry<Integer, Cover> entry : covers.entrySet()) {
            if (terrains.get(entry.getKey()) != entry.getValue().terrain()) {
                throw new ValidationException(
                        ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "terrains", "value", entry.getKey()));
            }
        }
    }

    /** Пояс комірки; порожньо — море. */
    public Optional<Climate> climate(int cell) {
        return Optional.ofNullable(climates.get(cell));
    }

    /** Покрив комірки; порожньо — море або суходіл без покриву. */
    public Optional<Cover> cover(int cell) {
        return Optional.ofNullable(covers.get(cell));
    }

    /** Тип місцевості комірки; порожньо — море. */
    public Optional<Terrain> terrain(int cell) {
        return Optional.ofNullable(terrains.get(cell));
    }

    /** Скільки комірок суходолу в поясі {@code climate}. */
    public int count(Climate climate) {
        return (int)
                climates.values().stream().filter(value -> value == climate).count();
    }

    /** Скільки комірок суходолу має тип місцевості {@code terrain}. */
    public int count(Terrain terrain) {
        return (int)
                terrains.values().stream().filter(value -> value == terrain).count();
    }

    private static <V> SortedMap<Integer, V> copy(SortedMap<Integer, V> map) {
        return Collections.unmodifiableSortedMap(new TreeMap<>(map));
    }

    private static void sameCells(String field, Map<Integer, ?> expected, Map<Integer, ?> actual) {
        if (!expected.keySet().equals(actual.keySet())) {
            throw new ValidationException(ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", field));
        }
    }
}
