package kolo.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import kolo.client.net.GameStart;
import kolo.client.net.RecordingListener;
import kolo.client.net.ServerConnection;
import kolo.client.net.SessionListener;
import kolo.engine.state.NpcShare;
import kolo.engine.view.MapView;
import kolo.server.EmbeddedServer;

/**
 * Світи з вбудованого сервера на вбудованому контенту — спільні для тестів клієнта, бо генерація недешева. Карти
 * приходять так само, як у грі: хост створює лобі, решта гравців приєднуються, хост починає гру — усе повідомленнями
 * через {@code LocalChannel}. Файли світів — у домашній теці тестів ({@code kolo.home} від збірки).
 */
public final class TestWorlds {

    public static final EmbeddedServer SERVER = EmbeddedServer.startWithBundledContent();

    /** Світ за замовчуванням екрана нового світу: 1 гравець, звичайна частка NPC. */
    public static final MapView DEFAULT = world(1970, 1, NpcShare.NORMAL);

    private TestWorlds() {}

    /** Найбільший світ: 16 гравців і багато NPC. */
    public static MapView largest(long seed) {
        return world(seed, 16, NpcShare.MANY);
    }

    /** Світ із {@code players} гравцями, як його отримує хост. */
    public static MapView world(long seed, int players, NpcShare share) {
        List<ServerConnection> connections = new ArrayList<>();
        try {
            RecordingListener host = new RecordingListener();
            ServerConnection hostConnection = connect(host);
            connections.add(hostConnection);
            long session =
                    await(hostConnection.createLobby("Хост", seed, share)).session();
            for (int n = 1; n < players; n++) {
                ServerConnection guest = connect(SessionListener.NONE);
                connections.add(guest);
                await(guest.joinLobby(session, "Гравець " + n));
            }
            hostConnection.startGame();
            return host.next(RecordingListener.Started.class).start().map();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            connections.forEach(ServerConnection::close);
        }
    }

    /** З'єднання з вбудованим сервером тестів. */
    public static ServerConnection connect(SessionListener listener) {
        return await(ServerConnection.connect(SERVER.address(), SERVER.contentHash(), listener));
    }

    /** Чекає результат і кидає його справжню причину, а не обгортку {@link CompletionException}. */
    public static <T> T await(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    /** Старт гри, що прийшов слухачеві. */
    public static GameStart started(RecordingListener listener) throws InterruptedException {
        return listener.next(RecordingListener.Started.class).start();
    }
}
