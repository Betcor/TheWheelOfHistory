package kolo.engine.view;

import java.util.List;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Карта світу для гравця: геометрія комірок, місцевість і кордони. Карта незмінна після генерації, тож клієнт отримує
 * її один раз.
 *
 * @param seed seed світу — щоб гравець міг повторити карту
 * @param width ширина карти в одиницях сітки
 * @param height висота карти в одиницях сітки
 * @param cells комірки; номер у списку — номер комірки сітки
 * @param countries держави за номером
 */
public record MapView(long seed, int width, int height, List<CellView> cells, List<CountryView> countries) {

    public MapView {
        Checks.inRange("width", width, 1, Integer.MAX_VALUE);
        Checks.inRange("height", height, 1, Integer.MAX_VALUE);
        cells = List.copyOf(cells);
        countries = List.copyOf(countries);
        Checks.inRange("cells", cells.size(), 1, Integer.MAX_VALUE);
        for (int n = 0; n < countries.size(); n++) {
            if (countries.get(n).number() != n) {
                throw new ValidationException(
                        ErrorCode.OUT_OF_ORDER, ErrorDetails.of("field", "countries", "value", n));
            }
        }
        int cellCount = cells.size();
        int countryCount = countries.size();
        for (CellView cell : cells) {
            for (int neighbor : cell.neighbors()) {
                Checks.inRange("neighbor", neighbor, 0, cells.size() - 1);
            }
            cell.downstream().ifPresent(next -> Checks.inRange("downstream", next, 0, cellCount - 1));
            cell.country().ifPresent(owner -> Checks.inRange("country", owner, 0, countryCount - 1));
        }
    }

    /** Середня сторона комірки — масштаб, від якого клієнт рахує зум і розмір підписів; щонайменше 1. */
    public int cellSide() {
        return Math.max(1, (int) Math.sqrt((double) width * height / cells.size()));
    }
}
