package kolo.server.session;

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
import kolo.engine.error.PhaseClosedException;
import kolo.engine.error.UnauthorizedException;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import kolo.engine.state.WorldState;
import kolo.engine.turn.TurnPipeline;
import kolo.engine.view.MapViews;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.MapChunks;
import kolo.protocol.message.Nicknames;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.YearPhase;
import kolo.server.auth.PlayerTokens;
import kolo.server.persistence.MapSnapshot;
import kolo.server.persistence.PlayerRecord;
import kolo.server.persistence.StateSnapshot;
import kolo.server.persistence.WorldDirectory;
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
 * закривається: файл закривається, потік зупиняється; продовжити світ можна, відкривши файл.
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
    private long seed;
    private NpcShare npcShare;
    private YearPhase phase;
    private WorldState world;
    private MapSnapshot map;
    private WorldStore store;
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
     * Гравець приєднується до лобі. Гру вже почато — {@code LOBBY_CLOSED}, лобі повне — {@code LOBBY_FULL}, нікнейм
     * зайнятий — {@code NICKNAME_TAKEN}; помилка — лише йому.
     */
    public void join(Peer player, String nickname) {
        Objects.requireNonNull(player, "player");
        Nicknames.check("nickname", nickname);
        post(() -> doJoin(player, nickname));
    }

    /**
     * Хост починає гру: світ генерується з гравцями лобі, створюється його файл, гравці отримують карту, список гравців
     * і фази першого року. Не хост — {@code FORBIDDEN}, гру вже почато — {@code LOBBY_CLOSED}. Невдача генерації
     * (параметри поза межами, файл не створено) — помилка всім і закриття сесії.
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
        this.seed = seed;
        this.npcShare = npcShare;
        Member member = add(host, nickname);
        member.host = true;
        LOG.info("Сесія {}: лобі відкрито, хост «{}»", id, nickname);
        lobbyChanged();
    }

    private void doJoin(Peer peer, String nickname) {
        try {
            if (state != SessionState.LOBBY) {
                throw new ConflictException(ErrorCode.LOBBY_CLOSED, ErrorDetails.of("session", id));
            }
            if (members.size() >= WorldLimits.MAX_PLAYERS) {
                throw new ConflictException(ErrorCode.LOBBY_FULL, ErrorDetails.of("max", WorldLimits.MAX_PLAYERS));
            }
            String key = Nicknames.key(nickname);
            if (members.stream().anyMatch(m -> Nicknames.key(m.nickname).equals(key))) {
                throw new ConflictException(ErrorCode.NICKNAME_TAKEN, ErrorDetails.of("nickname", nickname));
            }
        } catch (GameException e) {
            peer.send(ServerMessage.Error.of(e));
            return;
        }
        add(peer, nickname);
        lobbyChanged();
    }

    private Member add(Peer peer, String nickname) {
        String token = tokens.generate();
        Member member = new Member(nextPlayer++, nickname, PlayerTokens.hash(token));
        member.peer = peer;
        members.add(member);
        peer.send(new ServerMessage.Joined(id, member.number, token));
        return member;
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
        } catch (GameException e) {
            peer.send(ServerMessage.Error.of(e));
            return;
        }
        state = SessionState.GENERATING;
        lobby = null;
        List<PlayerRecord> records = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
            member.country = i;
            records.add(new PlayerRecord(member.number, member.nickname, member.tokenHash, i, member.host));
        }
        try {
            WorldState initial = NewWorlds.generate(content, seed, members.size(), npcShare);
            MapSnapshot snapshot = MapSnapshot.of(initial.map());
            store = worlds.create(seed, snapshot, StateSnapshot.of(initial, snapshot), records);
            map = snapshot;
            world = initial;
        } catch (GameException e) {
            broadcast(List.of(ServerMessage.Error.of(e)));
            shutdown();
            return;
        }
        LOG.info("Сесія {}: світ {} з {} гравцями створено у {}", id, seed, members.size(), store.file());
        state = SessionState.RUNNING;
        broadcast(MapChunks.split(MapViews.of(world)));
        startYear();
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
        peer.send(new ServerMessage.Joined(id, member.number, token));
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
            members.remove(member);
            if (members.isEmpty()) {
                shutdown();
                return;
            }
            if (member.host) {
                members.getFirst().host = true;
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
        Member host = members.stream().filter(m -> m.host).findFirst().orElseThrow();
        lobby = new LobbyInfo(id, host.nickname, members.size(), npcShare);
        broadcast(List.of(new ServerMessage.Lobby(id, seed, npcShare, players())));
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
        executor.shutdown();
        onClosed.accept(this);
        LOG.debug("Сесію {} закрито", id);
    }

    /** Гравець сесії; змінюється лише в потоці сесії. */
    private static final class Member {
        final int number;
        final String nickname;
        final String tokenHash;
        /** З'єднання гравця; {@code null} — гравець не на зв'язку. */
        Peer peer;

        boolean host;
        boolean ready;
        /** Номер держави; до початку гри — −1. */
        int country = -1;

        Member(int number, String nickname, String tokenHash) {
            this.number = number;
            this.nickname = nickname;
            this.tokenHash = tokenHash;
        }
    }
}
