package kolo.server.session;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import kolo.engine.content.ContentPack;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.GameException;
import kolo.engine.error.PhaseClosedException;
import kolo.engine.error.ProtocolException;
import kolo.protocol.ProtocolErrors;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.Handshake;
import kolo.protocol.message.ServerMessage;

/**
 * Розмова сервера з одним клієнтом без мережі: рукостискання й передача запитів у його {@link SessionActor}. Відповіді
 * йдуть клієнтові через {@link Peer}: на привітання й помилки — одразу, решта — з потоку сесії.
 *
 * <p>Спершу — рукостискання ({@link Handshake}): будь-яка помилка до нього (інше перше повідомлення, інша версія чи
 * контент, невалідний контент сервера) — {@link ServerMessage.Error} і закриття з'єднання, бо решті повідомлень не
 * можна вірити. Після нього помилка запиту (рік уже закрито) — лише {@link ServerMessage.Error}; порушення порядку
 * розмови (повторне привітання) — знову помилка й закриття.
 *
 * <p>{@code CreateWorld} створює нову сесію з цим клієнтом-хостом; попередню сесію клієнт полишає. Закрите з'єднання
 * ({@link #disconnected()}) — теж вихід із сесії.
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
                case ClientMessage.CreateWorld create -> createWorld(create);
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

    private void createWorld(ClientMessage.CreateWorld create) {
        requireWelcomed();
        ContentPack pack = content.get();
        leave();
        session = sessions.create(pack);
        session.generate(peer, create.seed(), create.players(), create.npcShare());
    }

    private void ready(ClientMessage.Ready ready) {
        requireWelcomed();
        SessionActor current =
                session().orElseThrow(() -> new PhaseClosedException(ErrorDetails.of("turn", ready.turn())));
        current.ready(peer, ready.turn());
    }

    private void leave() {
        if (session != null) {
            session.leave(peer);
            session = null;
        }
    }

    private void requireWelcomed() {
        if (!welcomed) {
            throw ProtocolErrors.malformed(HANDSHAKE, "hello_expected");
        }
    }
}
