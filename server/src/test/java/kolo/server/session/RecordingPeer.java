package kolo.server.session;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import kolo.engine.view.MapView;
import kolo.protocol.message.MapAssembler;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.YearPhase;

/** Співрозмовник для тестів сесій без мережі: складає повідомлення в чергу, пам'ятає, чи його закрили. */
final class RecordingPeer implements Peer {

    private static final long TIMEOUT_SECONDS = 30;

    private final BlockingQueue<ServerMessage> received = new LinkedBlockingQueue<>();
    private volatile boolean closed;

    @Override
    public void send(List<ServerMessage> messages, boolean close) {
        received.addAll(messages);
        if (close) {
            closed = true;
        }
    }

    boolean closed() {
        return closed;
    }

    /** Наступне повідомлення; сесія відповідає зі свого потоку, тож чекаємо. */
    ServerMessage next() throws InterruptedException {
        ServerMessage message = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (message == null) {
            throw new AssertionError("сесія не відповіла за " + TIMEOUT_SECONDS + " с");
        }
        return message;
    }

    /** Наступне повідомлення — цього типу. */
    <T extends ServerMessage> T next(Class<T> type) throws InterruptedException {
        ServerMessage message = next();
        if (!type.isInstance(message)) {
            throw new AssertionError("очікувалося " + type.getSimpleName() + ", а прийшло " + message);
        }
        return type.cast(message);
    }

    ServerMessage.Error error() throws InterruptedException {
        return next(ServerMessage.Error.class);
    }

    ServerMessage.Joined joined() throws InterruptedException {
        return next(ServerMessage.Joined.class);
    }

    ServerMessage.Lobby lobby() throws InterruptedException {
        return next(ServerMessage.Lobby.class);
    }

    ServerMessage.Players players() throws InterruptedException {
        return next(ServerMessage.Players.class);
    }

    /** Збирає карту з наступних повідомлень. */
    MapView map() throws InterruptedException {
        MapAssembler assembler = new MapAssembler();
        assembler.start((ServerMessage.MapStart) next());
        Optional<MapView> map = Optional.empty();
        while (map.isEmpty()) {
            map = assembler.add((ServerMessage.MapCells) next());
        }
        return map.orElseThrow();
    }

    ServerMessage.Phase phase() throws InterruptedException {
        return next(ServerMessage.Phase.class);
    }

    void expectPhase(int turn, YearPhase phase) throws InterruptedException {
        ServerMessage message = next();
        if (!message.equals(new ServerMessage.Phase(turn, phase))) {
            throw new AssertionError("очікувалася фаза " + phase + " року " + turn + ", а прийшло " + message);
        }
    }

    /** Початок року: список гравців і фази до прийому наказів. */
    void expectOrders(int turn) throws InterruptedException {
        players();
        expectPhase(turn, YearPhase.START_OF_YEAR);
        expectPhase(turn, YearPhase.ORDERS);
    }

    /** Фази розв'язання року {@code turn} і початку наступного. */
    void expectYear(int turn) throws InterruptedException {
        expectPhase(turn, YearPhase.RESOLVING);
        expectPhase(turn, YearPhase.REPORT);
        expectOrders(turn + 1);
    }

    /** Чи нічого не прийшло за коротку паузу. */
    boolean quiet() throws InterruptedException {
        return received.poll(200, TimeUnit.MILLISECONDS) == null;
    }
}
