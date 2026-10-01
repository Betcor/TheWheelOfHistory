package kolo.server.persistence;

import java.time.Instant;
import java.util.Objects;

/**
 * Гравець, прочитаний з файлу світу.
 *
 * @param lastSeenAt коли гравець востаннє був на зв'язку (створення світу або від'єднання); у хеш стану не входить
 */
public record SavedPlayer(PlayerRecord player, Instant lastSeenAt) {

    public SavedPlayer {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
    }
}
