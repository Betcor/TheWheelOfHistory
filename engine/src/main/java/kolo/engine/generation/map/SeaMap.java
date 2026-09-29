package kolo.engine.generation.map;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.SortedMap;
import java.util.TreeMap;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Водойми й морські зони карти — результат {@link SeaGenerator}.
 *
 * @param bodies водойми за зростанням найменшої комірки; номер у списку — номер водойми
 * @param cellBodies для кожної комірки сітки — номер її водойми або {@link #NONE} для суходолу
 * @param zones морські зони: моря в порядку {@code bodies}, усередині моря — у порядку зерен; номер у списку — номер
 *     зони
 * @param cellZones для кожної комірки сітки — номер її морської зони або {@link #NONE} для суходолу й озер
 * @param coasts вихід до моря: для кожної комірки суходолу, що межує з морем, — номери сусідніх морських зон за
 *     зростанням; лише такі комірки
 */
public record SeaMap(
        List<WaterBody> bodies,
        List<Integer> cellBodies,
        List<SeaZone> zones,
        List<Integer> cellZones,
        SortedMap<Integer, List<Integer>> coasts) {

    /** Комірка без водойми чи без морської зони. */
    public static final int NONE = -1;

    public SeaMap {
        bodies = List.copyOf(bodies);
        cellBodies = List.copyOf(cellBodies);
        zones = List.copyOf(zones);
        cellZones = List.copyOf(cellZones);
        TreeMap<Integer, List<Integer>> coastCopy = new TreeMap<>();
        coasts.forEach((cell, near) -> coastCopy.put(cell, List.copyOf(near)));
        coasts = Collections.unmodifiableSortedMap(coastCopy);
        if (cellZones.size() != cellBodies.size()) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "cell_zones", "value", cellZones.size()));
        }
        int water = 0;
        for (int b = 0; b < bodies.size(); b++) {
            for (int cell : bodies.get(b).cells()) {
                Checks.inRange("cell", cell, 0, cellBodies.size() - 1);
                if (cellBodies.get(cell) != b) {
                    throw new ValidationException(
                            ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "cell_bodies", "value", cell));
                }
            }
            water += bodies.get(b).cells().size();
        }
        long marked = cellBodies.stream().filter(body -> body != NONE).count();
        if (marked != water) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "cell_bodies", "value", marked));
        }
        int zoned = 0;
        for (int z = 0; z < zones.size(); z++) {
            SeaZone zone = zones.get(z);
            Checks.inRange("zone.body", zone.body(), 0, bodies.size() - 1);
            if (bodies.get(zone.body()).kind() != WaterKind.SEA) {
                throw new ValidationException(
                        ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "zone.body", "value", zone.body()));
            }
            for (int cell : zone.cells()) {
                Checks.inRange("cell", cell, 0, cellZones.size() - 1);
                if (cellZones.get(cell) != z || cellBodies.get(cell) != zone.body()) {
                    throw new ValidationException(
                            ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "cell_zones", "value", cell));
                }
            }
            for (int neighbor : zone.neighbors()) {
                Checks.inRange("zone.neighbor", neighbor, 0, zones.size() - 1);
                if (neighbor == z) {
                    throw new ValidationException(
                            ErrorCode.SELF_REFERENCE, ErrorDetails.of("field", "zone.neighbors", "value", z));
                }
            }
            zoned += zone.cells().size();
        }
        long seaCells = bodies.stream()
                .filter(body -> body.kind() == WaterKind.SEA)
                .mapToLong(body -> body.cells().size())
                .sum();
        long markedZones = cellZones.stream().filter(zone -> zone != NONE).count();
        if (zoned != seaCells || markedZones != seaCells) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "cell_zones", "value", markedZones));
        }
        for (Map.Entry<Integer, List<Integer>> entry : coasts.entrySet()) {
            Checks.inRange("coast", entry.getKey(), 0, cellBodies.size() - 1);
            if (cellBodies.get(entry.getKey()) != NONE || entry.getValue().isEmpty()) {
                throw new ValidationException(
                        ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "coasts", "value", entry.getKey()));
            }
            for (int zone : entry.getValue()) {
                Checks.inRange("coast.zone", zone, 0, zones.size() - 1);
            }
        }
    }

    /** Чи комірка — вода: море чи озеро. */
    public boolean isWater(int cell) {
        return cellBodies.get(cell) != NONE;
    }

    /** Чи комірка — море. */
    public boolean isSea(int cell) {
        return cellZones.get(cell) != NONE;
    }

    /** Чи комірка — озеро. */
    public boolean isLake(int cell) {
        return isWater(cell) && !isSea(cell);
    }

    /** Морська зона комірки; порожньо — суходіл чи озеро. */
    public OptionalInt zone(int cell) {
        int zone = cellZones.get(cell);
        return zone == NONE ? OptionalInt.empty() : OptionalInt.of(zone);
    }

    /** Чи комірка суходолу має вихід до моря. */
    public boolean coastal(int cell) {
        return coasts.containsKey(cell);
    }

    /** Морські зони, з якими межує комірка суходолу, за зростанням; порожньо — виходу до моря немає. */
    public List<Integer> seaZones(int cell) {
        return coasts.getOrDefault(cell, List.of());
    }

    /** Скільки водойм цього виду. */
    public int count(WaterKind kind) {
        return (int) bodies.stream().filter(body -> body.kind() == kind).count();
    }
}
