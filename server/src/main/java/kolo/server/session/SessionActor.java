package kolo.server.session;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import kolo.engine.content.ContentPack;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.GameException;
import kolo.engine.error.PhaseClosedException;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldState;
import kolo.engine.turn.TurnPipeline;
import kolo.engine.view.MapViews;
import kolo.protocol.message.MapChunks;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.YearPhase;
import kolo.server.persistence.MapSnapshot;
import kolo.server.persistence.StateSnapshot;
import kolo.server.persistence.WorldDirectory;
import kolo.server.persistence.WorldStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Одна ігрова сесія: світ, його файл і гравці. Усі зміни сесії виконуються в її власному потоці ({@code
 * kolo-session-<номер>}) по черзі, тож стан без блокувань; публічні методи лише ставлять завдання в чергу й
 * повертаються одразу. Результати гравці отримують повідомленнями через {@link Peer}.
 *
 * <p>Стани — {@link SessionState}: хост створює сесію, вона генерує світ і створює файл світу ({@code GENERATING}),
 * надсилає карту й живе роками ({@code RUNNING}). Рік — фази {@link YearPhase}: {@code START_OF_YEAR → ORDERS}; коли
 * всі гравці сесії натиснули «Готово» — {@code RESOLVING} (рушій розв'язує рік, рік пишеться у файл однією
 * транзакцією) → {@code REPORT} → наступний рік. Якщо рік не вдалося розв'язати чи зберегти — {@code PAUSED}: стан і
 * файл лишаються на попередньому році, гравці отримують помилку. Коли сесію полишає останній гравець, вона
 * закривається: файл закривається, потік зупиняється; продовжити світ можна, відкривши файл.
 */
public final class SessionActor {

    private static final Logger LOG = LoggerFactory.getLogger(SessionActor.class);

    private final long id;
    private final ContentPack content;
    private final WorldDirectory worlds;
    private final UnaryOperator<WorldState> years;
    private final Consumer<SessionActor> onClosed;
    private final ExecutorService executor;
    private volatile SessionState state = SessionState.LOBBY;

    // Далі — лише в потоці сесії.
    private final Set<Peer> players = new LinkedHashSet<>();
    private final Set<Peer> ready = new LinkedHashSet<>();
    private YearPhase phase;
    private WorldState world;
    private MapSnapshot map;
    private WorldStore store;

    /**
     * @param years розв'язання року: стан на початку року → новий стан наприкінці; у грі — {@link TurnPipeline}
     * @param onClosed викликається в потоці сесії, коли її закрито
     */
    SessionActor(
            long id,
            ContentPack content,
            WorldDirectory worlds,
            UnaryOperator<WorldState> years,
            Consumer<SessionActor> onClosed) {
        this.id = id;
        this.content = Objects.requireNonNull(content, "content");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
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

    /**
     * Генерує світ, створює його файл і надсилає хостові карту й фази першого року. Невдача (параметри поза межами,
     * файл не створено) — помилка хостові й закриття сесії.
     */
    public void generate(Peer host, long seed, int playerCount, NpcShare npcShare) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(npcShare, "npcShare");
        post(() -> doGenerate(host, seed, playerCount, npcShare));
    }

    /**
     * Гравець натиснув «Готово» для року {@code turn}. Не той рік чи не фаза наказів — помилка {@code PHASE_CLOSED}
     * лише йому.
     */
    public void ready(Peer player, int turn) {
        Objects.requireNonNull(player, "player");
        post(() -> doReady(player, turn));
    }

    /** Гравець полишив сесію (закрив з'єднання чи створив інший світ). */
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
                    for (Peer player : players) {
                        player.send(List.of(), true);
                    }
                    shutdown();
                }
            });
        } catch (RejectedExecutionException e) {
            // Сесію вже закрито: завдання нікому не потрібне.
        }
    }

    private void doGenerate(Peer host, long seed, int playerCount, NpcShare npcShare) {
        if (state != SessionState.LOBBY) {
            throw new IllegalStateException("світ сесії " + id + " уже створено");
        }
        players.add(host);
        state = SessionState.GENERATING;
        try {
            WorldState initial = NewWorlds.generate(content, seed, playerCount, npcShare);
            MapSnapshot snapshot = MapSnapshot.of(initial.map());
            store = worlds.create(seed, snapshot, StateSnapshot.of(initial, snapshot));
            map = snapshot;
            world = initial;
        } catch (GameException e) {
            broadcast(List.of(ServerMessage.Error.of(e)));
            shutdown();
            return;
        }
        LOG.info("Сесія {}: світ {} створено у {}", id, seed, store.file());
        state = SessionState.RUNNING;
        broadcast(MapChunks.split(MapViews.of(world)));
        startYear();
    }

    private void doReady(Peer player, int turn) {
        if (!players.contains(player)) {
            return;
        }
        if (state != SessionState.RUNNING || phase != YearPhase.ORDERS || turn != world.turn()) {
            player.send(ServerMessage.Error.of(new PhaseClosedException(ErrorDetails.of("turn", turn))));
            return;
        }
        ready.add(player);
        resolveIfAllReady();
    }

    private void doLeave(Peer player) {
        if (!players.remove(player)) {
            return;
        }
        ready.remove(player);
        if (players.isEmpty()) {
            shutdown();
            return;
        }
        // Гравець, якого чекали, пішов — решта, можливо, вже готова.
        resolveIfAllReady();
    }

    private void resolveIfAllReady() {
        if (state == SessionState.RUNNING
                && phase == YearPhase.ORDERS
                && !ready.isEmpty()
                && ready.containsAll(players)) {
            resolveYear();
        }
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
            ready.clear();
            broadcast(List.of(ServerMessage.Error.of(e)));
            return;
        }
        world = next;
        ready.clear();
        phase(turn, YearPhase.REPORT);
        startYear();
    }

    private void startYear() {
        phase(world.turn(), YearPhase.START_OF_YEAR);
        phase(world.turn(), YearPhase.ORDERS);
    }

    private void phase(int turn, YearPhase next) {
        phase = next;
        broadcast(List.of(new ServerMessage.Phase(turn, next)));
    }

    private void broadcast(List<ServerMessage> messages) {
        for (Peer player : players) {
            player.send(messages, false);
        }
    }

    private void shutdown() {
        if (state == SessionState.CLOSED) {
            return;
        }
        state = SessionState.CLOSED;
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
}
