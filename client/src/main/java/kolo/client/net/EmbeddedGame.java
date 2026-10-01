package kolo.client.net;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import kolo.engine.state.NpcShare;
import kolo.engine.view.MapView;
import kolo.server.EmbeddedServer;

/**
 * Гра з вбудованим сервером (одиночна, hot-seat): сервер у процесі клієнта і з'єднання з ним через {@code
 * LocalChannel}. З'єднання встановлюється при першому запиті й відновлюється, якщо закрилося. Потокобезпечний, але
 * блокує — лише з фонового потоку.
 */
public final class EmbeddedGame implements WorldSource, AutoCloseable {

    private final EmbeddedServer server;
    private ServerConnection connection;

    private EmbeddedGame(EmbeddedServer server) {
        this.server = server;
    }

    /** Запускає вбудований сервер із вбудованим контентом; контент завантажиться при першому запиті. */
    public static EmbeddedGame start() {
        return new EmbeddedGame(EmbeddedServer.startWithBundledContent());
    }

    @Override
    public synchronized MapView newWorld(long seed, int players, NpcShare npcShare) {
        return await(connection().createWorld(seed, players, npcShare));
    }

    /** Закриває з'єднання й зупиняє сервер. */
    @Override
    public synchronized void close() {
        if (connection != null) {
            connection.close();
        }
        server.close();
    }

    private ServerConnection connection() {
        if (connection == null || !connection.isOpen()) {
            connection = await(ServerConnection.connect(server.address(), server.contentHash()));
        }
        return connection;
    }

    /** Чекає результат і кидає його справжню причину, а не обгортку {@link CompletionException}. */
    static <T> T await(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }
}
