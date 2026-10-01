package kolo.server.persistence;

import java.time.Instant;
import java.util.Objects;
import kolo.engine.error.Checks;

/**
 * Гравець, прочитаний з файлу світу.
 *
 * @param lastSeenAt коли гравець востаннє був на зв'язку (створення світу або від'єднання); у хеш стану не входить
 * @param missedTurns скільки останніх років поспіль гравець не натиснув «Готово» й за нього діяв автопілот (GD §6.3)
 */
public record SavedPlayer(PlayerRecord player, Instant lastSeenAt, int missedTurns) {

    public SavedPlayer {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        Checks.inRange("missed_turns", missedTurns, 0, Integer.MAX_VALUE);
    }
}
