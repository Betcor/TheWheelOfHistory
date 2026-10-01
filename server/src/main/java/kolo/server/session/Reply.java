package kolo.server.session;

import java.util.List;
import kolo.protocol.message.ServerMessage;

/**
 * Відповідь сервера на одне повідомлення клієнта.
 *
 * @param messages повідомлення клієнтові по порядку; може бути порожньо
 * @param close чи закрити з'єднання, надіславши їх
 */
public record Reply(List<ServerMessage> messages, boolean close) {

    public Reply {
        messages = List.copyOf(messages);
    }

    static Reply of(List<ServerMessage> messages) {
        return new Reply(messages, false);
    }

    static Reply closing(ServerMessage message) {
        return new Reply(List.of(message), true);
    }
}
