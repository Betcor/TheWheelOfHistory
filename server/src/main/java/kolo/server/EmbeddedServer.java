package kolo.server;

import io.netty.channel.local.LocalAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.function.Supplier;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.server.persistence.WorldDirectory;
import kolo.server.session.LazyContent;
import kolo.server.transport.GameServer;

/**
 * Вбудований сервер для одиночної гри, hot-seat і LAN-хоста: той самий {@link GameServer}, що й окремий, але
 * в процесі клієнта й на адресі {@code LocalChannel}. Клієнт говорить із ним лише повідомленнями протоколу через
 * {@link #address()} — так само, як з віддаленим сервером.
 *
 * <p>Контент завантажується при першому зверненні, а не при старті: старт клієнта до меню його не чекає. Файли світів
 * — у {@link WorldDirectory#defaultLocation()}.
 */
public final class EmbeddedServer implements AutoCloseable {

    private final Supplier<ContentPack> content;
    private final GameServer server;
    private final LocalAddress address;

    private EmbeddedServer(Supplier<ContentPack> content, Path worlds) {
        this.content = new LazyContent(content);
        this.server = GameServer.start(this.content, new WorldDirectory(worlds));
        this.address = server.bindLocal();
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
     * Хеш контенту сервера — клієнт одиночної гри вітається з ним, бо грає тим самим контентом. Перше звернення
     * завантажує контент: викликати не з потоку UI.
     *
     * @throws kolo.engine.error.ContentException якщо вбудований контент невалідний
     */
    public String contentHash() {
        return content.get().hash();
    }

    /** Зупиняє сервер і закриває всі з'єднання. */
    @Override
    public void close() {
        server.close();
    }
}
