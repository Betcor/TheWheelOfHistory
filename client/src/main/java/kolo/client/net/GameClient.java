package kolo.client.net;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import kolo.engine.state.NpcShare;
import kolo.protocol.Protocol;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.ServerMessage;
import kolo.server.EmbeddedServer;

/**
 * Гра клієнта: вбудований сервер (одиночна гра й LAN-хост) і поточне з'єднання — з ним або з віддаленим сервером.
 * Пам'ятає, хто гравець у поточній сесії (номер і токен), щоб повернутися після розриву.
 *
 * <p>Одночасно — одне з'єднання: нове закриває попереднє, і події закритого до слухача вже не доходять. Потокобезпечний;
 * методи не блокують: важке (завантаження контенту заради його хешу) — у фоновому виконавці, мережа — у потоці
 * з'єднання.
 */
public final class GameClient implements AutoCloseable {

    private final EmbeddedServer server;
    private final int lanPort;
    private final SessionListener listener;
    private final Executor background;
    private ServerConnection connection;
    private SocketAddress address;
    private CurrentListener current;
    private ServerMessage.Joined credentials;
    private InetSocketAddress lan;

    private GameClient(EmbeddedServer server, int lanPort, SessionListener listener, Executor background) {
        this.server = server;
        this.lanPort = lanPort;
        this.listener = Objects.requireNonNull(listener, "listener");
        this.background = Objects.requireNonNull(background, "background");
    }

    /** Вбудований сервер із типовою текою світів; LAN — на порту {@value Protocol#DEFAULT_PORT}. */
    public static GameClient start(SessionListener listener, Executor background) {
        return new GameClient(EmbeddedServer.startWithBundledContent(), Protocol.DEFAULT_PORT, listener, background);
    }

    /**
     * @param worlds тека файлів світів вбудованого сервера
     * @param lanPort порт гри для локальної мережі; 0 — будь-який вільний
     */
    public static GameClient start(Path worlds, int lanPort, SessionListener listener, Executor background) {
        return new GameClient(EmbeddedServer.startWithBundledContent(worlds), lanPort, listener, background);
    }

    /**
     * Створює лобі на вбудованому сервері.
     *
     * @param openLan відкрити гру й для локальної мережі; порт зайнятий — {@link LanUnavailableException}
     */
    public CompletableFuture<ServerMessage.Joined> hostLobby(
            String nickname, long seed, NpcShare npcShare, boolean openLan) {
        return CompletableFuture.supplyAsync(
                        () -> {
                            if (openLan) {
                                openLan();
                            }
                            return server.address();
                        },
                        background)
                .thenCompose(this::connection)
                .thenCompose(connection -> remember(connection.createLobby(nickname, seed, npcShare)));
    }

    /** З'єднується з сервером за адресою й повертає його відкриті лобі. */
    public CompletableFuture<List<LobbyInfo>> findLobbies(InetSocketAddress address) {
        return connection(address).thenCompose(ServerConnection::lobbies);
    }

    /** Приєднується до лобі на сервері поточного з'єднання ({@link #findLobbies}). */
    public CompletableFuture<ServerMessage.Joined> joinLobby(long session, String nickname) {
        ServerConnection open = open();
        if (open == null) {
            return CompletableFuture.failedFuture(new ConnectionClosedException("немає з'єднання з сервером"));
        }
        return remember(open.joinLobby(session, nickname));
    }

    /** Нове з'єднання з тим самим сервером і повернення до своєї держави з токеном. */
    public CompletableFuture<ServerMessage.Joined> reconnect() {
        ServerMessage.Joined joined;
        SocketAddress last;
        synchronized (this) {
            joined = credentials;
            last = address;
        }
        if (joined == null || last == null) {
            return CompletableFuture.failedFuture(new ConnectionClosedException("немає сесії для повернення"));
        }
        return reconnect(last).thenCompose(connection -> connection.rejoin(joined));
    }

    /** Хост починає гру. */
    public void startGame() {
        ServerConnection open = open();
        if (open != null) {
            open.startGame();
        }
    }

    /** «Готово» для року {@code turn}. */
    public void ready(int turn) {
        ServerConnection open = open();
        if (open != null) {
            open.ready(turn);
        }
    }

