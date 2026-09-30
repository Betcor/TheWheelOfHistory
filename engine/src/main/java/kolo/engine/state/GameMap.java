package kolo.engine.state;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Карта світу: плаский прямокутник з комірок і морських зон. Після генерації не змінюється, тож клієнт отримує її
 * один раз, а стан при копіюванні ділить її між копіями.
 *
 * @param width ширина карти
 * @param height висота карти
 * @param tiles комірки за номером; номер суходолу — номер провінції ({@link ProvinceId#number()})
 * @param seaZones морські зони за номером ({@link SeaZoneId#number()})
 */
public record GameMap(int width, int height, List<MapTile> tiles, List<SeaZoneState> seaZones) {

    public GameMap {
        Checks.inRange("width", width, 1, Integer.MAX_VALUE);
        Checks.inRange("height", height, 1, Integer.MAX_VALUE);
        tiles = List.copyOf(tiles);
        seaZones = List.copyOf(seaZones);
        Checks.inRange("tiles", tiles.size(), 1, Integer.MAX_VALUE);
        for (int n = 0; n < seaZones.size(); n++) {
            if (seaZones.get(n).id().number() != n) {
                throw new ValidationException(
                        ErrorCode.VALUE_OUT_OF_RANGE,
                        ErrorDetails.of(
                                "field",
                                "sea_zones",
                                "value",
                                seaZones.get(n).id().value(),
                                "expected",
                                n));
            }
        }
    }

    /** Комірка з номером {@code cell}. */
    public MapTile tile(int cell) {
        Checks.inRange("cell", cell, 0, tiles.size() - 1);
        return tiles.get(cell);
    }

    /** Комірка провінції. */
    public MapTile tile(ProvinceId province) {
        Objects.requireNonNull(province, "province");
        Checks.inRange("province", province.number(), 0, tiles.size() - 1L);
        return tiles.get((int) province.number());
    }

    /** Морська зона. */
    public SeaZoneState seaZone(SeaZoneId zone) {
        Objects.requireNonNull(zone, "zone");
        Checks.inRange("sea_zone", zone.number(), 0, seaZones.size() - 1L);
        return seaZones.get((int) zone.number());
    }
}
