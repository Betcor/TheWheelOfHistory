package kolo.client.net;

import java.util.Objects;
import kolo.protocol.message.ServerMessage;

/**
 * Сервер відповів на запит помилкою. Не {@link kolo.engine.error.GameException}: помилку кинув сервер, а тут лише
 * отримано повідомлення; гравець бачить текст за кодом і подробицями ({@code Texts.error}).
 */
public final class ServerErrorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    // transient: виняток живе лише в клієнті й не серіалізується.
    private final transient ServerMessage.Error error;

    public ServerErrorException(ServerMessage.Error error) {
        super(Objects.requireNonNull(error, "error").code().key() + " " + error.details());
        this.error = error;
    }

    public ServerMessage.Error error() {
        return error;
    }
}
