package kolo.client.net;

import java.net.InetSocketAddress;
import java.util.Objects;
import kolo.protocol.message.LobbyInfo;

/**
 * Відкрите лобі на сервері за адресою: зі списку сервера ({@link GameClient#findLobbies}) чи з пошуку в локальній
 * мережі ({@link GameClient#findLanLobbies}).
 *
 * @param server TCP-адреса сервера
 * @param lobby лобі зі списку сервера
 */
public record RemoteLobby(InetSocketAddress server, LobbyInfo lobby) {

    public RemoteLobby {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(lobby, "lobby");
    }
}
