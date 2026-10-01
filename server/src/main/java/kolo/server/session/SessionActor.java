package kolo.server.session;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import kolo.engine.content.ContentPack;
import kolo.engine.error.ConflictException;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ForbiddenException;
import kolo.engine.error.GameException;
import kolo.engine.error.NotFoundException;
import kolo.engine.error.PhaseClosedException;
import kolo.engine.error.SaveFileException;
import kolo.engine.error.UnauthorizedException;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import kolo.engine.state.WorldState;
import kolo.engine.turn.TurnPipeline;
import kolo.engine.view.MapViews;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.LobbySetup;
import kolo.protocol.message.MapChunks;
import kolo.protocol.message.Nicknames;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.PlayerToken;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.YearPhase;
import kolo.server.auth.PlayerTokens;
import kolo.server.persistence.MapSnapshot;
import kolo.server.persistence.PlayerRecord;
import kolo.server.persistence.SavedPlayer;
import kolo.server.persistence.StateSnapshot;
import kolo.server.persistence.WorldDirectory;
import kolo.server.persistence.WorldMeta;
import kolo.server.persistence.WorldStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Одна ігрова сесія: лобі, світ, його файл і гравці. Усі зміни сесії виконуються в її власному потоці ({@code
 * kolo-session-<номер>}) по черзі, тож стан без блокувань; публічні методи лише ставлять завдання в чергу й
 * повертаються одразу. Результати гравці отримують повідомленнями через {@link Peer}.
 *
 * <p>Стани — {@link SessionState}. Хост відкриває лобі ({@code LOBBY}), інші приєднуються з нікнеймами, кожен отримує
 * номер і токен. Хост починає гру: гравці — усі, хто в лобі, у порядку приєднання, {@code n}-й отримує державу {@code
 * n}; сесія генерує світ і створює файл світу з гравцями ({@code GENERATING}), надсилає карту й живе роками ({@code
 * RUNNING}). Рік — фази {@link YearPhase}: {@code START_OF_YEAR → ORDERS}; коли «Готово» натиснули всі гравці на
 * зв'язку — {@code RESOLVING} (рушій розв'язує рік, рік пишеться у файл однією транзакцією) → {@code REPORT} →
 * наступний рік. Якщо рік не вдалося розв'язати чи зберегти — {@code PAUSED}: стан і файл лишаються на попередньому
 * році, гравці отримують помилку.
 *
 * <p>З лобі гравець іде зовсім (пішов хост — хостом стає наступний за порядком); з гри — лише від'єднується: держава
 * лишається його, а з токеном він повертається ({@link #rejoin}). Коли на зв'язку не лишилося нікого, сесія
 * закривається: файл закривається, потік зупиняється; продовжити світ можна, завантаживши файл.
 *
 * <p>Завантажений світ ({@link #load}) теж починається з лобі, але гравці й держави в ньому вже є — з файлу. Гравець
 * світу з токеном сідає на своє місце ({@link #rejoin}); хто прийшов без токена (з іншого комп'ютера, з іншого режиму)
 * — гість лобі під нікнеймом, і хост віддає йому вільне місце ({@link #assign}) з новим токеном. Гру почато — рік
 * продовжується з останнього збереженого; гравці світу, яких немає, — не на зв'язку, як після від'єднання.
 */
public final class SessionActor {

    private static final Logger LOG = LoggerFactory.getLogger(SessionActor.class);

    private final long id;
    private final ContentPack content;
    private final WorldDirectory worlds;
    private final PlayerTokens tokens;
    private final UnaryOperator<WorldState> years;
    private final Consumer<SessionActor> onClosed;
    private final ExecutorService executor;
    private volatile SessionState state = SessionState.LOBBY;
    private volatile LobbyInfo lobby;

    // Далі — лише в потоці сесії.
    private final List<Member> members = new ArrayList<>();
    private int nextPlayer = 1;
    private String worldKey;
    private LobbySetup setup;
    private YearPhase phase;
    private WorldState world;
    private MapSnapshot map;
    private WorldStore store;
    /** Файл світу, зайнятий цією сесією в теці; відпускається при закритті. */
    private Path claimed;

    private ServerMessage.Error pauseError;

    /**
     * @param years розв'язання року: стан на початку року → новий стан наприкінці; у грі — {@link TurnPipeline}
     * @param onClosed викликається в потоці сесії, коли її закрито
     */
    SessionActor(
            long id,
            ContentPack content,
            WorldDirectory worlds,
            PlayerTokens tokens,
            UnaryOperator<WorldState> years,
            Consumer<SessionActor> onClosed) {
        this.id = id;
        this.content = Objects.requireNonNull(content, "content");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.years = Objects.requireNonNull(years, "years");
        this.onClosed = Objects.requireNonNull(onClosed, "onClosed");
        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "kolo-session-" + id);
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Розв'язання року рушієм. */
    static UnaryOperator<WorldState> engine(ContentPack content) {
        return state -> TurnPipeline.resolve(state, content).newState();
    }

    public long id() {
        return id;
    }

    /** Поточний стан; читається з будь-якого потоку. */
    public SessionState state() {
        return state;
    }

    /** Лобі для списку сервера, поки гру не почато; читається з будь-якого потоку. */
    public Optional<LobbyInfo> lobby() {
        return Optional.ofNullable(lobby);
    }

    /** Відкриває лобі з хостом і параметрами світу; хост отримує свій номер, токен і стан лобі. */
    public void open(Peer host, String nickname, long seed, NpcShare npcShare) {
        Objects.requireNonNull(host, "host");
        Nicknames.check("nickname", nickname);
        Objects.requireNonNull(npcShare, "npcShare");
        post(() -> doOpen(host, nickname, seed, npcShare));
    }

    /**
     * Завантажує світ із теки в лобі з цим хостом. З токеном свого місця ({@code seat}) хост сідає на нього; без нього
     * — заходить гостем, якщо з'єднання довірене ({@code trusted}: клієнт того самого процесу, що й сервер), інакше —
     * {@code UNAUTHORIZED}. Світу немає ({@code NOT_FOUND}), його відкрито іншою сесією ({@code WORLD_IN_USE}), файл не
     * прочитати чи світ іншого контенту ({@code SAVE_CONTENT_MISMATCH}) — помилка хостові й закриття сесії.
     *
     * @param world ім'я світу в теці ({@link WorldDirectory#list()})
     * @param nickname нікнейм хоста, якщо він зайде гостем
     */
    public void load(Peer host, String world, String nickname, Optional<PlayerToken> seat, boolean trusted) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(world, "world");
        Nicknames.check("nickname", nickname);
        Objects.requireNonNull(seat, "seat");
        post(() -> doLoad(host, world, nickname, seat, trusted));
    }

    /**
     * Гравець приєднується до лобі. Гру вже почато — {@code LOBBY_CLOSED}, лобі повне — {@code LOBBY_FULL}, нікнейм
     * зайнятий — {@code NICKNAME_TAKEN}; помилка — лише йому.
     */
    public void join(Peer player, String nickname) {
        Objects.requireNonNull(player, "player");
        Nicknames.check("nickname", nickname);
        post(() -> doJoin(player, nickname));
    }

    /**
     * Хост віддає гостеві лобі завантаженого світу вільне місце гравця {@code seat}: гість отримує номер місця й новий
     * токен. Не хост — {@code FORBIDDEN}, лобі не завантаженого світу чи гру почато — {@code LOBBY_CLOSED}, гостя чи
     * місця немає — {@code NOT_FOUND}, місце зайняте — {@code SEAT_TAKEN}, той самий нікнейм в іншого гравця світу —
     * {@code NICKNAME_TAKEN}; помилка — лише хостові.
     */
    public void assign(Peer host, int guest, int seat) {
        Objects.requireNonNull(host, "host");
        post(() -> doAssign(host, guest, seat));
    }

    /**
     * Хост починає гру: новий світ генерується з гравцями лобі, створюється його файл; завантажений — продовжується з
     * останнього збереженого року, гравці пишуться у файл. Гравці отримують карту, список гравців і фази року. Не хост
     * — {@code FORBIDDEN}, гру вже почато — {@code LOBBY_CLOSED}, у лобі завантаженого світу є гості без місця — {@code
     * PLAYERS_UNSEATED}. Невдача генерації чи запису (параметри поза межами, файл не створено) — помилка всім і
     * закриття сесії.
     */
    public void start(Peer player) {
        Objects.requireNonNull(player, "player");
        post(() -> doStart(player));
    }

    /**
     * Гравець повертається з токеном на нове з'єднання; старе, якщо ще відкрите, закривається. Невірний гравець чи
     * токен — {@code UNAUTHORIZED}.
     */
    public void rejoin(Peer player, int number, String token) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(token, "token");
        post(() -> doRejoin(player, number, token));
    }

    /**
     * Гравець натиснув «Готово» для року {@code turn}. Не гравець сесії, не той рік чи не фаза наказів — помилка
     * {@code PHASE_CLOSED} лише йому.
     */
    public void ready(Peer player, int turn) {
        Objects.requireNonNull(player, "player");
        post(() -> doReady(player, turn));
    }

    /** Гравець полишив сесію (вийшов, закрив з'єднання чи перейшов в іншу сесію). */
    public void leave(Peer player) {
        Objects.requireNonNull(player, "player");
        post(() -> doLeave(player));
    }

    /** Закриває сесію після завдань, що вже в черзі. */
    public void close() {
        post(this::shutdown);
    }

    /**
     * Чекає, доки потік сесії зупиниться.
     *
     * @return чи зупинився за відведений час
     */
    public boolean awaitClosed(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    @Override
    public String toString() {
        return "SessionActor[" + id + ", " + state + "]";
    }

    // ---- Потік сесії ----

    private void post(Runnable task) {
        try {
            executor.execute(() -> {
                if (state == SessionState.CLOSED) {
                    return;
                }
                try {
                    task.run();
                } catch (RuntimeException e) {
                    // Межа сесії: баг сервера не має лишити гравців у сесії, що вже не відповідає.
                    LOG.error("Збій сесії {}", id, e);
                    for (Member member : members) {
                        if (member.peer != null) {
                            member.peer.send(List.of(), true);
                        }
                    }
                    shutdown();
                }
            });
        } catch (RejectedExecutionException e) {
            // Сесію вже закрито: завдання нікому не потрібне.
        }
    }

    private void doOpen(Peer host, String nickname, long seed, NpcShare npcShare) {
        if (state != SessionState.LOBBY || !members.isEmpty()) {
            throw new IllegalStateException("лобі сесії " + id + " уже відкрито");
        }
        worldKey = WorldStore.newKey();
        setup = new LobbySetup.NewWorld(seed, npcShare);
        add(host, nickname, true);
        LOG.info("Сесія {}: лобі відкрито, хост «{}»", id, nickname);
        lobbyChanged();
    }

    private void doJoin(Peer peer, String nickname) {
        try {
            if (state != SessionState.LOBBY) {
                throw new ConflictException(ErrorCode.LOBBY_CLOSED, ErrorDetails.of("session", id));
            }
            if (connected().size() >= WorldLimits.MAX_PLAYERS) {
                throw new ConflictException(ErrorCode.LOBBY_FULL, ErrorDetails.of("max", WorldLimits.MAX_PLAYERS));
            }
            // Гравець світу, якого немає, нікнейму не тримає: він міг прийти без токена під тим самим нікнеймом.
            String key = Nicknames.key(nickname);
            if (connected().stream().anyMatch(m -> Nicknames.key(m.nickname).equals(key))) {
                throw new ConflictException(ErrorCode.NICKNAME_TAKEN, ErrorDetails.of("nickname", nickname));
            }
        } catch (GameException e) {
            peer.send(ServerMessage.Error.of(e));
            return;
        }
        add(peer, nickname, false);
        lobbyChanged();
    }

    /** Новий гравець лобі; номер і токен він отримує, коли лобі вже оновилося в списку сервера. */
    private void add(Peer peer, String nickname, boolean host) {
        String token = tokens.generate();
        Member member = new Member(nextPlayer++, nickname, PlayerTokens.hash(token));
        member.peer = peer;
        member.host = host;
        members.add(member);
        listLobby();
        peer.send(new ServerMessage.Joined(id, worldKey, member.number, token));
    }

    private void doLoad(Peer peer, String name, String nickname, Optional<PlayerToken> seat, boolean trusted) {
        if (state != SessionState.LOBBY || !members.isEmpty()) {
            throw new IllegalStateException("лобі сесії " + id + " уже відкрито");
        }
        Member seated;
        try {
            claimed = worlds.open(name);
            store = WorldStore.open(claimed);
            WorldMeta meta = store.meta();
            if (!meta.contentHash().equals(content.hash())) {
                throw new SaveFileException(
                        ErrorCode.SAVE_CONTENT_MISMATCH,
                        ErrorDetails.of("world", name, "saved", meta.contentHash(), "current", content.hash()));
            }
            world = store.loadLatest().state();
            map = store.map();
            for (SavedPlayer saved : store.players()) {
                PlayerRecord record = saved.player();
                Member member = new Member(record.number(), record.nickname(), record.tokenHash());
                member.country = record.country();
                members.add(member);
                nextPlayer = Math.max(nextPlayer, record.number() + 1);
            }
            seated = seat.flatMap(this::seat).orElse(null);
            if (seated == null && (seat.isPresent() || !trusted)) {
                throw new UnauthorizedException(ErrorDetails.of("world", name));
            }
        } catch (GameException e) {
            peer.send(ServerMessage.Error.of(e));
            shutdown();
            return;
        }
        worldKey = store.meta().key();
        setup = new LobbySetup.SavedWorld(name, store.meta().seed(), world.turn());
        if (seated != null) {
            seated.peer = peer;
            seated.host = true;
            listLobby();
            peer.send(new ServerMessage.Joined(
                    id, worldKey, seated.number, seat.orElseThrow().token()));
        } else {
            add(peer, nickname, true);
        }
        LOG.info("Сесія {}: світ {} завантажено з {}, рік {}", id, name, claimed, world.turn());
        lobbyChanged();
    }

    /** Гравець світу з цим номером і токеном. */
    private Optional<Member> seat(PlayerToken seat) {
        return members.stream()
                .filter(m ->
                        m.country >= 0 && m.number == seat.player() && PlayerTokens.matches(seat.token(), m.tokenHash))
                .findFirst();
    }

    private void doAssign(Peer peer, int guestNumber, int seatNumber) {
        Member host = member(peer);
        Member guest;
        Member seat;
        try {
            if (host == null || !host.host) {
                throw new ForbiddenException(ErrorDetails.of("action", "assign_seat"));
            }
            if (state != SessionState.LOBBY || !(setup instanceof LobbySetup.SavedWorld)) {
                throw new ConflictException(ErrorCode.LOBBY_CLOSED, ErrorDetails.of("session", id));
            }
            guest = members.stream()
                    .filter(m -> m.number == guestNumber && m.country < 0 && m.peer != null)
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException(
                            ErrorCode.NOT_FOUND, ErrorDetails.of("what", "player", "id", guestNumber)));
            seat = members.stream()
                    .filter(m -> m.number == seatNumber && m.country >= 0)
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException(
                            ErrorCode.NOT_FOUND, ErrorDetails.of("what", "seat", "id", seatNumber)));
            if (seat.peer != null) {
                throw new ConflictException(ErrorCode.SEAT_TAKEN, ErrorDetails.of("player", seatNumber));
            }
            String key = Nicknames.key(guest.nickname);
            if (members.stream()
                    .anyMatch(m -> m != seat
                            && m.country >= 0
                            && Nicknames.key(m.nickname).equals(key))) {
                throw new ConflictException(ErrorCode.NICKNAME_TAKEN, ErrorDetails.of("nickname", guest.nickname));
            }
        } catch (GameException e) {
            peer.send(ServerMessage.Error.of(e));
            return;
        }
        // Місце переходить до гостя разом із новим токеном: старий токен попереднього гравця більше не діє.
        String token = tokens.generate();
        seat.nickname = guest.nickname;
        seat.tokenHash = PlayerTokens.hash(token);
        seat.peer = guest.peer;
        seat.host = guest.host;
        members.remove(guest);
        seat.peer.send(new ServerMessage.Joined(id, worldKey, seat.number, token));
        lobbyChanged();
    }

    private void doStart(Peer peer) {
        Member starter = member(peer);
        try {
            if (starter == null || !starter.host) {
                throw new ForbiddenException(ErrorDetails.of("action", "start_game"));
            }
            if (state != SessionState.LOBBY) {
                throw new ConflictException(ErrorCode.LOBBY_CLOSED, ErrorDetails.of("session", id));
            }
            if (setup instanceof LobbySetup.SavedWorld
                    && members.stream().anyMatch(m -> m.peer != null && m.country < 0)) {
                throw new ConflictException(ErrorCode.PLAYERS_UNSEATED, ErrorDetails.of("session", id));
            }
        } catch (GameException e) {
            peer.send(ServerMessage.Error.of(e));
            return;
        }
        lobby = null;
        switch (setup) {
            case LobbySetup.NewWorld fresh -> {
                if (!generate(fresh)) {
                    return;
                }
            }
            case LobbySetup.SavedWorld saved -> {
                if (!resume(saved)) {
                    return;
                }
            }
        }
        state = SessionState.RUNNING;
        broadcast(MapChunks.split(MapViews.of(world)));
        startYear();
    }

    /** @return чи світ згенеровано й файл створено */
    private boolean generate(LobbySetup.NewWorld fresh) {
        state = SessionState.GENERATING;
        List<PlayerRecord> records = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
            member.country = i;
            records.add(new PlayerRecord(member.number, member.nickname, member.tokenHash, i, member.host));
        }
        try {
            WorldState initial = NewWorlds.generate(content, fresh.seed(), members.size(), fresh.npcShare());
            MapSnapshot snapshot = MapSnapshot.of(initial.map());
            store = worlds.create(fresh.seed(), worldKey, snapshot, StateSnapshot.of(initial, snapshot), records);
            claimed = store.file();
            map = snapshot;
            world = initial;
        } catch (GameException e) {
            broadcast(List.of(ServerMessage.Error.of(e)));
            shutdown();
            return false;
        }
        LOG.info("Сесія {}: світ {} з {} гравцями створено у {}", id, fresh.seed(), members.size(), store.file());
        return true;
    }

    /** @return чи гравців записано у файл завантаженого світу */
    private boolean resume(LobbySetup.SavedWorld saved) {
        List<PlayerRecord> records = new ArrayList<>();
        for (Member member : members) {
            records.add(
                    new PlayerRecord(member.number, member.nickname, member.tokenHash, member.country, member.host));
        }
        try {
            store.updatePlayers(records);
        } catch (GameException e) {
            broadcast(List.of(ServerMessage.Error.of(e)));
            shutdown();
            return false;
        }
        LOG.info("Сесія {}: світ {} продовжується з року {}", id, saved.name(), world.turn());
        return true;
    }

    private void doRejoin(Peer peer, int number, String token) {
        Member member = members.stream()
                .filter(m -> m.number == number && PlayerTokens.matches(token, m.tokenHash))
                .findFirst()
                .orElse(null);
        if (member == null) {
            peer.send(ServerMessage.Error.of(new UnauthorizedException(ErrorDetails.of("player", number))));
            return;
        }
        if (member.peer != null && member.peer != peer) {
            // Старе з'єднання, мабуть, напіввідкрите: гравець уже тут, із нового.
            member.peer.send(List.of(), true);
        }
        member.peer = peer;
        if (state == SessionState.LOBBY) {
            listLobby();
        }
        peer.send(new ServerMessage.Joined(id, worldKey, member.number, token));
        if (state == SessionState.LOBBY) {
            lobbyChanged();
            return;
        }
        List<ServerMessage> messages = new ArrayList<>(MapChunks.split(MapViews.of(world)));
        messages.add(new ServerMessage.Phase(world.turn(), phase));
        if (state == SessionState.PAUSED) {
            messages.add(pauseError);
        }
        peer.send(messages, false);
        playersChanged();
    }

    private void doReady(Peer peer, int turn) {
        Member member = member(peer);
        if (member == null || state != SessionState.RUNNING || phase != YearPhase.ORDERS || turn != world.turn()) {
            peer.send(ServerMessage.Error.of(new PhaseClosedException(ErrorDetails.of("turn", turn))));
            return;
        }
        if (member.ready) {
            return;
        }
        member.ready = true;
        if (!resolveIfAllReady()) {
            playersChanged();
        }
    }

    private void doLeave(Peer peer) {
        Member member = member(peer);
        if (member == null) {
            return;
        }
        if (state == SessionState.LOBBY) {
            // Гравець завантаженого світу лишає своє місце вільним; решта йде з лобі зовсім.
            if (member.country >= 0) {
                member.peer = null;
            } else {
                members.remove(member);
            }
            boolean wasHost = member.host;
            member.host = false;
            List<Member> left = connected();
            if (left.isEmpty()) {
                shutdown();
                return;
            }
            if (wasHost) {
                left.getFirst().host = true;
            }
            lobbyChanged();
            return;
        }
        member.peer = null;
        member.ready = false;
        markSeen(member);
        if (members.stream().allMatch(m -> m.peer == null)) {
            shutdown();
            return;
        }
        // Гравець, якого чекали, пішов — решта, можливо, вже готова.
        if (!resolveIfAllReady()) {
            playersChanged();
        }
    }

    /** @return чи рік розв'язано */
    private boolean resolveIfAllReady() {
        boolean allReady = members.stream().filter(m -> m.peer != null).allMatch(m -> m.ready);
        if (state == SessionState.RUNNING && phase == YearPhase.ORDERS && allReady) {
            resolveYear();
            return true;
        }
        return false;
    }

    private void resolveYear() {
        int turn = world.turn();
        phase(turn, YearPhase.RESOLVING);
        WorldState next;
        try {
            next = years.apply(world);
            store.saveTurn(StateSnapshot.of(next, map));
        } catch (GameException e) {
            // Рушій порушив інваріант або рік не записано: файл лишився на попередньому році, стан теж.
            LOG.error("Сесія {}: рік {} не розв'язано, сесію призупинено", id, turn, e);
            state = SessionState.PAUSED;
            members.forEach(m -> m.ready = false);
            pauseError = ServerMessage.Error.of(e);
            broadcast(List.of(pauseError));
            return;
        }
        world = next;
        members.forEach(m -> m.ready = false);
        phase(turn, YearPhase.REPORT);
        startYear();
    }

    private void startYear() {
        playersChanged();
        phase(world.turn(), YearPhase.START_OF_YEAR);
        phase(world.turn(), YearPhase.ORDERS);
    }

    private void phase(int turn, YearPhase next) {
        phase = next;
        broadcast(List.of(new ServerMessage.Phase(turn, next)));
    }

    private void lobbyChanged() {
        listLobby();
        broadcast(List.of(new ServerMessage.Lobby(id, worldKey, setup, players())));
    }

    /**
     * Оновлює лобі в списку сервера. Раніше, ніж гравець дізнається про вхід ({@code Joined}): клієнт, що вже знає
     * номер сесії, мусить бачити її й у списку.
     */
    private void listLobby() {
        Member host = members.stream().filter(m -> m.host).findFirst().orElseThrow();
        lobby = new LobbyInfo(id, worldKey, host.nickname, connected().size(), setup);
    }

    /** Гравці на зв'язку в порядку списку. */
    private List<Member> connected() {
        return members.stream().filter(m -> m.peer != null).toList();
    }

    private void playersChanged() {
        broadcast(List.of(new ServerMessage.Players(players())));
    }

    private List<PlayerInfo> players() {
        List<PlayerInfo> players = new ArrayList<>(members.size());
        for (Member member : members) {
            players.add(new PlayerInfo(
                    member.number,
                    member.nickname,
                    member.host,
                    member.peer != null,
                    member.ready,
                    member.country < 0 ? OptionalInt.empty() : OptionalInt.of(member.country)));
        }
        return players;
    }

    private void markSeen(Member member) {
        try {
            store.markSeen(member.number);
        } catch (GameException e) {
            // Час останнього візиту — довідка, а не стан світу: через нього сесію не зупиняємо.
            LOG.warn("Сесія {}: не записано візит гравця {}", id, member.number, e);
        }
    }

    private Member member(Peer peer) {
        for (Member member : members) {
            if (member.peer == peer) {
                return member;
            }
        }
        return null;
    }

    private void broadcast(List<ServerMessage> messages) {
        for (Member member : members) {
            if (member.peer != null) {
                member.peer.send(messages, false);
            }
        }
    }

    private void shutdown() {
        if (state == SessionState.CLOSED) {
            return;
        }
        state = SessionState.CLOSED;
        lobby = null;
        if (store != null) {
            try {
                store.close();
            } catch (GameException e) {
                LOG.error("Сесія {}: файл світу не закрито", id, e);
            }
        }
        if (claimed != null) {
            worlds.release(claimed);
        }
        executor.shutdown();
        onClosed.accept(this);
        LOG.debug("Сесію {} закрито", id);
    }

    /** Гравець сесії; змінюється лише в потоці сесії. */
    private static final class Member {
        final int number;
        String nickname;
        String tokenHash;
        /** З'єднання гравця; {@code null} — гравець не на зв'язку. */
        Peer peer;

        boolean host;
        boolean ready;
        /** Номер держави; у лобі нового світу й у гостя лобі завантаженого — −1. */
        int country = -1;

        Member(int number, String nickname, String tokenHash) {
            this.number = number;
            this.nickname = nickname;
            this.tokenHash = tokenHash;
        }
    }
}
