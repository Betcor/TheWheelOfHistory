package kolo.server.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import kolo.engine.content.ContentPack;
import kolo.engine.state.WorldState;
import kolo.server.persistence.WorldDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Відкриті сесії сервера: створює їх із наскрізними номерами й закриває всі при зупинці. Потокобезпечний. */
public final class Sessions {

    private static final Logger LOG = LoggerFactory.getLogger(Sessions.class);

    private final WorldDirectory worlds;
    private final Function<ContentPack, UnaryOperator<WorldState>> years;
    private final TreeMap<Long, SessionActor> open = new TreeMap<>();
    private long nextId = 1;
    private boolean closed;

    /** @param worlds тека, де сесії створюють файли світів */
    public Sessions(WorldDirectory worlds) {
        this(worlds, SessionActor::engine);
    }

    Sessions(WorldDirectory worlds, Function<ContentPack, UnaryOperator<WorldState>> years) {
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.years = Objects.requireNonNull(years, "years");
    }

    public WorldDirectory worlds() {
        return worlds;
    }

    /**
     * Нова сесія в стані {@link SessionState#LOBBY}.
     *
     * @throws IllegalStateException якщо сервер зупиняється
     */
    public synchronized SessionActor create(ContentPack content) {
        if (closed) {
            throw new IllegalStateException("сесії закрито");
        }
        long id = nextId++;
        SessionActor session = new SessionActor(id, content, worlds, years.apply(content), this::closed);
        open.put(id, session);
        return session;
    }

    /** Відкриті сесії за номером. */
    public synchronized List<SessionActor> active() {
        return List.copyOf(open.values());
    }

    /**
     * Закриває всі сесії й чекає їхніх потоків: файли світів мають закритися цілими. Нових сесій більше не буде.
     *
     * @param timeout скільки чекати кожну сесію (генерація світу не переривається)
     */
    public void closeAll(long timeout, TimeUnit unit) {
        List<SessionActor> toClose;
        synchronized (this) {
            closed = true;
            toClose = new ArrayList<>(open.values());
        }
        for (SessionActor session : toClose) {
            session.close();
        }
        for (SessionActor session : toClose) {
            try {
                if (!session.awaitClosed(timeout, unit)) {
                    LOG.warn("Сесія {} не закрилася за {} {}", session.id(), timeout, unit);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private synchronized void closed(SessionActor session) {
        open.remove(session.id());
    }
}
