package kolo.server.session;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import kolo.engine.content.ContentPack;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ForbiddenException;
import kolo.engine.error.GameException;
import kolo.engine.error.NotFoundException;
import kolo.engine.error.PhaseClosedException;
import kolo.engine.error.ProtocolException;
import kolo.protocol.ProtocolErrors;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.Handshake;
import kolo.protocol.message.ServerMessage;

/**
 * Розмова сервера з одним клієнтом без мережі: рукостискання, список лобі й передача запитів у {@link SessionActor}
 * клієнта. Відповіді йдуть клієнтові через {@link Peer}: на привітання, список і помилки запиту — одразу, решта — з
 * потоку сесії.
 *
 * <p>Спершу — рукостискання ({@link Handshake}): будь-яка помилка до нього (інше перше повідомлення, інша версія чи
 * контент, невалідний контент сервера) — {@link ServerMessage.Error} і закриття з'єднання, бо решті повідомлень не
 * можна вірити. Після нього помилка запиту (рік уже закрито, лобі повне) — лише {@link ServerMessage.Error}; порушення
 * порядку розмови (повторне привітання) — знову помилка й закриття.
 *
 * <p>Клієнт буває щонайбільше в одній сесії: створення лобі, приєднання й повернення спершу полишають попередню, як і
 * {@code Leave} та закрите з'єднання ({@link #disconnected()}). Кожна сесія пише клієнтові через власного
 * співрозмовника-посередника, що замовкає при виході: повідомлення сесії, яку клієнт уже полишив, до нього не дійдуть
 * і не змішаються з повідомленнями нової.
 *
 * <p>Не потокобезпечний: повідомлення одного з'єднання обробляються по черзі в одному потоці.
 */
public final class ClientSession {

    /** Місце помилок порядку розмови. */
    static final String HANDSHAKE = "handshake";

    private final Supplier<ContentPack> content;
    private final Sessions sessions;
    private final Peer peer;
    private boolean welcomed;
    private SessionActor session;
    private ScopedPeer scoped;

    public ClientSession(Supplier<ContentPack> content, Sessions sessions, Peer peer) {
        this.content = Objects.requireNonNull(content, "content");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.peer = Objects.requireNonNull(peer, "peer");
    }

    /** Чи пройдено рукостискання. */
    public boolean welcomed() {
        return welcomed;
    }

    /** Сесія клієнта, якщо вона ще відкрита. */
    public Optional<SessionActor> session() {
        return session == null || session.state() == SessionState.CLOSED ? Optional.empty() : Optional.of(session);
    }

    /**
     * Обробляє повідомлення клієнта. Помилки гри стають {@link ServerMessage.Error}; інші винятки — баг сервера, вони
     * летять далі.
     */
    public void handle(ClientMessage message) {
        try {
            switch (message) {
                case ClientMessage.Hello hello -> hello(hello);
                case ClientMessage.ListLobbies list -> listLobbies();
                case ClientMessage.CreateLobby create -> createLobby(create);
                case ClientMessage.JoinLobby join -> joinLobby(join);
                case ClientMessage.StartGame start -> startGame();
                case ClientMessage.Rejoin rejoin -> rejoin(rejoin);
                case ClientMessage.Leave leave -> {
                    requireWelcomed();
                    leave();
                }
                case ClientMessage.Ready ready -> ready(ready);
            }
        } catch (GameException e) {
            ServerMessage.Error error = ServerMessage.Error.of(e);
            // Порушена розмова (не той порядок повідомлень) далі не має сенсу, як і будь-яка помилка до привітання.
            peer.send(List.of(error), !welcomed || e instanceof ProtocolException);
        }
    }

    /** З'єднання закрито: клієнт полишає сесію. */
    public void disconnected() {
        leave();
    }

    private void hello(ClientMessage.Hello hello) {
        if (welcomed) {
            throw ProtocolErrors.malformed(HANDSHAKE, "hello_repeated");
        }
        ServerMessage.Welcome welcome = Handshake.accept(hello, content.get().hash());
        welcomed = true;
        peer.send(welcome);
    }

    private void listLobbies() {
        requireWelcomed();
        peer.send(new ServerMessage.Lobbies(sessions.lobbies()));
    }

    private void createLobby(ClientMessage.CreateLobby create) {
        requireWelcomed();
        ContentPack pack = content.get();
        leave();
        enter(sessions.create(pack)).open(scoped, create.nickname(), create.seed(), create.npcShare());
    }

    private void joinLobby(ClientMessage.JoinLobby join) {
        requireWelcomed();
        SessionActor target = find(join.session());
        leave();
        enter(target).join(scoped, join.nickname());
    }

    private void rejoin(ClientMessage.Rejoin rejoin) {
        requireWelcomed();
        SessionActor target = find(rejoin.session());
        leave();
        enter(target).rejoin(scoped, rejoin.player(), rejoin.token());
    }

    private void startGame() {
        requireWelcomed();
        SessionActor current =
                session().orElseThrow(() -> new ForbiddenException(ErrorDetails.of("action", "start_game")));
        current.start(scoped);
    }

    private void ready(ClientMessage.Ready ready) {
        requireWelcomed();
        SessionActor current =
                session().orElseThrow(() -> new PhaseClosedException(ErrorDetails.of("turn", ready.turn())));
        current.ready(scoped, ready.turn());
    }

    private SessionActor find(long id) {
        return sessions.find(id)
                .orElseThrow(
                        () -> new NotFoundException(ErrorCode.NOT_FOUND, ErrorDetails.of("what", "session", "id", id)));
    }

    private SessionActor enter(SessionActor target) {
        session = target;
        scoped = new ScopedPeer(peer);
        return target;
    }

    private void leave() {
        if (session != null) {
            // Спершу замовкаємо: що сесія надішле після цього, клієнтові вже не потрібне.
            scoped.silence();
            session.leave(scoped);
            session = null;
            scoped = null;
        }
    }

    private void requireWelcomed() {
        if (!welcomed) {
            throw ProtocolErrors.malformed(HANDSHAKE, "hello_expected");
        }
    }

    /**
     * Співрозмовник для однієї сесії: передає її повідомлення з'єднанню, доки клієнт у ній. Перевірка й запис — під
     * одним замком, тож після {@link #silence()} жодне повідомлення сесії вже не стане в чергу з'єднання поперед
     * повідомлень нової сесії.
     */
    private static final class ScopedPeer implements Peer {
        private final Peer target;
        private boolean active = true;

        ScopedPeer(Peer target) {
            this.target = target;
        }

        @Override
        public synchronized void send(List<ServerMessage> messages, boolean close) {
            if (active) {
                target.send(messages, close);
            }
        }

        synchronized void silence() {
            active = false;
        }
    }
}
