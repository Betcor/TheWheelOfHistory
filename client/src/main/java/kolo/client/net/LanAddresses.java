package kolo.client.net;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Optional;

/**
 * Адреси гри, відкритої для локальної мережі.
 *
 * @param game TCP-адреса гри
 * @param discovery UDP-адреса відповідей на пошук; порожньо, якщо порт пошуку недоступний — тоді до гри підключаються
 *     лише за адресою
 */
public record LanAddresses(InetSocketAddress game, Optional<InetSocketAddress> discovery) {

    public LanAddresses {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(discovery, "discovery");
    }
}
