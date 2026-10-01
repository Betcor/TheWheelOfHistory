package kolo.client.net;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * Гра, що відповіла на пошук у локальній мережі.
 *
 * @param server TCP-адреса гри
 * @param version версія протоколу гри
 */
public record LanHost(InetSocketAddress server, int version) {

    public LanHost {
        Objects.requireNonNull(server, "server");
    }
}
