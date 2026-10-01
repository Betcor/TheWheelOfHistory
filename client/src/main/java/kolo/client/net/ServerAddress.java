package kolo.client.net;

import java.net.InetSocketAddress;
import kolo.protocol.Protocol;

/** Адреса сервера з поля введення: {@code вузол}, {@code вузол:порт} або {@code [IPv6]:порт}. */
public final class ServerAddress {

    private ServerAddress() {}

    /**
     * Адреса без розв'язання імені (DNS — уже під час з'єднання, не в потоці UI); без порту — {@link
     * Protocol#DEFAULT_PORT}.
     *
     * @throws IllegalArgumentException якщо адреса порожня, порт не число або поза 1..65535
     */
    public static InetSocketAddress parse(String text) {
        String trimmed = text == null ? "" : text.strip();
        String host = trimmed;
        String port = null;
        if (trimmed.startsWith("[")) {
            int end = trimmed.indexOf(']');
            if (end < 0) {
                throw new IllegalArgumentException("незакрита дужка: " + trimmed);
            }
            host = trimmed.substring(1, end);
            String rest = trimmed.substring(end + 1);
            if (!rest.isEmpty()) {
                if (!rest.startsWith(":")) {
                    throw new IllegalArgumentException("після адреси IPv6 — лише порт: " + trimmed);
                }
                port = rest.substring(1);
            }
        } else if (trimmed.indexOf(':') == trimmed.lastIndexOf(':') && trimmed.indexOf(':') >= 0) {
            // Одна двокрапка — вузол і порт; кілька без дужок — це IPv6 без порту.
            int colon = trimmed.indexOf(':');
            host = trimmed.substring(0, colon);
            port = trimmed.substring(colon + 1);
        }
        if (host.isBlank()) {
            throw new IllegalArgumentException("порожня адреса");
        }
        return InetSocketAddress.createUnresolved(host, port == null ? Protocol.DEFAULT_PORT : port(port));
    }

    private static int port(String text) {
        int port;
        try {
            port = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("порт — ціле число: " + text, e);
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("порт поза 1..65535: " + port);
        }
        return port;
    }
}
