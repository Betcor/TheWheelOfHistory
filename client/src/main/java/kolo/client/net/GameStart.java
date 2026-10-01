package kolo.client.net;

import java.util.Objects;
import kolo.engine.view.MapView;

/**
 * Світ, у який клієнт щойно увійшов: карта й рік, накази якого сервер уже приймає.
 *
 * @param map карта світу
 * @param turn поточний рік (хід, не календарний рік)
 */
public record GameStart(MapView map, int turn) {

    public GameStart {
        Objects.requireNonNull(map, "map");
    }
}
