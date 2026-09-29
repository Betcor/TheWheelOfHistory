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
 * Стік води й річки суходолу — результат {@link RiverGenerator}. Усі мапи з номерами комірок суходолу як ключами.
 *
 * @param downstream куди стікає вода з комірки: сусідня комірка суходолу або води; ланцюжок завжди доходить до води
 * @param flows стік комірки — сума вологи її й усіх комірок, що стікають через неї; ті самі комірки
 * @param rivers річкові системи за зростанням гирла; номер у списку — номер річки
 * @param cellRivers річкова комірка → номер її річки; лише комірки з річкою
 */
public record RiverMap(
        SortedMap<Integer, Integer> downstream,
        SortedMap<Integer, Integer> flows,
        List<River> rivers,
        SortedMap<Integer, Integer> cellRivers) {

    public RiverMap {
        downstream = Collections.unmodifiableSortedMap(new TreeMap<>(downstream));
        flows = Collections.unmodifiableSortedMap(new TreeMap<>(flows));
        rivers = List.copyOf(rivers);
        cellRivers = Collections.unmodifiableSortedMap(new TreeMap<>(cellRivers));
        if (!downstream.keySet().equals(flows.keySet())) {
            throw new ValidationException(ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "flows"));
        }
        for (Map.Entry<Integer, Integer> entry : downstream.entrySet()) {
            Checks.inRange("cell", entry.getKey(), 0, Integer.MAX_VALUE);
            Checks.inRange("downstream", entry.getValue(), 0, Integer.MAX_VALUE);
            if (entry.getKey().equals(entry.getValue())) {
                throw new ValidationException(
                        ErrorCode.SELF_REFERENCE, ErrorDetails.of("field", "downstream", "value", entry.getKey()));
            }
        }
        for (int flow : flows.values()) {
            Checks.inRange("flow", flow, 0, Integer.MAX_VALUE);
        }
        int riverCells = 0;
        for (int r = 0; r < rivers.size(); r++) {
            River river = rivers.get(r);
            if (r > 0 && river.mouth() <= rivers.get(r - 1).mouth()) {
                throw new ValidationException(
                        ErrorCode.OUT_OF_ORDER, ErrorDetails.of("field", "rivers", "value", river.mouth()));
            }
            if (!Integer.valueOf(river.outlet()).equals(downstream.get(river.mouth()))
                    || downstream.containsKey(river.outlet())) {
                throw new ValidationException(
                        ErrorCode.VALUE_OUT_OF_RANGE,
                        ErrorDetails.of("field", "river.outlet", "value", river.outlet()));
            }
            for (int cell : river.cells()) {
                if (!Integer.valueOf(r).equals(cellRivers.get(cell))) {
                    throw new ValidationException(
                            ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "cell_rivers", "value", cell));
                }
                // Річка тече донизу своєю ж системою аж до гирла.
                if (cell != river.mouth() && !Integer.valueOf(r).equals(cellRivers.get(downstream.get(cell)))) {
                    throw new ValidationException(
                            ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "river.cells", "value", cell));
                }
            }
            riverCells += river.cells().size();
        }
        if (cellRivers.size() != riverCells) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "cell_rivers", "value", cellRivers.size()));
        }
    }

    /** Чи комірка суходолу має річку. */
    public boolean hasRiver(int cell) {
        return cellRivers.containsKey(cell);
    }

    /** Номер річки комірки; порожньо — річки немає або це вода. */
    public OptionalInt river(int cell) {
        Integer river = cellRivers.get(cell);
        return river == null ? OptionalInt.empty() : OptionalInt.of(river);
    }

    /** Куди стікає вода з комірки суходолу; порожньо — це вода. */
    public OptionalInt downstream(int cell) {
        Integer next = downstream.get(cell);
        return next == null ? OptionalInt.empty() : OptionalInt.of(next);
    }
}
