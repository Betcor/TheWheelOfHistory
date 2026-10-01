package kolo.protocol.message;

import java.util.Objects;
import kolo.engine.error.Checks;

/**
 * Відкрите лобі в списку сервера ({@link ServerMessage.Lobbies}): до нього можна приєднатися ({@link
 * ClientMessage.JoinLobby}) або, з токеном цього світу, повернутися на своє місце ({@link ClientMessage.Rejoin}).
 *
 * @param session номер сесії на сервері
 * @param world ключ світу ({@link WorldKeys}): за ним клієнт шукає свій токен
 * @param host нікнейм хоста
 * @param players скільки гравців уже в лобі (на зв'язку)
 * @param setup новий світ чи завантажений
 */
public record LobbyInfo(long session, String world, String host, int players, LobbySetup setup) {

    public LobbyInfo {
        Checks.inRange("session", session, 1, Long.MAX_VALUE);
        WorldKeys.check("world", world);
        Nicknames.check("host", host);
        Checks.inRange("players", players, 1, Integer.MAX_VALUE);
        Objects.requireNonNull(setup, "setup");
    }
}
