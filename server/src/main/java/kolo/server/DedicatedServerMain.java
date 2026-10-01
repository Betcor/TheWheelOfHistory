package kolo.server;

import java.net.InetSocketAddress;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.error.ContentException;
import kolo.protocol.Protocol;
import kolo.server.transport.GameServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Точка входу окремого (headless) сервера: {@code [--port N]}, типово {@value Protocol#DEFAULT_PORT}.
 *
 * <p>Контент завантажується одразу: з невалідним контентом сервер не стартує. Зупинка — сигналом процесу.
 */
public final class DedicatedServerMain {
    private static final Logger LOG = LoggerFactory.getLogger(DedicatedServerMain.class);

    /** Код виходу: невірні аргументи. */
    static final int EXIT_USAGE = 2;

    /** Код виходу: сервер не стартував. */
    static final int EXIT_FAILURE = 1;

    private DedicatedServerMain() {}

    public static void main(String[] args) throws InterruptedException {
        int port;
        try {
            port = port(args);
        } catch (IllegalArgumentException e) {
            LOG.error("Невірні аргументи: {}. Використання: [--port 0..65535]", e.getMessage());
            System.exit(EXIT_USAGE);
            return;
        }
        ContentPack content;
        try {
            content = ContentLoader.loadBundled();
        } catch (ContentException e) {
            LOG.error("Контент невалідний: {} {}", e.code().key(), e.details(), e);
            System.exit(EXIT_FAILURE);
            return;
        }
        GameServer server = start(content, port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "kolo-shutdown"));
        server.awaitClose();
    }

    /**
     * Сервер на TCP-порту з усіх адрес.
     *
     * @param port порт; 0 — будь-який вільний
     */
    static GameServer start(ContentPack content, int port) {
        GameServer server = GameServer.start(() -> content);
        try {
            InetSocketAddress address = server.bindTcp(new InetSocketAddress(port));
            LOG.info("Сервер слухає {}, контент {}", address, content.hash());
            return server;
        } catch (RuntimeException e) {
            server.close();
            throw e;
        }
    }

    /**
     * Порт з аргументів.
     *
     * @throws IllegalArgumentException якщо аргументи невірні
     */
    static int port(String... args) {
        int port = Protocol.DEFAULT_PORT;
        for (int i = 0; i < args.length; i++) {
            if (!args[i].equals("--port")) {
                throw new IllegalArgumentException("невідомий аргумент " + args[i]);
            }
            if (i + 1 == args.length) {
                throw new IllegalArgumentException("бракує номера порту");
            }
            try {
                port = Integer.parseInt(args[++i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("порт — ціле число: " + args[i], e);
            }
            if (port < 0 || port > 65_535) {
                throw new IllegalArgumentException("порт поза 0..65535: " + port);
            }
        }
        return port;
    }
}
