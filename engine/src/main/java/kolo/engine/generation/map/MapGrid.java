package kolo.engine.generation.map;

import java.util.List;
import kolo.engine.error.Checks;

/**
 * Сітка комірок Вороного, що вкриває прямокутник карти {@code [0, width] × [0, height]} без щілин і накладань.
 *
 * @param cells комірки за зростанням центрів ({@link GridPoint#compareTo}); номер у списку — номер комірки
 */
public record MapGrid(int width, int height, List<MapCell> cells) {

    public MapGrid {
        Checks.inRange("width", width, 1, Integer.MAX_VALUE);
        Checks.inRange("height", height, 1, Integer.MAX_VALUE);
        cells = List.copyOf(cells);
        Checks.inRange("cells", cells.size(), 1, Integer.MAX_VALUE);
        for (MapCell cell : cells) {
            for (int neighbor : cell.neighbors()) {
                Checks.inRange("neighbor", neighbor, 0, cells.size() - 1);
            }
        }
    }
}
