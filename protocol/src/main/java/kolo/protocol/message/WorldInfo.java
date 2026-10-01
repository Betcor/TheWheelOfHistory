package kolo.protocol.message;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import kolo.engine.error.Checks;

/**
 * Збережений світ у теці сервера ({@link ServerMessage.Worlds}): його можна завантажити ({@link
 * ClientMessage.LoadWorld}).
 *
 * @param name ім'я світу — ім'я файлу без розширення
 * @param key ключ світу ({@link WorldKeys}); порожньо — файл старішої версії гри, ключ він отримає при завантаженні
 * @param seed seed світу
 * @param turn рік, з якого гра продовжиться (хід, не календарний рік)
 * @param players нікнейми гравців світу за номером
 */
public record WorldInfo(String name, Optional<String> key, long seed, int turn, List<String> players) {

    /** Найдовше ім'я світу, символів. */
    public static final int MAX_NAME_LENGTH = 255;

    public WorldInfo {
        checkName(name);
        Objects.requireNonNull(key, "key");
        key.ifPresent(value -> WorldKeys.check("key", value));
        Checks.inRange("turn", turn, 0, Integer.MAX_VALUE);
        players = List.copyOf(players);
    }

    static void checkName(String name) {
        Checks.notBlank("name", name);
        Checks.inRange("name", name.length(), 1, MAX_NAME_LENGTH);
    }
}
