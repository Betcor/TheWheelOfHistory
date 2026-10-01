package kolo.client.net;

/** З'єднання з сервером не встановилося або закрилося, доки запит чекав відповіді. */
public final class ConnectionClosedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConnectionClosedException(String message) {
        super(message);
    }

    public ConnectionClosedException(String message, Throwable cause) {
        super(message, cause);
    }
}
