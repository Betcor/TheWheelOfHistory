package kolo.protocol.message;

import kolo.engine.error.ErrorDetails;
import kolo.engine.error.VersionMismatchException;
import kolo.protocol.Protocol;

/**
 * Рукостискання: клієнт надсилає {@link ClientMessage.Hello}, сервер відповідає {@link ServerMessage.Welcome}. Обидві
 * сторони звіряють версію протоколу (спершу — без неї решті повідомлення не можна вірити) і хеш контенту: з різним
 * контентом колеса на клієнті показували б не ті шанси.
 */
public final class Handshake {

    /** Частина, що не збіглася: версія протоколу. */
    public static final String PROTOCOL = "protocol";

    /** Частина, що не збіглася: хеш контенту. */
    public static final String CONTENT = "content";

    private Handshake() {}

    /** Привітання клієнта з поточною версією протоколу. */
    public static ClientMessage.Hello hello(String contentHash) {
        return new ClientMessage.Hello(Protocol.VERSION, contentHash);
    }

    /**
     * Перевірка привітання на сервері.
     *
     * @return відповідь клієнтові, якщо все збіглося
     * @throws VersionMismatchException якщо версія протоколу чи хеш контенту клієнта інші; подробиці — {@code part},
     *     {@code client}, {@code server}
     */
    public static ServerMessage.Welcome accept(ClientMessage.Hello hello, String serverContentHash) {
        check(hello.protocolVersion(), hello.contentHash(), Protocol.VERSION, serverContentHash);
        return new ServerMessage.Welcome(Protocol.VERSION, serverContentHash);
    }

    /**
     * Перевірка відповіді сервера на клієнті: сервер іншої версії міг відповісти, не перевіривши привітання.
     *
     * @throws VersionMismatchException якщо версія протоколу чи хеш контенту сервера інші
     */
    public static void confirm(ServerMessage.Welcome welcome, String clientContentHash) {
        check(Protocol.VERSION, clientContentHash, welcome.protocolVersion(), welcome.contentHash());
    }

    private static void check(int clientVersion, String clientHash, int serverVersion, String serverHash) {
        if (clientVersion != serverVersion) {
            throw new VersionMismatchException(
                    ErrorDetails.of("part", PROTOCOL, "client", clientVersion, "server", serverVersion));
        }
        if (!clientHash.equals(serverHash)) {
            throw new VersionMismatchException(
                    ErrorDetails.of("part", CONTENT, "client", clientHash, "server", serverHash));
        }
    }
}
