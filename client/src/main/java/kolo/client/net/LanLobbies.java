package kolo.client.net;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;

/**
 * Що знайшов пошук у локальній мережі.
 *
 * @param lobbies відкриті лобі сумісних ігор
 * @param incompatible скільки ігор іншої версії (протоколу чи контенту): до них не приєднатися, але гравець має знати,
 *     що гру знайдено
 */
public record LanLobbies(List<RemoteLobby> lobbies, int incompatible) {

    public LanLobbies {
        lobbies = List.copyOf(Objects.requireNonNull(lobbies, "lobbies"));
        Checks.inRange("incompatible", incompatible, 0, Integer.MAX_VALUE);
    }
}
