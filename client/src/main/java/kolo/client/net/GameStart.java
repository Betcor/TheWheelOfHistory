package kolo.client.net;

import java.util.Objects;
import kolo.engine.view.MapView;
import kolo.protocol.message.ServerMessage;

/**
 * Світ, у який клієнт щойно увійшов: карта й поточна фаза року.
 *
 * @param map карта світу
 * @param phase поточний рік і його фаза; на початку гри — початок року, після повернення — будь-яка (з межею часу,
 *     якщо це фаза наказів із таймером)
 */
public record GameStart(MapView map, ServerMessage.Phase phase) {

    public GameStart {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(phase, "phase");
    }

    /** Поточний рік (хід, не календарний рік). */
    public int turn() {
        return phase.turn();
    }
}
