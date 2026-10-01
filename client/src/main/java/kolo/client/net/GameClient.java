package kolo.client.net;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import kolo.engine.error.ErrorCode;
import kolo.engine.state.NpcShare;
import kolo.engine.state.TurnTimer;
import kolo.protocol.Protocol;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.PlayerToken;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.WorldInfo;
import kolo.server.EmbeddedServer;

/**
 * Гра клієнта: вбудований сервер (одиночна гра й LAN-хост) і поточне з'єднання — з ним або з віддаленим сервером.
 * Пам'ятає, хто гравець у поточній сесії (номер і токен), щоб повернутися після розриву, і зберігає токени на диску
 * ({@link TokenStore}) за ключем світу: з ними гравець сідає на своє місце в завантаженому світі.
 *
 * <p>Одночасно — одне з'єднання: нове закриває попереднє, і події закритого до слухача вже не доходять. Потокобезпечний;
 * методи не блокують: важке (завантаження контенту заради його хешу) — у фоновому виконавці, мережа — у потоці
 * з'єднання.
 */
public final class GameClient implements AutoCloseable {

    /** Тека світів у домашній теці гри. */
    private static final String WORLDS = "worlds";

    private final EmbeddedServer server;
    private final int lanPort;
    private final TokenStore tokens;
    private final SessionListener listener;
    private final Executor background;
    private ServerConnection connection;
    private SocketAddress address;
    private CurrentListener current;
    private ServerMessage.Joined credentials;
    private InetSocketAddress lan;

    private GameClient(
            EmbeddedServer server, int lanPort, TokenStore tokens, SessionListener listener, Executor background) {
        this.server = server;
        this.lanPort = lanPort;
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.background = Objects.requireNonNull(background, "background");
    }

    /** Гра з типовою домашньою текою ({@link EmbeddedServer#defaultHome()}); LAN — на порту {@value Protocol#DEFAULT_PORT}. */
    public static GameClient start(SessionListener listener, Executor background) {
        return start(EmbeddedServer.defaultHome(), Protocol.DEFAULT_PORT, listener, background);
    }

    /**
     * @param home домашня тека гри: у ній тека світів вбудованого сервера ({@code worlds}) і токени гравця ({@link
     *     TokenStore#FILE_NAME})
     * @param lanPort порт гри для локальної мережі; 0 — будь-який вільний
     */
    public static GameClient start(Path home, int lanPort, SessionListener listener, Executor background) {
        TokenStore tokens = new TokenStore(home.resolve(TokenStore.FILE_NAME));
        return new GameClient(
                EmbeddedServer.startWithBundledContent(home.resolve(WORLDS)), lanPort, tokens, listener, background);
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

    /** Світи теки вбудованого сервера. */
    public CompletableFuture<List<WorldInfo>> localWorlds() {
        return CompletableFuture.supplyAsync(server::address, background)
                .thenCompose(this::connection)
                .thenCompose(ServerConnection::worlds);
    }

    /**
     * Завантажує світ із теки вбудованого сервера в нове лобі. Якщо на диску є токен цього світу — клієнт сідає на своє
     * місце, інакше заходить гостем під нікнеймом і віддає місце собі сам.
     *
     * @param openLan відкрити гру й для локальної мережі; порт зайнятий — {@link LanUnavailableException}
     */
    public CompletableFuture<ServerMessage.Joined> loadLocalWorld(WorldInfo world, String nickname, boolean openLan) {
        return CompletableFuture.supplyAsync(
                        () -> {
                            if (openLan) {
                                openLan();
                            }
                            return server.address();
                        },
                        background)
                .thenCompose(this::connection)
                .thenCompose(connection -> remember(connection.loadWorld(world.name(), nickname, seat(world))));
    }

    /** З'єднується з сервером за адресою й повертає світи його теки. */
    public CompletableFuture<List<WorldInfo>> findWorlds(InetSocketAddress address) {
        return connection(address).thenCompose(ServerConnection::worlds);
    }

    /**
     * Завантажує світ із теки сервера поточного з'єднання ({@link #findWorlds}). Сервер з мережі відкриває світ лише
     * з токеном місця в ньому.
     */
    public CompletableFuture<ServerMessage.Joined> loadWorld(WorldInfo world, String nickname) {
        ServerConnection open = open();
        if (open == null) {
            return CompletableFuture.failedFuture(new ConnectionClosedException("немає з'єднання з сервером"));
        }
        return remember(open.loadWorld(world.name(), nickname, seat(world)));
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

    /**
     * Входить у лобі зі списку: якщо на диску є токен цього світу — повертається на своє місце, а якщо токен не
     * підійшов (місце вже віддали іншому) чи його немає — приєднується під нікнеймом.
     */
    public CompletableFuture<ServerMessage.Joined> joinLobby(LobbyInfo lobby, String nickname) {
        ServerConnection open = open();
        if (open == null) {
            return CompletableFuture.failedFuture(new ConnectionClosedException("немає з'єднання з сервером"));
        }
        Optional<PlayerToken> seat = tokens.find(lobby.world());
        if (seat.isEmpty()) {
            return joinLobby(lobby.session(), nickname);
        }
        return remember(open.rejoin(lobby.session(), seat.get()).exceptionallyCompose(error -> {
            if (error.getCause() instanceof ServerErrorException rejected
                    && rejected.error().code() == ErrorCode.UNAUTHORIZED) {
                return open.joinLobby(lobby.session(), nickname);
            }
            return CompletableFuture.failedFuture(error);
        }));
    }

    /** Хост віддає гостеві лобі завантаженого світу вільне місце; помилку отримає слухач. */
    public void assignSeat(int guest, int seat) {
        ServerConnection open = open();
        if (open != null) {
            open.assignSeat(guest, seat);
        }
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

    /** Хост обирає таймер ходу в лобі. */
    public void setTimer(TurnTimer timer) {
        ServerConnection open = open();
        if (open != null) {
            open.setTimer(timer);
        }
    }

    /** Хост завершує рік {@code turn}, не чекаючи «Готово» всіх. */
    public void endYear(int turn) {
        ServerConnection open = open();
        if (open != null) {
            open.endYear(turn);
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
            remember(joined);
            return joined;
        });
    }

    private void remember(ServerMessage.Joined joined) {
        synchronized (this) {
            credentials = joined;
        }
        tokens.save(joined.world(), new PlayerToken(joined.player(), joined.token()));
    }

    /** Збережений токен місця в цьому світі. */
    private Optional<PlayerToken> seat(WorldInfo world) {
        return world.key().flatMap(tokens::find);
    }

    private synchronized ServerConnection open() {
        return connection != null && connection.isOpen() ? connection : null;
    }

    /** Слухач одного з'єднання: передає події, доки це з'єднання поточне. */
    private final class CurrentListener implements SessionListener {
        private final SessionListener target;
        private volatile boolean active = true;

        CurrentListener(SessionListener target) {
            this.target = target;
        }

        @Override
        public void joined(ServerMessage.Joined joined) {
            if (active) {
                remember(joined);
                target.joined(joined);
            }
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
