package kolo.server;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.OptionalInt;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.error.ContentException;
import kolo.protocol.Protocol;
import kolo.server.persistence.WorldDirectory;
import kolo.server.transport.GameServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Точка входу окремого (headless) сервера: {@code [--port N] [--worlds DIR] [--no-discovery]}, типово порт {@value
 * Protocol#DEFAULT_PORT}, тека {@code worlds} у поточній теці й відповіді на пошук у локальній мережі (UDP-порт
 * {@value Protocol#DISCOVERY_PORT}); {@code --no-discovery} вимикає їх — сервер в інтернеті локальної мережі не має.
 *
 * <p>Контент завантажується одразу: з невалідним контентом сервер не стартує. Зупинка — сигналом процесу.
 */
public final class DedicatedServerMain {
    private static final Logger LOG = LoggerFactory.getLogger(DedicatedServerMain.class);

    /** Код виходу: невірні аргументи. */
    static final int EXIT_USAGE = 2;

    /** Код виходу: сервер не стартував. */
    static final int EXIT_FAILURE = 1;

    /** Тека світів, якщо її не задано. */
    static final Path DEFAULT_WORLDS = Path.of("worlds");

    private DedicatedServerMain() {}

    /**
     * Аргументи запуску.
     *
     * @param port TCP-порт; 0 — будь-який вільний
     * @param worlds тека файлів світів
     * @param discovery UDP-порт відповідей на пошук у локальній мережі (0 — будь-який вільний); порожньо — не
     *     відповідати
     */
    record Options(int port, Path worlds, OptionalInt discovery) {}

    public static void main(String[] args) throws InterruptedException {
        Options options;
        try {
            options = options(args);
        } catch (IllegalArgumentException e) {
            LOG.error(
                    "Невірні аргументи: {}. Використання: [--port 0..65535] [--worlds DIR] [--no-discovery]",
                    e.getMessage());
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
        GameServer server = start(content, options);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "kolo-shutdown"));
        server.awaitClose();
    }

    /** Сервер на TCP-порту з усіх адрес. */
    static GameServer start(ContentPack content, Options options) {
        GameServer server = GameServer.start(() -> content, new WorldDirectory(options.worlds()));
        try {
            InetSocketAddress address = server.bindTcp(new InetSocketAddress(options.port()));
            options.discovery().ifPresent(port -> discovery(server, port, address.getPort()));
            LOG.info(
                    "Сервер слухає {}, контент {}, світи у {}",
                    address,
                    content.hash(),
                    options.worlds().toAbsolutePath());
            return server;
        } catch (RuntimeException e) {
            server.close();
            throw e;
        }
    }

    /** Відповіді на пошук у локальній мережі; порт недоступний — сервер працює й без них. */
    private static void discovery(GameServer server, int port, int gamePort) {
        try {
            LOG.info("Пошук у локальній мережі: UDP {}", server.bindDiscovery(new InetSocketAddress(port), gamePort));
        } catch (Exception e) {
            // Netty кидає й перевірювані винятки без оголошення (BindException — порт зайнятий).
            LOG.warn("Пошук у локальній мережі недоступний: UDP-порт {} — {}", port, e.toString());
        }
    }

    /**
     * Аргументи запуску.
     *
     * @throws IllegalArgumentException якщо аргументи невірні
     */
    static Options options(String... args) {
        int port = Protocol.DEFAULT_PORT;
        Path worlds = DEFAULT_WORLDS;
        OptionalInt discovery = OptionalInt.of(Protocol.DISCOVERY_PORT);
        for (int i = 0; i < args.length; i++) {
            String name = args[i];
            if (name.equals("--no-discovery")) {
                discovery = OptionalInt.empty();
                continue;
            }
            if (!name.equals("--port") && !name.equals("--worlds")) {
                throw new IllegalArgumentException("невідомий аргумент " + name);
            }
            if (i + 1 == args.length) {
                throw new IllegalArgumentException("бракує значення " + name);
            }
            String value = args[++i];
            if (name.equals("--worlds")) {
                if (value.isBlank()) {
                    throw new IllegalArgumentException("порожня тека світів");
                }
                worlds = Path.of(value);
                continue;
            }
            try {
                port = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("порт — ціле число: " + value, e);
            }
            if (port < 0 || port > 65_535) {
                throw new IllegalArgumentException("порт поза 0..65535: " + port);
            }
        }
        return new Options(port, worlds, discovery);
    }
}
