package kolo.client.net;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import kolo.engine.content.ContentPack;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.VersionMismatchException;
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
 * <p>Одночасно — одне з'єднання: нове закриває попереднє, і події закритого до слухача вже не доходять. Виняток —
 * hot-seat (GD §21): у лобі вбудованого сервера, не відкритого для мережі, можна додати гравців за цим комп'ютером
 * ({@link #addLocalPlayer}) — кожен зі своїм з'єднанням і токеном, тож сервер бачить їх як різних гравців. Слухач
 * отримує події лише місця того, хто зараз за комп'ютером ({@link #showPlayer}); решта місць запам'ятовує свій стан,
 * щоб показати його, коли комп'ютер передадуть. Черга ходу — хто відкрив лобі, далі інші в порядку додавання.
 *
 * <p>Потокобезпечний; методи не блокують: важке (завантаження контенту заради його хешу) — у фоновому виконавці,
 * мережа — у потоці з'єднання.
 */
public final class GameClient implements AutoCloseable {

    /** Тека світів у домашній теці гри. */
    private static final String WORLDS = "worlds";

    private final EmbeddedServer server;
    private final LanPorts lanPorts;
    private final TokenStore tokens;
    private final SessionListener listener;
    private final Executor background;
    private ServerConnection connection;
    private SocketAddress address;
    private CurrentListener current;
    private ServerMessage.Joined credentials;
    private LanAddresses lan;
    /** Інші гравці за цим комп'ютером (hot-seat), у порядку черги. */
    private final List<LocalSeat> locals = new ArrayList<>();
    /** Місце того, хто зараз за комп'ютером: його події йдуть слухачеві; {@code null} — ще немає з'єднання. */
    private CurrentListener shown;

    private GameClient(
            EmbeddedServer server,
            LanPorts lanPorts,
            TokenStore tokens,
            SessionListener listener,
            Executor background) {
        this.server = server;
        this.lanPorts = Objects.requireNonNull(lanPorts, "lanPorts");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.background = Objects.requireNonNull(background, "background");
    }

    /**
     * Гра з типовою домашньою текою ({@link EmbeddedServer#defaultHome()}); LAN — на порту {@value
     * Protocol#DEFAULT_PORT}, пошук — на UDP-порту {@value Protocol#DISCOVERY_PORT}.
     */
    public static GameClient start(SessionListener listener, Executor background) {
        return start(EmbeddedServer.defaultHome(), LanPorts.DEFAULT, listener, background);
    }

    /**
     * @param home домашня тека гри: у ній тека світів вбудованого сервера ({@code worlds}) і токени гравця ({@link
     *     TokenStore#FILE_NAME})
     * @param lanPorts порти гри й пошуку в локальній мережі
     */
    public static GameClient start(Path home, LanPorts lanPorts, SessionListener listener, Executor background) {
        TokenStore tokens = new TokenStore(home.resolve(TokenStore.FILE_NAME));
        return new GameClient(
                EmbeddedServer.startWithBundledContent(home.resolve(WORLDS)), lanPorts, tokens, listener, background);
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
     * місце, інакше заходить гостем під нікнеймом і віддає місце собі сам. Якщо на диску місця кількох гравців за цим
     * комп'ютером (hot-seat) і гру не відкрито для мережі — вони теж сідають на свої місця; місце, яке вже віддали
     * іншому, пропускається. У мережевій грі кожен гравець — за своїм комп'ютером, тож сідає лише перший.
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
                .thenCompose(connection -> {
                    List<PlayerToken> seats = seats(world);
                    CompletableFuture<ServerMessage.Joined> opened = remember(connection.loadWorld(
                            world.name(), nickname, seats.stream().findFirst()));
                    if (openLan || seats.size() < 2) {
                        return opened;
                    }
                    return opened.thenCompose(joined -> rejoinLocals(joined.session(), seats.subList(1, seats.size()))
                            .thenApply(done -> joined));
                });
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
        return remember(
                open.loadWorld(world.name(), nickname, seats(world).stream().findFirst()));
    }

    /** З'єднується з сервером за адресою й повертає його відкриті лобі. */
    public CompletableFuture<List<LobbyInfo>> findLobbies(InetSocketAddress address) {
        return connection(address).thenCompose(ServerConnection::lobbies);
    }

    /**
     * Шукає ігри в локальній мережі ({@link LanSearch} на порту пошуку цього клієнта) і збирає їхні відкриті лобі:
     * з кожною сумісною грою — коротке окреме з'єднання лише заради списку, поточне з'єднання не змінюється. Гра, що
     * відповіла на пошук, але не на з'єднання (вже закрилася), пропускається.
     *
     * @throws LanSearchUnavailableException (у future) якщо пошук неможливий
     */
    public CompletableFuture<LanLobbies> findLanLobbies() {
        return findLanLobbies(new LanSearch(lanPorts.discovery(), LanSearch.WAIT));
    }

    /**
     * Контент гри — той, чий хеш звірено з сервером при з'єднанні, тож id з повідомлень (картка держави, сектори коліс)
     * він підписує так само, як сервер. Перше звернення завантажує контент — викликати не з потоку UI; далі —
     * миттєво.
     */
    public ContentPack content() {
        return server.content();
    }

    /** Те саме з заданим пошуком. */
    CompletableFuture<LanLobbies> findLanLobbies(LanSearch search) {
        CompletableFuture<List<LanHost>> hosts = CompletableFuture.supplyAsync(search::find, background);
        CompletableFuture<String> hash = CompletableFuture.supplyAsync(server::contentHash, background);
        return hosts.thenCombine(hash, Found::new).thenCompose(found -> {
            List<CompletableFuture<HostLobbies>> asked = new ArrayList<>();
            for (LanHost host : found.hosts()) {
                asked.add(
                        host.version() == Protocol.VERSION
                                ? lobbiesOf(host.server(), found.hash())
                                : CompletableFuture.completedFuture(HostLobbies.INCOMPATIBLE));
            }
            return CompletableFuture.allOf(asked.toArray(CompletableFuture[]::new))
                    .thenApply(done -> {
                        List<RemoteLobby> lobbies = new ArrayList<>();
                        int incompatible = 0;
                        for (CompletableFuture<HostLobbies> one : asked) {
                            HostLobbies result = one.join();
                            lobbies.addAll(result.lobbies());
                            incompatible += result.incompatible() ? 1 : 0;
                        }
                        return new LanLobbies(lobbies, incompatible);
                    });
        });
    }

    /** Лобі однієї гри через окреме з'єднання, яке одразу закривається. */
    private static CompletableFuture<HostLobbies> lobbiesOf(InetSocketAddress address, String hash) {
        return ServerConnection.connect(address, hash, SessionListener.NONE)
                .thenCompose(connection -> connection.lobbies().whenComplete((lobbies, error) -> connection.close()))
                .thenApply(lobbies -> new HostLobbies(
                        lobbies.stream()
                                .map(lobby -> new RemoteLobby(address, lobby))
                                .toList(),
                        false))
                .exceptionally(error -> incompatible(error) ? HostLobbies.INCOMPATIBLE : HostLobbies.NONE);
    }

    /** Чи з'єднання не вдалося через іншу версію протоколу чи контенту — на боці клієнта чи сервера. */
    private static boolean incompatible(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof VersionMismatchException
                || (cause instanceof ServerErrorException server
                        && server.error().code() == ErrorCode.VERSION_MISMATCH);
    }

    /**
     * Входить у лобі на сервері за його адресою (зі списку {@link #findLobbies} чи {@link #findLanLobbies}): з
     * поточним з'єднанням, якщо воно з тим самим сервером, інакше — з новим. Далі — як {@link #joinLobby(LobbyInfo,
     * String)}.
     */
    public CompletableFuture<ServerMessage.Joined> joinLobby(RemoteLobby lobby, String nickname) {
        return connection(lobby.server()).thenCompose(open -> join(open, lobby.lobby(), nickname));
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
        return join(open, lobby, nickname);
    }

    private CompletableFuture<ServerMessage.Joined> join(ServerConnection open, LobbyInfo lobby, String nickname) {
        Optional<PlayerToken> seat = tokens.find(lobby.world()).stream().findFirst();
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

    // ---- Hot-seat ----

    /** Чи можна додати гравця за цим комп'ютером: клієнт у лобі вбудованого сервера, не відкритого для мережі. */
    public synchronized boolean canAddLocalPlayers() {
        return credentials != null && lan == null && server.address().equals(address);
    }

    /**
     * Додає до лобі вбудованого сервера ще одного гравця за цим комп'ютером (hot-seat) — окремим з'єднанням. Hot-seat
     * — лише без таймера ходу: обраний хостом таймер скидається. У лобі завантаженого світу гравець заходить гостем, і
     * місце йому віддає хост ({@link #assignSeat}).
     *
     * @throws IllegalStateException (у future) якщо гравця за цим комп'ютером додати не можна ({@link
     *     #canAddLocalPlayers()})
     */
    public CompletableFuture<ServerMessage.Joined> addLocalPlayer(String nickname) {
        long session;
        synchronized (this) {
            if (!canAddLocalPlayers()) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("гравців за цим комп'ютером — лише в лобі гри без мережі"));
            }
            session = credentials.session();
        }
        return seatLocal(connection -> connection.joinLobby(session, nickname));
    }

    /** Гравець за цим комп'ютером (не той, хто відкрив лобі) полишає лобі; його з'єднання закривається. */
    public void removeLocalPlayer(int player) {
        LocalSeat removed = null;
        synchronized (this) {
            for (LocalSeat local : locals) {
                if (local.player() == player) {
                    removed = local;
                }
            }
            if (removed == null) {
                return;
            }
            locals.remove(removed);
            removed.listener().active = false;
            if (shown == removed.listener()) {
                shown = current;
            }
        }
        removed.leave();
        saveTokens(removed.listener().credentials.world());
    }

    /** Чи за цим комп'ютером кілька гравців (hot-seat). */
    public synchronized boolean hotSeat() {
        return !locals.isEmpty();
    }

    /** Номери гравців за цим комп'ютером у порядку черги: першим — той, хто відкрив лобі. */
    public synchronized List<Integer> localPlayers() {
        List<Integer> players = new ArrayList<>();
        if (credentials != null) {
            players.add(credentials.player());
        }
        for (LocalSeat local : locals) {
            players.add(local.player());
        }
        return List.copyOf(players);
    }

    /** Чи цей гравець — за цим комп'ютером. */
    public boolean isLocal(PlayerInfo player) {
        return localPlayers().contains(player.number());
    }

    /** Кому передати комп'ютер після «Готово» того, хто за ним: наступному в черзі; порожньо — він останній. */
    public synchronized OptionalInt handoffAfterReady() {
        List<Integer> order = localPlayers();
        int at = credentialsOf(shown)
                .map(joined -> order.indexOf(joined.player()))
                .orElse(-1);
        return at >= 0 && at + 1 < order.size() ? OptionalInt.of(order.get(at + 1)) : OptionalInt.empty();
    }

    /** Кому передати комп'ютер на початку року: першому в черзі, якщо за комп'ютером зараз не він. */
    public synchronized OptionalInt handoffAtYearStart() {
        List<Integer> order = localPlayers();
        if (order.size() < 2
                || credentialsOf(shown)
                        .map(joined -> joined.player() == order.getFirst())
                        .orElse(true)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(order.getFirst());
    }

    /**
     * Комп'ютер передали гравцеві {@code player}: тепер слухач отримує події його місця — спершу гравців і, якщо гра
     * йде, її початок з поточною фазою року ({@link SessionListener#gameStarted}), щоб показати його карту.
     *
     * @return чи це гравець за цим комп'ютером
     */
    public boolean showPlayer(int player) {
        synchronized (this) {
            CurrentListener seat = null;
            if (credentials != null && credentials.player() == player) {
                seat = current;
            }
            for (LocalSeat local : locals) {
                if (local.player() == player) {
                    seat = local.listener();
                }
            }
            if (seat == null) {
                return false;
            }
            shown = seat;
            seat.replay();
            return true;
        }
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

    /** «Готово» для року {@code turn} від того, хто за комп'ютером. */
    public void ready(int turn) {
        ServerConnection open = shownConnection();
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
        ServerConnection open = shownConnection();
        if (open != null) {
            open.endYear(turn);
        }
    }

    /** Хост відновлює рік {@code turn}, на якому сесію призупинено. */
    public void resume(int turn) {
        ServerConnection open = shownConnection();
        if (open != null) {
            open.resume(turn);
        }
    }

    /** Полишає сесію — разом з усіма гравцями за цим комп'ютером; повернутися до неї вже не можна. */
    public void leave() {
        ServerConnection open;
        List<LocalSeat> dropped;
        synchronized (this) {
            credentials = null;
            open = connection;
            dropped = dropLocals();
        }
        dropped.forEach(LocalSeat::leave);
        if (open != null && open.isOpen()) {
            open.leave();
        }
    }

    /** Гравець поточної сесії — той, хто зараз за комп'ютером. */
    public synchronized Optional<ServerMessage.Joined> credentials() {
        return credentialsOf(shown);
    }

    /** Чи це я — гравець поточної сесії, що зараз за комп'ютером. */
    public boolean isMe(PlayerInfo player) {
        return credentials().map(joined -> joined.player() == player.number()).orElse(false);
    }

    /** Адреси гри в локальній мережі, якщо її відкрито. */
    public synchronized Optional<LanAddresses> lanAddress() {
        return Optional.ofNullable(lan);
    }

    /** Закриває з'єднання й зупиняє вбудований сервер. */
    @Override
    public void close() {
        ServerConnection open;
        List<LocalSeat> dropped;
        synchronized (this) {
            open = connection;
            connection = null;
            if (current != null) {
                current.active = false;
            }
            dropped = dropLocals();
        }
        dropped.forEach(local -> local.connection().close());
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
        LanAddresses bound;
        try {
            InetSocketAddress game = server.openLan(new InetSocketAddress(lanPorts.game()), lanPorts.discovery());
            bound = new LanAddresses(game, server.discoveryAddress());
        } catch (Exception e) {
            // Netty кидає й перевірювані винятки без оголошення (BindException — порт зайнятий).
            throw new LanUnavailableException(lanPorts.game(), e);
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
        List<LocalSeat> dropped = List.of();
        synchronized (this) {
            previous = connection;
            connection = null;
            if (!target.equals(address)) {
                // Інший сервер — інша сесія: гравці за цим комп'ютером лишаються в попередній.
                dropped = dropLocals();
            }
            address = target;
            if (current != null) {
                current.active = false;
            }
            if (shown == null || shown == current) {
                shown = next;
            }
            current = next;
        }
        dropped.forEach(LocalSeat::leave);
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
        saveTokens(joined.world());
    }

    /** Збережені місця клієнта в цьому світі. */
    private List<PlayerToken> seats(WorldInfo world) {
        return world.key().map(tokens::find).orElse(List.of());
    }

    /** Запам'ятовує на диску місця всіх гравців за цим комп'ютером у світі. */
    private void saveTokens(String world) {
        List<PlayerToken> seats = new ArrayList<>();
        synchronized (this) {
            if (credentials != null && credentials.world().equals(world)) {
                seats.add(new PlayerToken(credentials.player(), credentials.token()));
            }
            for (LocalSeat local : locals) {
                ServerMessage.Joined joined = local.listener().credentials;
                if (joined.world().equals(world)) {
                    seats.add(new PlayerToken(joined.player(), joined.token()));
                }
            }
        }
        tokens.save(world, seats);
    }

    private synchronized ServerConnection open() {
        return connection != null && connection.isOpen() ? connection : null;
    }

    /** З'єднання того, хто зараз за комп'ютером. */
    private synchronized ServerConnection shownConnection() {
        for (LocalSeat local : locals) {
            if (local.listener() == shown) {
                return local.connection().isOpen() ? local.connection() : null;
            }
        }
        return open();
    }

    /** Гравець місця з цим слухачем. Викликати під замком. */
    private Optional<ServerMessage.Joined> credentialsOf(CurrentListener seat) {
        if (seat == null || seat == current) {
            return Optional.ofNullable(credentials);
        }
        return Optional.ofNullable(seat.credentials);
    }

    // ---- Місця гравців за цим комп'ютером ----

    /** Нове з'єднання з вбудованим сервером для ще одного гравця за цим комп'ютером. */
    private CompletableFuture<ServerMessage.Joined> seatLocal(
            Function<ServerConnection, CompletableFuture<ServerMessage.Joined>> joining) {
        CurrentListener seat = new CurrentListener(listener);
        return CompletableFuture.supplyAsync(server::contentHash, background)
                .thenCompose(hash -> ServerConnection.connect(server.address(), hash, seat))
                .thenCompose(opened -> joining.apply(opened)
                        .thenApply(joined -> {
                            addLocal(new LocalSeat(opened, seat), joined);
                            return joined;
                        })
                        .whenComplete((joined, error) -> {
                            if (error != null) {
                                opened.close();
                            }
                        }));
    }

    private void addLocal(LocalSeat local, ServerMessage.Joined joined) {
        synchronized (this) {
            if (credentials == null || credentials.session() != joined.session()) {
                // Тим часом лобі полишили.
                local.listener().active = false;
                throw new ConnectionClosedException("лобі вже полишено");
            }
            local.listener().credentials = joined;
            locals.add(local);
        }
        saveTokens(joined.world());
        keepManualTimer();
    }

    /** Повертає гравців за цим комп'ютером на їхні місця по черзі; місце, яке вже віддали, пропускається. */
    private CompletableFuture<Void> rejoinLocals(long session, List<PlayerToken> seats) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (PlayerToken seat : seats) {
            chain = chain.thenCompose(done ->
                    seatLocal(connection -> connection.rejoin(session, seat)).handle((joined, error) -> null));
        }
        return chain;
    }

    /** Hot-seat — лише без таймера ходу: хост за цим комп'ютером скидає таймер, обраний раніше чи збережений у світі. */
    private void keepManualTimer() {
        ServerConnection open;
        synchronized (this) {
            ServerMessage.Lobby lobby = current == null ? null : current.lobby;
            if (locals.isEmpty()
                    || credentials == null
                    || lobby == null
                    || !lobby.setup().timer().timed()
                    || lobby.players().stream().noneMatch(p -> p.host() && p.number() == credentials.player())) {
                return;
            }
            open = open();
        }
        if (open != null) {
            open.setTimer(TurnTimer.MANUAL);
        }
    }

    /** Забуває гравців за цим комп'ютером; з'єднання закриває той, хто викликав. Викликати під замком. */
    private List<LocalSeat> dropLocals() {
        List<LocalSeat> dropped = List.copyOf(locals);
        for (LocalSeat local : dropped) {
            local.listener().active = false;
        }
        locals.clear();
        if (shown != current) {
            shown = current;
        }
        return dropped;
    }

    /** Що знайшов пошук і хеш контенту клієнта для привітання. */
    private record Found(List<LanHost> hosts, String hash) {}

    /** Лобі однієї гри з пошуку. */
    private record HostLobbies(List<RemoteLobby> lobbies, boolean incompatible) {
        static final HostLobbies NONE = new HostLobbies(List.of(), false);
        static final HostLobbies INCOMPATIBLE = new HostLobbies(List.of(), true);
    }

    /** Місце ще одного гравця за цим комп'ютером: його з'єднання й слухач. */
    private record LocalSeat(ServerConnection connection, CurrentListener listener) {

        int player() {
            return listener.credentials.player();
        }

        /** Полишає сесію й закриває з'єднання. */
        void leave() {
            if (connection.isOpen()) {
                connection.leave();
            }
            connection.close();
        }
    }

    /**
     * Слухач одного з'єднання: запам'ятовує стан свого місця й передає події, доки це з'єднання поточне і за
     * комп'ютером — гравець цього місця. Стан і передача — під замком гри: перемикання місця ({@link #showPlayer}) не
     * переплітається з подіями.
     */
    private final class CurrentListener implements SessionListener {
        private final SessionListener target;
        private volatile boolean active = true;
        // Під замком гри.
        private ServerMessage.Joined credentials;
        private ServerMessage.Lobby lobby;
        private GameStart start;
        private ServerMessage.Phase phase;
        private List<PlayerInfo> players = List.of();

        CurrentListener(SessionListener target) {
            this.target = target;
        }

        /** Чи передавати події слухачеві. Викликати під замком гри. */
        private boolean forwarding() {
            return active && shown == this;
        }

        /** Стан місця — слухачеві, коли за комп'ютер сів його гравець. Викликати під замком гри. */
        void replay() {
            target.players(players);
            if (start != null) {
                target.gameStarted(new GameStart(start.map(), start.card(), phase));
            }
        }

        @Override
        public void joined(ServerMessage.Joined joined) {
            if (!active) {
                return;
            }
            boolean primary;
            synchronized (GameClient.this) {
                primary = this == current;
                if (!primary) {
                    credentials = joined;
                }
            }
            if (primary) {
                remember(joined);
            } else {
                saveTokens(joined.world());
            }
            synchronized (GameClient.this) {
                if (forwarding()) {
                    target.joined(joined);
                }
            }
        }

        @Override
        public void lobby(ServerMessage.Lobby lobby) {
            boolean primary;
            synchronized (GameClient.this) {
                this.lobby = lobby;
                primary = active && this == current;
                if (forwarding()) {
                    target.lobby(lobby);
                }
            }
            if (primary) {
                keepManualTimer();
            }
        }

        @Override
        public void gameStarted(GameStart start) {
            synchronized (GameClient.this) {
                this.start = start;
                phase = start.phase();
                if (forwarding()) {
                    target.gameStarted(start);
                }
            }
        }

        @Override
        public void players(List<PlayerInfo> players) {
            synchronized (GameClient.this) {
                this.players = List.copyOf(players);
                if (forwarding()) {
                    target.players(players);
                }
            }
        }

        @Override
        public void phase(ServerMessage.Phase phase) {
            synchronized (GameClient.this) {
                this.phase = phase;
                if (forwarding()) {
                    target.phase(phase);
                }
            }
        }

        @Override
        public void error(ServerMessage.Error error) {
            synchronized (GameClient.this) {
                if (forwarding()) {
                    target.error(error);
                }
            }
        }

        @Override
        public void disconnected() {
            synchronized (GameClient.this) {
                if (forwarding()) {
                    target.disconnected();
                }
            }
        }
    }
}
