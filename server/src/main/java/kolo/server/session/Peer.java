package kolo.server.session;

import java.util.List;
import kolo.protocol.message.ServerMessage;

/**
 * Співрозмовник сервера — одне з'єднання клієнта. Сесія пише йому повідомлення, не знаючи про транспорт; рівність —
 * за тотожністю (одне з'єднання — один співрозмовник). Реалізація потокобезпечна й зберігає порядок повідомлень,
 * надісланих з одного потоку.
 */
public interface Peer {

    /**
     * Надсилає повідомлення по порядку.
     *
     * @param close закрити з'єднання, коли їх буде записано
     */
    void send(List<ServerMessage> messages, boolean close);

    default void send(ServerMessage message) {
        send(List.of(message), false);
    }
}
