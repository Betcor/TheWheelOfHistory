package kolo.client.net;

/** Гру не вдалося відкрити для локальної мережі: порт зайнятий чи недоступний. */
public final class LanUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int port;

    public LanUnavailableException(int port, Throwable cause) {
        super("порт " + port + " недоступний", cause);
        this.port = port;
    }

    public int port() {
        return port;
    }
}
