package kolo.client.net;

import java.util.Objects;
import kolo.engine.view.MapView;
import kolo.protocol.message.YearPhase;

/**
 * Світ, у який клієнт щойно увійшов: карта, поточний рік і його фаза.
 *
 * @param map карта світу
 * @param turn поточний рік (хід, не календарний рік)
 * @param phase фаза року; на початку гри — прийом наказів
 */
public record GameStart(MapView map, int turn, YearPhase phase) {

    public GameStart {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(phase, "phase");
    }
}
