package kolo.server;

import io.netty.channel.local.LocalAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.server.persistence.WorldDirectory;
import kolo.server.session.LazyContent;
import kolo.server.transport.GameServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Вбудований сервер для одиночної гри, hot-seat і LAN-хоста: той самий {@link GameServer}, що й окремий, але
 * в процесі клієнта й на адресі {@code LocalChannel}. Клієнт говорить із ним лише повідомленнями протоколу через
 * {@link #address()} — так само, як з віддаленим сервером.
 *
 * <p>Контент завантажується при першому зверненні, а не при старті: старт клієнта до меню його не чекає. Файли світів
 * — у {@link WorldDirectory#defaultLocation()}.
 */
public final class EmbeddedServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedServer.class);

    private final Supplier<ContentPack> content;
    private final GameServer server;
    private final LocalAddress address;
    private InetSocketAddress discovery;

    private EmbeddedServer(Supplier<ContentPack> content, Path worlds) {
        this.content = new LazyContent(content);
        this.server = GameServer.start(this.content, new WorldDirectory(worlds));
        this.address = server.bindLocal();
    }

    /**
     * Домашня тека гри ({@link WorldDirectory#home()}): у ній тека світів вбудованого сервера ({@code worlds}) і файли
     * клієнта.
     */
    public static Path defaultHome() {
        return WorldDirectory.home();
    }

    /** Запускає сервер із вбудованим контентом гри й типовою текою світів. */
    public static EmbeddedServer startWithBundledContent() {
        return startWithBundledContent(WorldDirectory.defaultLocation());
    }

    /**
     * Запускає сервер із вбудованим контентом гри.
     *
     * @param worlds тека файлів світів; створюється при першому світі
     */
    public static EmbeddedServer startWithBundledContent(Path worlds) {
        return new EmbeddedServer(ContentLoader::loadBundled, worlds);
    }

    /** Адреса для з'єднання клієнта ({@code LocalChannel}). */
    public SocketAddress address() {
        return address;
    }

    /**
     * Відкриває гру для локальної мережі (LAN-хост): сервер слухає ще й TCP і відповідає на UDP-пошук ({@link
     * #discoveryAddress()}). Порт пошуку недоступний (зайнятий іншою грою на цьому комп'ютері) — не помилка: гра
     * відкрита, до неї підключаються за адресою. Повторний виклик відкриває ще одну адресу.
     *
     * @param address адреса й порт гри; порт 0 — будь-який вільний
     * @param discoveryPort UDP-порт пошуку ({@link kolo.protocol.Protocol#DISCOVERY_PORT}); 0 — будь-який вільний
     * @return справжня адреса гри, зокрема обраний порт
     * @throws Exception (Netty кидає без оголошення, зокрема {@link java.net.BindException}) якщо порт гри зайнятий чи
     *     недоступний
     */
    public InetSocketAddress openLan(InetSocketAddress address, int discoveryPort) {
        InetSocketAddress game = server.bindTcp(address);
        try {
            InetSocketAddress bound = server.bindDiscovery(new InetSocketAddress(discoveryPort), game.getPort());
            synchronized (this) {
                if (discovery == null) {
                    discovery = bound;
                }
            }
        } catch (Exception e) {
            // Netty кидає й перевірювані винятки без оголошення (BindException — порт зайнятий).
            LOG.warn("Пошук у локальній мережі недоступний: UDP-порт {} — {}", discoveryPort, e.toString());
        }
        return game;
    }

    /** UDP-адреса відповідей на пошук у локальній мережі; порожньо, якщо гру не відкрито для мережі чи порт недоступний. */
    public synchronized Optional<InetSocketAddress> discoveryAddress() {
        return Optional.ofNullable(discovery);
    }

    /**
     * Хеш контенту сервера — клієнт одиночної гри вітається з ним, бо грає тим самим контентом. Перше звернення
     * завантажує контент: викликати не з потоку UI.
     *
     * @throws kolo.engine.error.ContentException якщо вбудований контент невалідний
     */
    public String contentHash() {
        return content.get().hash();
    }

    /**
     * Контент сервера — той самий, з яким клієнт вітається ({@link #contentHash()}): клієнт підписує ним id з
     * повідомлень (лад, родовища, сектори коліс). Перше звернення завантажує контент: викликати не з потоку UI.
     *
     * @throws kolo.engine.error.ContentException якщо вбудований контент невалідний
     */
    public ContentPack content() {
        return content.get();
    }

    /** Зупиняє сервер і закриває всі з'єднання. */
    @Override
    public void close() {
        server.close();
    }
}
