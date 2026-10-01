package kolo.protocol.message;

import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.state.NpcShare;

/**
 * Відкрите лобі в списку сервера ({@link ServerMessage.Lobbies}): до нього можна приєднатися ({@link
 * ClientMessage.JoinLobby}).
 *
 * @param session номер сесії на сервері
 * @param host нікнейм хоста
 * @param players скільки гравців уже в лобі
 * @param npcShare частка NPC-держав, яку задав хост
 */
public record LobbyInfo(long session, String host, int players, NpcShare npcShare) {

    public LobbyInfo {
        Checks.inRange("session", session, 1, Long.MAX_VALUE);
        Nicknames.check("host", host);
        Checks.inRange("players", players, 1, Integer.MAX_VALUE);
        Objects.requireNonNull(npcShare, "npcShare");
    }
}
