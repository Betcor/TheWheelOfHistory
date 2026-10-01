package kolo.client.net;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.ServerMessage;

/** Слухач для тестів: складає події сесії в чергу в порядку надходження. */
public final class RecordingListener implements SessionListener {

    private static final long TIMEOUT_SECONDS = 60;

    /** Подія сесії. */
    public sealed interface Event {}

    public record Joined(ServerMessage.Joined joined) implements Event {}

    public record Lobby(ServerMessage.Lobby lobby) implements Event {}

    public record Started(GameStart start) implements Event {}

    public record Players(List<PlayerInfo> players) implements Event {}

    public record Phase(ServerMessage.Phase phase) implements Event {}

    public record Error(ServerMessage.Error error) implements Event {}

    public record Disconnected() implements Event {}

    private final BlockingQueue<Event> events = new LinkedBlockingQueue<>();

    @Override
    public void joined(ServerMessage.Joined joined) {
        events.add(new Joined(joined));
    }

    @Override
    public void lobby(ServerMessage.Lobby lobby) {
        events.add(new Lobby(lobby));
    }

    @Override
    public void gameStarted(GameStart start) {
        events.add(new Started(start));
    }

    @Override
    public void players(List<PlayerInfo> players) {
        events.add(new Players(players));
    }

    @Override
    public void phase(ServerMessage.Phase phase) {
        events.add(new Phase(phase));
    }

    @Override
    public void error(ServerMessage.Error error) {
        events.add(new Error(error));
    }

    @Override
    public void disconnected() {
        events.add(new Disconnected());
    }

    /** Наступна подія. */
    public Event next() throws InterruptedException {
        Event event = events.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (event == null) {
            throw new AssertionError("подія не прийшла за " + TIMEOUT_SECONDS + " с");
        }
        return event;
    }

    /** Наступна подія цього типу; інші пропускаються. */
    public <T extends Event> T next(Class<T> type) throws InterruptedException {
        while (true) {
            Event event = next();
            if (type.isInstance(event)) {
                return type.cast(event);
            }
        }
    }

    /** Пропускає події, доки не прийде прийом наказів року {@code turn}. */
    public void awaitOrders(int turn) throws InterruptedException {
        while (true) {
            Event event = next();
            if (event instanceof Phase(ServerMessage.Phase phase)
                    && phase.turn() == turn
                    && phase.phase() == kolo.protocol.message.YearPhase.ORDERS) {
                return;
            }
        }
    }

    /** Чи нічого не прийшло за коротку паузу. */
    public boolean quiet() throws InterruptedException {
        return events.poll(200, TimeUnit.MILLISECONDS) == null;
    }
}
