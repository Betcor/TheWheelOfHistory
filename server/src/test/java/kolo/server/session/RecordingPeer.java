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

    ServerMessage.Error error() throws InterruptedException {
        ServerMessage message = next();
        if (!(message instanceof ServerMessage.Error error)) {
            throw new AssertionError("очікувалася помилка, а прийшло " + message);
        }
        return error;
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

    void expectPhase(int turn, YearPhase phase) throws InterruptedException {
        ServerMessage message = next();
        if (!message.equals(new ServerMessage.Phase(turn, phase))) {
            throw new AssertionError("очікувалася фаза " + phase + " року " + turn + ", а прийшло " + message);
        }
    }

    /** Фази першого року до прийому наказів. */
    void expectOrders(int turn) throws InterruptedException {
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
