package kolo.client.net;

/** Пошук у локальній мережі не вдався: немає мережі чи сокет недоступний. До гри ще можна підключитися за адресою. */
public final class LanSearchUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LanSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