    /** Полишає сесію; повернутися до неї вже не можна. */
    public void leave() {
        ServerConnection open;
        synchronized (this) {
            credentials = null;
            open = connection;
        }
        if (open != null && open.isOpen()) {
            open.leave();
        }
    }

    /** Гравець поточної сесії. */
    public synchronized Optional<ServerMessage.Joined> credentials() {
        return Optional.ofNullable(credentials);
    }

    /** Чи це я — гравець поточної сесії. */
    public boolean isMe(PlayerInfo player) {
        return credentials().map(joined -> joined.player() == player.number()).orElse(false);
    }

    /** Адреса гри в локальній мережі, якщо її відкрито. */
    public synchronized Optional<InetSocketAddress> lanAddress() {
        return Optional.ofNullable(lan);
    }

    /** Закриває з'єднання й зупиняє вбудований сервер. */
    @Override
    public void close() {
        ServerConnection open;
        synchronized (this) {
            open = connection;
            connection = null;
            if (current != null) {
                current.active = false;
            }
        }
        if (open != null) {
            open.close();
        }
        server.close();
    }

    // ---- З'єднання ----

    private void openLan() {
        synchronized (this) {
            if (lan != null) {
                return;
            }
        }
        InetSocketAddress bound;
        try {
            bound = server.openLan(new InetSocketAddress(lanPort));
        } catch (Exception e) {
            // Netty кидає й перевірювані винятки без оголошення (BindException — порт зайнятий).
            throw new LanUnavailableException(lanPort, e);
        }
        synchronized (this) {
            lan = bound;
        }
    }

    /** Відкрите з'єднання з цією адресою — наявне або нове. */
    private CompletableFuture<ServerConnection> connection(SocketAddress target) {
        synchronized (this) {
            if (connection != null
                    && connection.isOpen()
                    && connection.address().equals(target)) {
                return CompletableFuture.completedFuture(connection);
            }
        }
        return reconnect(target);
    }

    /** Нове з'єднання з адресою; попереднє закривається, і його події більше не доходять до слухача. */
    private CompletableFuture<ServerConnection> reconnect(SocketAddress target) {
        CurrentListener next = new CurrentListener(listener);
        ServerConnection previous;
        synchronized (this) {
            previous = connection;
            connection = null;
            address = target;
            if (current != null) {
                current.active = false;
            }
            current = next;
        }
        if (previous != null) {
            previous.close();
        }
        return CompletableFuture.supplyAsync(server::contentHash, background)
                .thenCompose(hash -> ServerConnection.connect(target, hash, next))
                .thenApply(opened -> {
                    synchronized (this) {
                        if (current == next) {
                            connection = opened;
                            return opened;
                        }
                    }
                    // Тим часом почалося інше з'єднання — це вже нікому не потрібне.
                    opened.close();
                    throw new ConnectionClosedException("з'єднання замінено новим");
                });
    }

    private CompletableFuture<ServerMessage.Joined> remember(CompletableFuture<ServerMessage.Joined> joining) {
        return joining.thenApply(joined -> {
            synchronized (this) {
                credentials = joined;
            }
            return joined;
        });
    }

    private synchronized ServerConnection open() {
        return connection != null && connection.isOpen() ? connection : null;
    }

    /** Слухач одного з'єднання: передає події, доки це з'єднання поточне. */
    private static final class CurrentListener implements SessionListener {
        private final SessionListener target;
        private volatile boolean active = true;

        CurrentListener(SessionListener target) {
            this.target = target;
        }

        @Override
        public void lobby(ServerMessage.Lobby lobby) {
            if (active) {
                target.lobby(lobby);
            }
        }

        @Override
        public void gameStarted(GameStart start) {
            if (active) {
                target.gameStarted(start);
            }
        }

        @Override
        public void players(List<PlayerInfo> players) {
            if (active) {
                target.players(players);
            }
        }

        @Override
        public void phase(ServerMessage.Phase phase) {
            if (active) {
                target.phase(phase);
            }
        }

        @Override
        public void error(ServerMessage.Error error) {
            if (active) {
                target.error(error);
            }
        }

        @Override
        public void disconnected() {
            if (active) {
                target.disconnected();
            }
        }
    }
}
