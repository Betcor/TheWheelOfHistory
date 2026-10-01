package kolo.client.screen;

import kolo.client.i18n.Texts;
import kolo.client.net.ConnectionClosedException;
import kolo.client.net.ServerErrorException;
import kolo.engine.error.GameException;

/** Текст для гравця з помилки запиту до сервера. */
final class ErrorTexts {

    private ErrorTexts() {}

    /**
     * @return текст помилки сервера чи гри за кодом; розрив — «зв'язок втрачено»; інше — неочікувана помилка (баг
     *     клієнта, її треба ще й кинути далі)
     */
    static String of(Texts texts, RuntimeException e) {
        return switch (e) {
            case ServerErrorException server ->
                texts.error(server.error().code(), server.error().details());
            case GameException game -> texts.error(game.code(), game.details());
            case ConnectionClosedException closed -> texts.text("app.error.connection_lost");
            default -> texts.text("app.error.unexpected");
        };
    }

    /** Чи це очікувана помилка запиту, а не баг клієнта. */
    static boolean expected(RuntimeException e) {
        return e instanceof ServerErrorException
                || e instanceof GameException
                || e instanceof ConnectionClosedException;
    }
}
