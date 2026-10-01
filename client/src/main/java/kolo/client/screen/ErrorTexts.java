package kolo.client.screen;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import kolo.client.i18n.Texts;
import kolo.client.net.ConnectionClosedException;
import kolo.client.net.LanSearchUnavailableException;
import kolo.client.net.LanUnavailableException;
import kolo.client.net.ServerErrorException;
import kolo.engine.error.GameException;

/** Текст для гравця з помилки запиту до сервера. */
public final class ErrorTexts {

    private ErrorTexts() {}

    /**
     * @return текст помилки сервера чи гри за кодом; розрив — «зв'язок втрачено»; зайнятий порт LAN — про порт; недоступний пошук у мережі — про адресу; інше —
     *     неочікувана помилка (баг клієнта, її треба ще й записати в лог)
     */
    public static String of(Texts texts, Throwable error) {
        return switch (cause(error)) {
            case ServerErrorException server ->
                texts.error(server.error().code(), server.error().details());
            case GameException game -> texts.error(game.code(), game.details());
            case ConnectionClosedException closed -> texts.text("app.error.connection_lost");
            case LanUnavailableException lan -> texts.text("app.error.lan_unavailable", lan.port());
            case LanSearchUnavailableException search -> texts.text("app.error.lan_search_unavailable");
            default -> texts.text("app.error.unexpected");
        };
    }

    /** Чи це очікувана помилка запиту, а не баг клієнта. */
    public static boolean expected(Throwable error) {
        Throwable cause = cause(error);
        return cause instanceof ServerErrorException
                || cause instanceof GameException
                || cause instanceof ConnectionClosedException
                || cause instanceof LanUnavailableException
                || cause instanceof LanSearchUnavailableException;
    }

    /** Справжня причина без обгорток {@code CompletableFuture}. */
    static Throwable cause(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
