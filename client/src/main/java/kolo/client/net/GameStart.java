package kolo.client.net;

import java.util.Objects;
import kolo.engine.view.CountryCard;
import kolo.engine.view.MapView;
import kolo.protocol.message.ServerMessage;

/**
 * Світ, у який клієнт щойно увійшов: карта, картка своєї держави й поточна фаза року.
 *
 * @param map карта світу
 * @param card картка держави гравця з записами коліс генерації
 * @param phase поточний рік і його фаза; у новому світі — генерація, на початку гри завантаженого — початок року, після повернення — будь-яка (з межею часу,
 *     якщо це фаза наказів із таймером)
 */
public record GameStart(MapView map, CountryCard card, ServerMessage.Phase phase) {

    public GameStart {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(card, "card");
        Objects.requireNonNull(phase, "phase");
    }

    /** Поточний рік (хід, не календарний рік). */
    public int turn() {
        return phase.turn();
    }
}
